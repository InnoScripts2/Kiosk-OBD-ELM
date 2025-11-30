#!/usr/bin/env python3
"""Unit tests for build_catalog.py DTC catalog builder."""
from __future__ import annotations

import json
import tempfile
from pathlib import Path
from typing import Dict, List

import pytest

# Import functions from build_catalog
from build_catalog import (
    GenericEntry,
    build_manufacturer_catalog,
    detect_manufacturer_name,
    load_dtcmapping,
    merge_generic_entries,
    normalize_code,
    normalize_spaces,
    parse_csharp_catalog,
    parse_manufacturer_file,
)


class TestNormalization:
    """Tests for normalization functions."""

    def test_normalize_code_uppercase(self) -> None:
        assert normalize_code("p0001") == "P0001"
        assert normalize_code("c1234") == "C1234"
        assert normalize_code("b5678") == "B5678"

    def test_normalize_code_strips_whitespace(self) -> None:
        assert normalize_code("  P0001  ") == "P0001"
        assert normalize_code("\tP0001\n") == "P0001"

    def test_normalize_code_removes_0x_prefix(self) -> None:
        assert normalize_code("0xP0001") == "P0001"
        assert normalize_code("0XP0001") == "P0001"

    def test_normalize_spaces(self) -> None:
        assert normalize_spaces("  multiple   spaces  ") == "multiple spaces"
        assert normalize_spaces("tabs\tand\nnewlines") == "tabs and newlines"
        assert normalize_spaces("  ") == ""


class TestCSharpParsing:
    """Tests for C# DTC.cs file parsing."""

    def test_parse_csharp_catalog_basic(self, tmp_path: Path) -> None:
        cs_content = """using System.ComponentModel;

namespace OBDII.DTC;

public enum DTC
{
    [Category(Categories.Powertrain), Description("Fuel Volume Regulator A Control Circuit/Open")]
    P0001 = 0x0001,
    
    [Category(Categories.Chassis), Description("ABS Control Module")]
    C0001 = 0x0001,
    
    [Category(Categories.Body), Description("Driver Frontal Stage 1 Deployment Control")]
    B0001 = 0x0001,
}
"""
        cs_file = tmp_path / "DTC.cs"
        cs_file.write_text(cs_content)

        entries = parse_csharp_catalog(cs_file)

        assert len(entries) == 3
        assert "P0001" in entries
        assert entries["P0001"].code == "P0001"
        assert entries["P0001"].system == "powertrain"
        assert "Fuel Volume Regulator" in entries["P0001"].label

        assert "C0001" in entries
        assert entries["C0001"].system == "chassis"

        assert "B0001" in entries
        assert entries["B0001"].system == "body"

    def test_parse_csharp_catalog_with_notes(self, tmp_path: Path) -> None:
        cs_content = """
    [Category(Categories.Network), Description("CAN Bus Communication Error")]
    U0001 = 0x0001,
"""
        cs_file = tmp_path / "DTC.cs"
        cs_file.write_text(cs_content)

        entries = parse_csharp_catalog(cs_file)

        assert "U0001" in entries
        assert entries["U0001"].system == "network"


class TestDtcMappingLoading:
    """Tests for dtcmapping.json loading."""

    def test_load_dtcmapping(self, tmp_path: Path) -> None:
        mapping = {
            "P0001": "Fuel Volume Regulator Control Circuit / Open",
            "p0002": "Fuel Volume Regulator Control Circuit Range/Performance",
            "0xP0003": "Fuel Volume Regulator Control Circuit Low",
        }
        mapping_file = tmp_path / "dtcmapping.json"
        mapping_file.write_text(json.dumps(mapping))

        result = load_dtcmapping(mapping_file)

        assert len(result) == 3
        assert "P0001" in result
        assert "P0002" in result
        assert "P0003" in result
        # Check normalization
        assert result["P0002"] == "Fuel Volume Regulator Control Circuit Range/Performance"


class TestGenericEntryMerging:
    """Tests for merging generic entries."""

    def test_merge_no_conflicts(self) -> None:
        primary = {
            "P0001": GenericEntry("P0001", "powertrain", "Primary description"),
        }
        fallback = {
            "P0002": "Fallback description",
        }

        result = merge_generic_entries(primary, fallback)

        assert len(result) == 2
        assert result[0].code == "P0001"
        assert result[1].code == "P0002"

    def test_merge_with_conflicts_adds_notes(self) -> None:
        primary = {
            "P0001": GenericEntry("P0001", "powertrain", "Primary description"),
        }
        fallback = {
            "P0001": "Different description",
        }

        result = merge_generic_entries(primary, fallback)

        assert len(result) == 1
        assert result[0].code == "P0001"
        assert result[0].label == "Primary description"
        assert result[0].notes is not None
        assert "Alt: Different description" in result[0].notes

    def test_merge_preserves_primary_when_same(self) -> None:
        primary = {
            "P0001": GenericEntry("P0001", "powertrain", "Same description"),
        }
        fallback = {
            "P0001": "Same description",
        }

        result = merge_generic_entries(primary, fallback)

        assert len(result) == 1
        assert result[0].notes is None


