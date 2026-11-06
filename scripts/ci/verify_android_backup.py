#!/usr/bin/env python3
"""Validate that Android cloud backup and device-to-device restore are disabled."""

from __future__ import annotations

import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ANDROID = "{http://schemas.android.com/apk/res/android}"
REQUIRED_DOMAINS = {
    "root", "file", "database", "sharedpref", "external",
    "device_root", "device_file", "device_database", "device_sharedpref",
}


def fail(message: str) -> None:
    raise SystemExit(f"FAIL: {message}")


def exclusions(section: ET.Element) -> set[tuple[str | None, str | None]]:
    return {
        (item.get("domain"), item.get("path"))
        for item in section.findall("exclude")
    }


def main() -> int:
    try:
        manifest = ET.parse(ROOT / "app/src/main/AndroidManifest.xml").getroot()
        application = manifest.find("application")
        if application is None:
            fail("Android application element is missing.")
        if application.get(ANDROID + "allowBackup") != "false":
            fail("android:allowBackup must remain false.")
        if application.get(ANDROID + "fullBackupContent") != "@xml/backup_rules":
            fail("Manifest must reference @xml/backup_rules.")
        if application.get(ANDROID + "dataExtractionRules") != "@xml/data_extraction_rules":
            fail("Manifest must reference @xml/data_extraction_rules.")

        legacy = ET.parse(ROOT / "app/src/main/res/xml/backup_rules.xml").getroot()
        if legacy.tag != "full-backup-content":
            fail("Legacy backup rules root must be full-backup-content.")
        missing = {(domain, ".") for domain in REQUIRED_DOMAINS} - exclusions(legacy)
        if missing:
            fail(f"Legacy backup exclusions are incomplete: {sorted(missing)}")

        modern = ET.parse(ROOT / "app/src/main/res/xml/data_extraction_rules.xml").getroot()
        if modern.tag != "data-extraction-rules":
            fail("Modern backup rules root must be data-extraction-rules.")
        for section_name in ("cloud-backup", "device-transfer"):
            section = modern.find(section_name)
            if section is None:
                fail(f"Modern {section_name} section is missing.")
            missing = {(domain, ".") for domain in REQUIRED_DOMAINS} - exclusions(section)
            if missing:
                fail(f"{section_name} exclusions are incomplete: {sorted(missing)}")
    except (ET.ParseError, OSError) as error:
        fail(f"Backup configuration could not be parsed: {error}")

    print("PASS: manifest references both backup rule files, allowBackup=false, and all private domains are excluded from cloud and device transfer.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
