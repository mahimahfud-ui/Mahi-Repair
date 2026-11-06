#!/usr/bin/env python3
"""Validate the repository's expected Supabase inventory and deployment guardrails."""

from __future__ import annotations

import re
import sys
import tomllib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
FUNCTIONS = (
    "admin-assign-tool",
    "admin-delete-draft-tool",
    "admin-disable-tool",
    "admin-invite-member",
    "admin-manage-device",
    "admin-manage-member",
    "admin-manage-tool-state",
    "admin-publish-tool",
    "admin-save-validation",
    "admin-upsert-tool",
    "get-admin-analytics",
    "get-my-repair-tools",
    "get-signed-repair-tool",
    "register-device",
    "submit-repair-run",
    "send-auth-email",
)
MIGRATIONS = (
    "20261105171141_202611050001_initial_schema.sql",
    "20261105171330_20261105180000_harden_rls_grants_and_indexes.sql",
    "20261105171827_20261105183000_deny_browser_rate_limit_access.sql",
    "20261106120000_private_realtime_catalog_broadcast.sql",
    "20261106153000_backend_report_idempotency_and_atomic_catalog.sql",
    "20261106160000_transactional_admin_mutations_audit.sql",
    "20261106170000_atomic_member_catalog_and_device_registration.sql",
)

EXPECTED_DENO_TESTS = (
    "_shared/admin_audit_contract_test.ts",
    "_shared/database_integrity_contract_test.ts",
    "_shared/http_contract_test.ts",
    "_shared/member_catalog_contract_test.ts",
    "_shared/realtime_contract_test.ts",
    "_shared/recipe_test.ts",
    "_shared/run_report_test.ts",
    "send-auth-email/index_test.ts",
)

FORBIDDEN_DEPLOY_FLAG = re.compile(
    r"(?m)^[ \t]*supabase[ \t]+functions[ \t]+deploy\b[^\r\n]*--no-verify-jwt(?:[ \t]|$)"
)


def has_forbidden_jwt_override(workflow_text: str) -> bool:
    """Return true only for an actual function-deploy command disabling JWT verification."""
    return FORBIDDEN_DEPLOY_FLAG.search(workflow_text) is not None


def fail(message: str) -> None:
    raise SystemExit(f"FAIL: {message}")


