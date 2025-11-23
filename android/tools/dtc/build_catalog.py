#!/usr/bin/env python3
"""Utility to build DTC catalogs from raw sources in android/base."""
from __future__ import annotations

import argparse
import json
import re
from collections import defaultdict
from dataclasses import dataclass
from pathlib import Path
from typing import Dict, List, Tuple

CODE_RE = re.compile(r"\b([PCBUpcbu][0-9A-Fa-f]{4})\b")
CATEGORY_RE = re.compile(r"\[Category\(Categories\.(?P<category>\w+)\),\s*Description\(\"(?P<description>[^\"]+)\"\)\]")
CODE_LINE_RE = re.compile(r"([PCBU][0-9A-F]{4})\s*=")
SYSTEM_BY_PREFIX = {
    "P": "powertrain",
    "C": "chassis",
    "B": "body",
    "U": "network",
}

SUPPORTED_MANUFACTURER_DIRS = (
    "All ",
    "Generic ",
)

@dataclass
class GenericEntry:
    code: str
    system: str
    label: str
    notes: str | None = None


def normalize_code(code: str) -> str:
    cleaned = code.strip().upper()
    if cleaned.startswith("0X"):
        cleaned = cleaned[2:]
    return cleaned


def normalize_spaces(text: str) -> str:
    return re.sub(r"\s+", " ", text.strip())


def parse_csharp_catalog(path: Path) -> Dict[str, GenericEntry]:
    entries: Dict[str, GenericEntry] = {}
    lines = path.read_text(encoding="utf-8").splitlines()
    line_count = len(lines)
    idx = 0
    while idx < line_count:
        match = CATEGORY_RE.search(lines[idx])
        if not match:
            idx += 1
            continue
        category = match.group("category")
        description = match.group("description")
        j = idx + 1
        code_match = None
        while j < line_count:
            code_match = CODE_LINE_RE.search(lines[j])
            if code_match:
                break
            j += 1
        if not code_match:
            idx = j
            continue
        code = normalize_code(code_match.group(1))
        system = SYSTEM_BY_PREFIX.get(code[0])
        if not system:
            idx = j + 1
            continue
        entry = GenericEntry(code=code, system=system, label=description, notes=None)
        entries[code] = entry
        idx = j + 1
    return entries


def load_dtcmapping(path: Path) -> Dict[str, str]:
    with path.open("r", encoding="utf-8") as handle:
        data = json.load(handle)
    return {normalize_code(code): normalize_spaces(label) for code, label in data.items()}


def merge_generic_entries(primary: Dict[str, GenericEntry], fallback: Dict[str, str]) -> List[GenericEntry]:
    for code, description in fallback.items():
        if code in primary:
            entry = primary[code]
            if entry.label != description:
                notes = entry.notes or ""
                alt = f"Alt: {description}"
                entry.notes = alt if not notes else f"{notes}; {alt}"
            continue
        system = SYSTEM_BY_PREFIX.get(code[0])
        if not system:
            continue
        primary[code] = GenericEntry(code=code, system=system, label=description)
    return sorted(primary.values(), key=lambda item: item.code)


def detect_manufacturer_name(path: Path) -> str:
    title = path.name
    if title.startswith("All "):
        title = title[len("All ") :]
    if title.startswith("Generic "):
        title = title[len("Generic ") :]
    title = title.split(" OBD2")[0]
    return title.strip() or "Misc"


def parse_manufacturer_file(path: Path) -> List[Tuple[str, str]]:
    text = path.read_text(encoding="utf-8", errors="ignore")
    matches = list(CODE_RE.finditer(text))
    entries: List[Tuple[str, str]] = []
    for index, match in enumerate(matches):
        code = normalize_code(match.group(1))
        start = match.end(0)
        end = matches[index + 1].start(0) if index + 1 < len(matches) else len(text)
        description = normalize_spaces(text[start:end])
        if not description:
            continue
        entries.append((code, description))
    return entries


def build_manufacturer_catalog(base_dir: Path) -> Dict[str, List[Dict[str, str]]]:
    catalog: Dict[str, List[Dict[str, str]]] = defaultdict(list)
    for child in base_dir.iterdir():
        if not child.is_dir():
            continue
        if not any(child.name.startswith(prefix) for prefix in SUPPORTED_MANUFACTURER_DIRS):
            continue
        manufacturer = detect_manufacturer_name(child)
        for txt in child.glob("*.txt"):
            entries = parse_manufacturer_file(txt)
            for code, description in entries:
                catalog[manufacturer].append(
                    {
                        "code": code,
                        "description": description,
                        "source": txt.name,
                    }
                )
    # stable ordering
    ordered = {
        manufacturer: sorted(items, key=lambda item: (item["code"], item["description"]))
        for manufacturer, items in sorted(catalog.items())
    }
    return ordered


def write_json(path: Path, payload) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp_path = path.with_suffix(path.suffix + ".tmp")
    with tmp_path.open("w", encoding="utf-8") as handle:
        json.dump(payload, handle, ensure_ascii=False, indent=2)
        handle.write("\n")
    tmp_path.replace(path)


def main() -> None:
    script_path = Path(__file__).resolve()
    android_dir = script_path.parents[2]

    parser = argparse.ArgumentParser(description="Build DTC catalogs")
    parser.add_argument("--base", type=Path, default=android_dir / "base", help="Path to android/base")
    parser.add_argument(
        "--generic-out",
        type=Path,
        default=android_dir
        / "feature-obd-core"
        / "src"
        / "main"
        / "resources"
        / "com"
        / "selfservice"
        / "obd"
        / "core"
        / "dtc"
        / "dtc.json",
        help="Output path for generic catalog",
    )
    parser.add_argument(
        "--manufacturer-out",
        type=Path,
        default=android_dir / "platform" / "data" / "src" / "main" / "assets" / "dtc_database.json",
        help="Output path for manufacturer catalog",
    )
    args = parser.parse_args()

    csharp_path = args.base / "OBDII.DTC-main" / "DTC.cs"
    dtcmapping_path = args.base / "dtcmapping.json"

    if not csharp_path.exists():
        raise FileNotFoundError(f"Missing C# catalog at {csharp_path}")
    if not dtcmapping_path.exists():
        raise FileNotFoundError(f"Missing dtcmapping at {dtcmapping_path}")

    generic_primary = parse_csharp_catalog(csharp_path)
    dtcmapping = load_dtcmapping(dtcmapping_path)
    generic_entries = merge_generic_entries(generic_primary, dtcmapping)

    manufacturer_catalog = build_manufacturer_catalog(args.base)

    write_json(
        args.generic_out,
        [
            {
                "code": entry.code,
                "system": entry.system,
                "label": entry.label,
                **({"notes": entry.notes} if entry.notes else {}),
            }
            for entry in generic_entries
        ],
    )
    write_json(args.manufacturer_out, manufacturer_catalog)

    print(
        f"Generated {len(generic_entries)} generic entries and "
        f"{sum(len(v) for v in manufacturer_catalog.values())} manufacturer entries",
    )


if __name__ == "__main__":
    main()
