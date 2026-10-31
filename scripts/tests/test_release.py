import importlib.util
from pathlib import Path
import pytest

spec = importlib.util.spec_from_file_location("prepare_release", Path(__file__).parents[1] / "prepare-release.py")
release = importlib.util.module_from_spec(spec)
spec.loader.exec_module(release)
ENV = {"GITHUB_SHA":"abc123","GITHUB_RUN_NUMBER":"7","GITHUB_REPOSITORY":"example/sevil","GITHUB_RUN_ID":"42"}

def test_missing_asset_blocks_publication(tmp_path):
    with pytest.raises(ValueError, match="missing or empty"):
        release.prepare(tmp_path,tmp_path / "notes.md",ENV)

def test_release_identifies_build_and_unsigned_ios(tmp_path):
    for name in release.ASSETS: (tmp_path / name).write_bytes(b"synthetic-test-artifact")
    manifest = release.prepare(tmp_path,tmp_path / "notes.md",ENV)
    assert manifest["commit"] == ENV["GITHUB_SHA"]
    assert len(manifest["assets"]) == 4
    assert all(len(a["sha256"]) == 64 for a in manifest["assets"])
    notes = (tmp_path / "notes.md").read_text(encoding="utf-8")
    assert "cannot be installed directly on an iPhone" in notes
    assert "requires uninstalling" not in notes  # Don't claim every update requires removal.
    assert (tmp_path / "INSTALLATION.md").read_text(encoding="utf-8") == notes
