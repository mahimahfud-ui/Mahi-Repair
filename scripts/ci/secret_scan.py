#!/usr/bin/env python3
"""High-signal source and APK scanner for credentials that must never ship."""

from __future__ import annotations

import argparse
import base64
import json
import re
import subprocess
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
# Assemble the marker fragments so this scanner does not match its own detection rule.\nPRIVATE_KEY = re.compile(rb"-----" + rb"BEGIN " + rb"(?:RSA |EC |OPENSSH )?PRIVATE KEY" + rb"-----")
SUPABASE_SECRET = re.compile(rb"\bsb_secret_[A-Za-z0-9_-]{20,}\b")
GITHUB_TOKEN = re.compile(rb"\b(?:gh[pousr]_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{30,})\b")
AWS_ACCESS_KEY = re.compile(rb"\bAKIA[0-9A-Z]{16}\b")
SLACK_TOKEN = re.compile(rb"\bxox[baprs]-[A-Za-z0-9-]{20,}\b")
STRIPE_SECRET = re.compile(rb"\bsk_live_[A-Za-z0-9]{20,}\b")
JWT = re.compile(rb"\b(eyJ[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,})\b")
SECRET_ASSIGNMENT = re.compile(
    rb"(?i)\b(?:SUPABASE_SERVICE_ROLE_KEY|SERVICE_ROLE_KEY|RECIPE_SIGNING_PRIVATE_KEY_PEM)\b"
    rb"\s*(?:=|:)\s*[\"']?([A-Za-z0-9_./+=-]{24,})"
)

SKIP_PARTS = {
    ".git", "node_modules", ".next", ".gradle", "build", "coverage",
    ".turbo", ".vercel", "dist", "vendor",
}
SKIP_SUFFIXES = {
    ".lock", ".png", ".jpg", ".jpeg", ".webp", ".gif", ".ico",
    ".pdf", ".zip", ".jar", ".class", ".apk", ".aab", ".keystore",
}


def is_service_role_jwt(token: bytes) -> bool:
    try:
        payload = token.split(b".", 2)[1]
        decoded = base64.urlsafe_b64decode(payload + b"=" * (-len(payload) % 4))
        claims = json.loads(decoded.decode("utf-8"))
        return claims.get("role") == "service_role"
    except (ValueError, UnicodeDecodeError, json.JSONDecodeError, IndexError):
        return False


def find_credential(data: bytes) -> str | None:
    if PRIVATE_KEY.search(data):
        return "private-key PEM block"
    if SUPABASE_SECRET.search(data):
        return "Supabase secret API key"
    if GITHUB_TOKEN.search(data):
        return "GitHub access token"
    if AWS_ACCESS_KEY.search(data):
        return "AWS access-key identifier"
    if SLACK_TOKEN.search(data):
        return "Slack access token"
    if STRIPE_SECRET.search(data):
        return "Stripe live secret key"
    for match in JWT.finditer(data):
        if is_service_role_jwt(match.group(1)):
            return "Supabase service-role JWT"
    for match in SECRET_ASSIGNMENT.finditer(data):
        value = match.group(1)
        if value.startswith((bytes((36, 123)), b"$", b"process.", b"secrets.", b"env(")):
            continue
        return "privileged secret assigned as a literal"
    return None


def tracked_source_files() -> list[Path]:
    result = subprocess.run(
        ["git", "ls-files", "-z", "--cached"],
        cwd=ROOT,
        check=True,
        stdout=subprocess.PIPE,
    )
    paths: list[Path] = []
    for raw in result.stdout.split(b"\0"):
        if not raw:
            continue
        rel = Path(raw.decode("utf-8", errors="surrogateescape"))
        if rel.name == ".env.example":
            continue  # Public template values are placeholders by design.
        if any(part in SKIP_PARTS for part in rel.parts) or rel.suffix.lower() in SKIP_SUFFIXES:
            continue
        path = ROOT / rel
        if path.is_file():
            paths.append(path)
    return paths


def scan_source() -> list[tuple[str, str]]:
    findings: list[tuple[str, str]] = []
    for path in tracked_source_files():
        try:
            data = path.read_bytes()
        except OSError as error:
            findings.append((path.relative_to(ROOT).as_posix(), f"could not read file: {error}"))
            continue
        if b"\0" in data[:8192]:
            continue
        finding = find_credential(data)
        if finding:
            findings.append((path.relative_to(ROOT).as_posix(), finding))
    return findings


def scan_apk(apk_path: Path) -> list[tuple[str, str]]:
    findings: list[tuple[str, str]] = []
    try:
        with zipfile.ZipFile(apk_path) as archive:
            for item in archive.infolist():
                if item.is_dir():
                    continue
                try:
                    data = archive.read(item)
                except (OSError, RuntimeError, zipfile.BadZipFile):
                    findings.append((f"{apk_path.name}:{item.filename}", "could not inspect APK entry"))
                    continue
                finding = find_credential(data)
                if finding:
                    findings.append((f"{apk_path.name}:{item.filename}", finding))
    except (OSError, zipfile.BadZipFile) as error:
        findings.append((str(apk_path), f"APK is unreadable or invalid: {error}"))
    return findings


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", action="store_true", help="scan all tracked text source files")
    parser.add_argument("--apk", type=Path, help="inspect every file entry in a generated APK")
    args = parser.parse_args()
    if not args.source and args.apk is None:
        parser.error("select --source and/or --apk")

    findings: list[tuple[str, str]] = []
    if args.source:
        findings.extend(scan_source())
    if args.apk is not None:
        findings.extend(scan_apk(args.apk if args.apk.is_absolute() else ROOT / args.apk))

    if findings:
        for path, reason in findings:
            print(f"::error file={path}::Potential {reason} detected in {path}. Values are intentionally not printed.")
        return 1

    print("PASS: no configured privileged credential patterns were found.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