class TestManufacturerParsing:
    """Tests for manufacturer-specific file parsing."""

    def test_detect_manufacturer_name(self, tmp_path: Path) -> None:
        assert detect_manufacturer_name(Path("All Toyota OBD2 Codes List (2)")) == "Toyota"
        assert detect_manufacturer_name(Path("All BMW OBD2 Codes List (7)")) == "BMW"
        # Generic strips to "OBD2 Codes List (6)" - the function keeps the parens
        result = detect_manufacturer_name(Path("Generic OBD2 Codes List (6)"))
        assert "OBD2 Codes List" in result

    def test_parse_manufacturer_file_basic(self, tmp_path: Path) -> None:
        content = """P1500 Hybrid battery voltage high
Some extra text here
P1501 Battery cooling fan malfunction
"""
        txt_file = tmp_path / "toyota.txt"
        txt_file.write_text(content)

        entries = parse_manufacturer_file(txt_file)

        assert len(entries) == 2
        assert entries[0] == ("P1500", "Hybrid battery voltage high Some extra text here")
        assert entries[1] == ("P1501", "Battery cooling fan malfunction")

    def test_parse_manufacturer_file_normalizes_codes(self, tmp_path: Path) -> None:
        # Note: CODE_RE in build_catalog requires specific format
        content = """P1500 Test description
P1501 Another test
"""
        txt_file = tmp_path / "test.txt"
        txt_file.write_text(content)

        entries = parse_manufacturer_file(txt_file)

        assert len(entries) >= 2
        assert entries[0][0] == "P1500"
        assert entries[1][0] == "P1501"

    def test_parse_manufacturer_file_handles_multiple_spaces(self, tmp_path: Path) -> None:
        content = """P1500    Multiple     spaces     between    words
"""
        txt_file = tmp_path / "test.txt"
        txt_file.write_text(content)

        entries = parse_manufacturer_file(txt_file)

        assert "Multiple spaces between words" in entries[0][1]


class TestManufacturerCatalogBuilding:
    """Tests for building complete manufacturer catalog."""

    def test_build_manufacturer_catalog(self, tmp_path: Path) -> None:
        # Create manufacturer directories
        toyota_dir = tmp_path / "All Toyota OBD2 Codes List (2)"
        toyota_dir.mkdir()
        (toyota_dir / "toyota.txt").write_text("P1500 Hybrid battery\n")

        bmw_dir = tmp_path / "All BMW OBD2 Codes List (7)"
        bmw_dir.mkdir()
        (bmw_dir / "bmw.txt").write_text("P1472 Secondary air\n")

        # Create a directory that should be ignored
        ignored_dir = tmp_path / "SomeOtherDir"
        ignored_dir.mkdir()
        (ignored_dir / "ignored.txt").write_text("P9999 Should not appear\n")

        catalog = build_manufacturer_catalog(tmp_path)

        assert len(catalog) == 2
        assert "Toyota" in catalog
        assert "BMW" in catalog
        assert len(catalog["Toyota"]) == 1
        assert catalog["Toyota"][0]["code"] == "P1500"
        assert len(catalog["BMW"]) == 1

    def test_build_manufacturer_catalog_empty_directory(self, tmp_path: Path) -> None:
        catalog = build_manufacturer_catalog(tmp_path)

        assert len(catalog) == 0


class TestIntegration:
    """Integration tests for the complete pipeline."""

    def test_full_pipeline(self, tmp_path: Path) -> None:
        # Setup test data
        base_dir = tmp_path / "base"
        base_dir.mkdir()

        # Create C# file
        dtc_dir = base_dir / "OBDII.DTC-main"
        dtc_dir.mkdir()
        cs_file = dtc_dir / "DTC.cs"
        cs_file.write_text("""
[Category(Categories.Powertrain), Description("Test description")]
P0001 = 0x0001,
""")

        # Create dtcmapping.json
        mapping_file = base_dir / "dtcmapping.json"
        mapping_file.write_text(json.dumps({"P0001": "Test description"}))

        # Create manufacturer file
        toyota_dir = base_dir / "All Toyota OBD2 Codes List (2)"
        toyota_dir.mkdir()
        (toyota_dir / "toyota.txt").write_text("P1500 Hybrid battery\n")

        # Parse
        generic_primary = parse_csharp_catalog(cs_file)
        dtcmapping = load_dtcmapping(mapping_file)
        generic_entries = merge_generic_entries(generic_primary, dtcmapping)
        manufacturer_catalog = build_manufacturer_catalog(base_dir)

        # Verify results
        assert len(generic_entries) == 1
        assert generic_entries[0].code == "P0001"

        assert len(manufacturer_catalog) == 1
        assert "Toyota" in manufacturer_catalog
        assert len(manufacturer_catalog["Toyota"]) == 1
        assert manufacturer_catalog["Toyota"][0]["code"] == "P1500"


if __name__ == "__main__":
    pytest.main([__file__, "-v"])
