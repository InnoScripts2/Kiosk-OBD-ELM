#!/usr/bin/env python3
"""Builds the PID catalog used by feature-obd-core from donor assets."""
from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Dict, Iterable, List, Optional, Tuple

ANDROID_OBD_ASSET_FILES = (
    "pids-mode1.json",
    "pids-mode4.json",
    "pids-mode9.json",
)

SOURCE_NOTE = "Dataset: AndroidOBD"


def parse_args() -> argparse.Namespace:
    script_path = Path(__file__).resolve()
    android_root = script_path.parents[2]
    default_output = (
        android_root
        / "feature-obd-core"
        / "src"
        / "main"
        / "resources"
        / "com"
        / "selfservice"
        / "obd"
        / "core"
        / "pids"
        / "pids.json"
    )
    default_donor = android_root.parent / "AndroidOBD-main" / "AndroidOBD-main" / "obd" / "src" / "main" / "assets"

    parser = argparse.ArgumentParser(description="Merge donor PID definitions into the kiosk catalog")
    parser.add_argument("--donor", type=Path, default=default_donor, help="Path to donor assets (pids-mode*.json)")
    parser.add_argument("--output", type=Path, default=default_output, help="Destination pids.json inside feature-obd-core")
    parser.add_argument(
        "--existing",
        type=Path,
        default=default_output,
        help="Path to existing pids.json used as merge base",
    )
    return parser.parse_args()


def load_existing(path: Path) -> Dict[Tuple[str, str], dict]:
    if not path.exists():
        return {}
    data = json.loads(path.read_text(encoding="utf-8"))
    result: Dict[Tuple[str, str], dict] = {}
    for entry in data:
        key = (entry["mode"], entry["pid"])
        result[key] = dict(entry)
    return result


def load_donor_entries(donor_dir: Path) -> Dict[Tuple[str, str], dict]:
    entries: Dict[Tuple[str, str], dict] = {}
    for filename in ANDROID_OBD_ASSET_FILES:
        asset_path = donor_dir / filename
        if not asset_path.exists():
            continue
        asset = json.loads(asset_path.read_text(encoding="utf-8"))
        for raw in asset.get("pids", []):
            normalized = normalize_donor_entry(raw)
            if not normalized:
                continue
            key = (normalized["mode"], normalized["pid"])
            entries[key] = normalized
    return entries


def normalize_donor_entry(raw: dict) -> Optional[dict]:
    mode = normalize_hex(raw.get("Mode"))
    pid = normalize_hex(raw.get("PID"))
    label = (raw.get("Description") or "").strip()
    if not mode or not pid or not label:
        return None
    if "pids supported" in label.lower():
        return None
    entry: dict = {"mode": mode, "pid": pid, "label": label}

    unit = normalize_unit(raw.get("Units"))
    if unit:
        entry["unit"] = unit

    minimum = parse_number(raw.get("Min"))
    maximum = parse_number(raw.get("Max"))
    if minimum is not None:
        entry["min"] = minimum
    if maximum is not None:
        entry["max"] = maximum

    formula = sanitize_formula(raw.get("Formula"))
    if formula:
        entry["formula"] = formula

    poll_interval = default_poll_interval(mode)
    if poll_interval is not None:
        entry["pollIntervalMs"] = poll_interval

    payload_length = parse_int(raw.get("Bytes"))
    if payload_length is not None:
        entry["payloadLengthBytes"] = payload_length

    notes = [SOURCE_NOTE]
    imperial_units = normalize_unit(raw.get("ImperialUnits"))
    imperial_formula = sanitize_formula(raw.get("ImperialFormula"))
    if imperial_units and imperial_formula:
        notes.append(f"Imperial: {imperial_formula} {imperial_units}")
    elif imperial_formula:
        notes.append(f"Imperial: {imperial_formula}")
    elif imperial_units:
        notes.append(f"Imperial units: {imperial_units}")

    if raw.get("isPersistent"):
        notes.append("Persistent")

    entry["notes"] = "; ".join(notes)
    return entry


def normalize_hex(value: Optional[str]) -> Optional[str]:
    if not value:
        return None
    cleaned = value.strip().upper()
    if cleaned.startswith("0X"):
        cleaned = cleaned[2:]
    if not cleaned:
        return None
    if len(cleaned) == 1:
        cleaned = f"0{cleaned}"
    return f"0x{cleaned}"


