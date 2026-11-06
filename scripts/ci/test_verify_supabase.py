#!/usr/bin/env python3
"""Regression tests for CI deployment-command guardrails."""

import unittest

from verify_supabase import has_forbidden_jwt_override


class DeploymentJwtGuardTests(unittest.TestCase):
    def test_rejects_function_deploy_command_with_no_verify_jwt(self) -> None:
        command = (
            "supabase functions deploy submit-repair-run "
            "--project-ref test-project --no-verify-jwt"
        )
        self.assertTrue(has_forbidden_jwt_override(command))

    def test_allows_function_deploy_command_without_forbidden_flag(self) -> None:
        command = "supabase functions deploy submit-repair-run --project-ref test-project"
        self.assertFalse(has_forbidden_jwt_override(command))

    def test_ignores_comments_and_echo_statements(self) -> None:
        text = (
            "# supabase functions deploy submit-repair-run --no-verify-jwt\n"
            "echo supabase functions deploy submit-repair-run --no-verify-jwt\n"
        )
        self.assertFalse(has_forbidden_jwt_override(text))


if __name__ == "__main__":
    unittest.main()