def main() -> int:
    supabase = ROOT / "admin-panel/supabase"
    functions_root = supabase / "functions"
    actual_functions = sorted(
        path.name for path in functions_root.iterdir()
        if path.is_dir() and not path.name.startswith("_")
    )
    if actual_functions != sorted(FUNCTIONS):
        fail(
            "Edge Function inventory mismatch. "
            f"Expected {sorted(FUNCTIONS)}, found {actual_functions}."
        )
    missing_entrypoints = [
        name for name in FUNCTIONS
        if not (functions_root / name / "index.ts").is_file()
    ]
    if missing_entrypoints:
        fail(f"Missing Edge Function index.ts entrypoints: {missing_entrypoints}")
    print(f"PASS: all {len(FUNCTIONS)} expected Edge Function directories have index.ts entrypoints.")

    migration_root = supabase / "migrations"
    actual_migrations = tuple(sorted(path.name for path in migration_root.glob("*.sql")))
    if actual_migrations != MIGRATIONS:
        fail(
            "Migration inventory changed. Review the new/removed SQL and update this "
            f"intentional inventory gate. Expected {MIGRATIONS}, found {actual_migrations}."
        )
    if any(not re.match(r"^\d{14}_.+\.sql$", name) for name in actual_migrations):
        fail("Every migration filename must begin with a unique 14-digit timestamp.")
    if len({name[:14] for name in actual_migrations}) != len(actual_migrations):
        fail("Migration timestamps must be unique.")
    for name in actual_migrations:
        content = (migration_root / name).read_text(encoding="utf-8").strip()
        if not content:
            fail(f"Migration is empty: {name}")
    print(f"PASS: expected migration inventory contains {len(actual_migrations)} non-empty, uniquely timestamped SQL files.")

    config_path = supabase / "config.toml"
    config = tomllib.loads(config_path.read_text(encoding="utf-8"))
    function_config = config.get("functions", {})
    hook_config = function_config.get("send-auth-email", {})
    if hook_config.get("verify_jwt") is not False:
        fail("The signed Supabase Auth email hook must explicitly disable platform JWT verification and verify its Standard Webhooks signature itself.")
    protected_functions = tuple(name for name in FUNCTIONS if name != "send-auth-email")
    disabled = [
        name for name in protected_functions
        if function_config.get(name, {}).get("verify_jwt", True) is not True
    ]
    if disabled:
        fail(f"JWT verification is disabled for protected application Edge Functions: {disabled}")
    hook_source = (functions_root / "send-auth-email/index.ts").read_text(encoding="utf-8")
    if "new Webhook(secret)" not in hook_source or "await webhook.verify(" not in hook_source:
        fail("send-auth-email must authenticate requests using the Standard Webhooks signature.")
    if "webhook-id" not in hook_source:
        fail("send-auth-email must require the signed webhook delivery identifier.")
    print("PASS: all 15 application functions require JWT verification; the sole webhook exception verifies its Standard Webhooks signature.")

    deploy_path = ROOT / ".github/workflows/supabase-functions-deploy.yml"
    deploy = deploy_path.read_text(encoding="utf-8")
    required_fragments = (
        "workflow_dispatch:",
        "confirm_production:",
        "if: github.ref == 'refs/heads/main' && inputs.confirm_production == 'DEPLOY'",
        "name: supabase-production",
        "secrets.SUPABASE_ACCESS_TOKEN",
        "secrets.SUPABASE_DB_PASSWORD",
        "vars.ADMIN_ORIGIN",
        "RECIPE_SIGNING_PRIVATE_KEY_PEM",
        "RESEND_API_KEY",
        "RESEND_FROM_EMAIL",
        "BREVO_API_KEY",
        "BREVO_FROM_EMAIL",
        "send-auth-email",
        'supabase secrets set "ADMIN_ORIGIN=$ADMIN_ORIGIN"',
        "supabase db push --linked --dry-run",
        "supabase db push --linked --yes",
        "supabase functions deploy",
    )
    missing = [fragment for fragment in required_fragments if fragment not in deploy]
    if missing:
        fail(f"Production deployment workflow is missing required gates: {missing}")
    preview_at = deploy.index("supabase db push --linked --dry-run")
    apply_at = deploy.index("supabase db push --linked --yes")
    if preview_at >= apply_at:
        fail("Migration preview must appear before migration application.")
    if has_forbidden_jwt_override(deploy):
        fail("A Supabase production deploy command must never disable JWT verification.")
    forbidden_fixture = "supabase functions deploy test-function --project-ref test --no-verify-jwt"
    if not has_forbidden_jwt_override(forbidden_fixture):
        fail("JWT deployment guard self-test failed to detect --no-verify-jwt.")
    safe_fixture = "# supabase functions deploy test-function --no-verify-jwt\necho deploy --no-verify-jwt"
    if has_forbidden_jwt_override(safe_fixture):
        fail("JWT deployment guard incorrectly matched a comment or echo statement.")
    print("PASS: JWT deployment guard detects forbidden deploy flags and ignores comments/non-deploy text.")
    deploy_block = deploy.split("functions=(", 1)[-1].split(")", 1)[0]
    deployed_functions = tuple(re.findall(r"^\s+([a-z][a-z0-9-]+)\s*$", deploy_block, flags=re.M))
    if deployed_functions != FUNCTIONS:
        fail(f"Production deploy target list differs from the protected inventory: {deployed_functions}")
    print("PASS: production workflow remains manual, main-only, exact-confirmation gated, protected-environment gated, credential checked, signing-secret and email-provider checked, origin checked, and migration-preview gated.")
    print("PASS: deployment targets all 16 functions; 15 require JWT and send-auth-email is the only signed-webhook exception.")

    test_files = sorted(functions_root.rglob("*_test.ts"))
    found_test_paths = {path.relative_to(functions_root).as_posix() for path in test_files}
    missing_tests = sorted(set(EXPECTED_DENO_TESTS) - found_test_paths)
    if missing_tests:
        fail(f"Expected Deno contract test files are missing: {missing_tests}")
    print(
        f"PASS: discovered {len(test_files)} Deno *_test.ts file(s) recursively; "
        f"all {len(EXPECTED_DENO_TESTS)} reviewed recipe/report/static/Auth-hook contract files are present."
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