def normalize_unit(value: Optional[str]) -> Optional[str]:
    if value is None:
        return None
    cleaned = value.strip()
    if not cleaned:
        return None
    replacements = {
        "°C": "degC",
        "°F": "degF",
        "%": "percent",
        "km/h": "km_per_h",
        "mph": "miles_per_h",
        "grams/sec": "grams_per_sec",
        "grams/s": "grams_per_sec",
        "kPa (gauge)": "kPa_gauge",
        "kPa (absolute)": "kPa",
        "kPa": "kPa",
        "rpm": "rpm",
        "seconds": "seconds",
    }
    if cleaned in replacements:
        return replacements[cleaned]
    slug = cleaned.lower()
    slug = slug.replace("°", "deg")
    slug = slug.replace("/", "_per_")
    slug = slug.replace(" ", "_")
    slug = slug.replace("-", "_")
    slug = slug.replace("(", "_").replace(")", "")
    slug = slug.replace("%", "percent")
    parts = [part for part in slug.strip("_").split("_") if part]
    slug = "_".join(parts)
    return slug or None


def parse_number(value) -> Optional[float]:
    if value is None:
        return None
    if isinstance(value, (int, float)):
        return value
    text = str(value).strip()
    if not text:
        return None
    try:
        number = float(text)
    except ValueError:
        return None
    if number.is_integer():
        return int(number)
    return round(number, 6)


def parse_int(value) -> Optional[int]:
    numeric = parse_number(value)
    if numeric is None:
        return None
    try:
        return int(numeric)
    except (TypeError, ValueError):
        return None


def sanitize_formula(value: Optional[str]) -> Optional[str]:
    if value is None:
        return None
    formula = value.strip()
    return formula or None


def default_poll_interval(mode: str) -> Optional[int]:
    try:
        mode_int = int(mode, 16)
    except (TypeError, ValueError):
        return None
    if mode_int == 0x01:
        return 1000
    if mode_int == 0x04:
        return 2000
    if mode_int == 0x09:
        return 10000
    return None


def merge_entries(existing: Dict[Tuple[str, str], dict], donors: Dict[Tuple[str, str], dict]) -> List[dict]:
    merged = {key: dict(value) for key, value in existing.items()}
    for key, donor in donors.items():
        current = merged.get(key)
        if current is None:
            merged[key] = donor
            continue
        # Merge missing scalar fields only when absent in current entry.
        for field in ("unit", "min", "max", "formula", "pollIntervalMs", "payloadLengthBytes"):
            if field not in current and field in donor:
                current[field] = donor[field]
        # Append donor notes for provenance.
        note = donor.get("notes")
        if note:
            current["notes"] = merge_notes(current.get("notes"), note)
    merged_list = [merged[key] for key in sorted(merged.keys(), key=sort_key)]
    return [entry for entry in merged_list if not is_support_bitfield(entry)]


def sort_key(key: Tuple[str, str]) -> Tuple[int, int]:
    mode, pid = key
    return (int(mode, 16), int(pid, 16))


def is_support_bitfield(entry: dict) -> bool:
    label = entry.get("label", "")
    return isinstance(label, str) and "pids supported" in label.lower()


def merge_notes(existing_note: Optional[str], donor_note: str) -> str:
    segments: List[str] = []
    seen = set()
    had_donor = SOURCE_NOTE in existing_note if existing_note else False
    if existing_note:
        for part in existing_note.split(";"):
            cleaned = part.strip()
            if not cleaned:
                continue
            if cleaned.startswith(SOURCE_NOTE):
                continue
            if had_donor and cleaned.startswith("Imperial:"):
                continue
            if cleaned in seen:
                continue
            segments.append(cleaned)
            seen.add(cleaned)
    if donor_note and donor_note not in seen:
        segments.append(donor_note)
    return "; ".join(segments)


def write_catalog(path: Path, entries: Iterable[dict]) -> None:
    payload = list(entries)
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp = path.with_suffix(path.suffix + ".tmp")
    with tmp.open("w", encoding="utf-8") as handle:
        json.dump(payload, handle, ensure_ascii=False, indent=2)
        handle.write("\n")
    tmp.replace(path)


def main() -> None:
    args = parse_args()
    existing = load_existing(args.existing)
    donors = load_donor_entries(args.donor)
    merged = merge_entries(existing, donors)
    write_catalog(args.output, merged)
    print(f"Merged {len(donors)} donor entries into {len(merged)} total PIDs")


if __name__ == "__main__":
    main()
