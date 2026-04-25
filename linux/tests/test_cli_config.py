from __future__ import annotations
import argparse
import json
import os
import subprocess
import sys
from pathlib import Path
from unittest.mock import MagicMock, patch

import pytest

from dgp import store
from dgp.cli import config
from dgp.exportcrypto import encrypt_export
from dgp.service import new_service, serialize_services


def _run(tmp_path, *args, extra_env=None, input=None):
    env = {**os.environ, "DGP_CONFIG_DIR": str(tmp_path)}
    if extra_env:
        env.update(extra_env)
    return subprocess.run(
        [sys.executable, "-m", "dgp", *args],
        env=env, capture_output=True, text=True, input=input
    )


# 1. config list on empty store
def test_list_empty(tmp_path):
    r = _run(tmp_path, "config", "list")
    assert r.returncode == 0
    assert r.stdout.strip() == ""


# 2. config add then config list
def test_add_then_list(tmp_path):
    r = _run(tmp_path, "config", "add", "mysvc")
    assert r.returncode == 0

    r = _run(tmp_path, "config", "list")
    assert r.returncode == 0
    assert "mysvc" in r.stdout
    assert "alnum" in r.stdout


# 3. config add duplicate name
def test_add_duplicate_exits_1(tmp_path):
    _run(tmp_path, "config", "add", "dup")
    r = _run(tmp_path, "config", "add", "dup")
    assert r.returncode == 1
    assert "already exists" in r.stderr


# 4. config remove existing
def test_remove_existing(tmp_path):
    _run(tmp_path, "config", "add", "torm")
    r = _run(tmp_path, "config", "remove", "torm")
    assert r.returncode == 0

    r = _run(tmp_path, "config", "list")
    assert "torm" not in r.stdout


# 5. config remove missing
def test_remove_missing_exits_1(tmp_path):
    r = _run(tmp_path, "config", "remove", "nosuchsvc")
    assert r.returncode == 1
    assert "not found" in r.stderr


# 6. export + import round-trip
def test_export_import_roundtrip(tmp_path):
    _run(tmp_path, "config", "add", "round_svc", "--comment", "hello")

    blob_file = tmp_path / "blob.b64"
    r = _run(tmp_path, "config", "export", "--pin", "hunter2", "--out", str(blob_file))
    assert r.returncode == 0
    assert blob_file.exists()

    r = _run(tmp_path, "config", "import", str(blob_file), "--pin", "hunter2")
    assert r.returncode == 0
    assert "Imported" in r.stdout

    r = _run(tmp_path, "config", "list")
    assert r.returncode == 0
    assert "round_svc" in r.stdout


# 7. import --plaintext
def test_import_plaintext(tmp_path):
    svc = new_service("pt_svc", type="hexlong", comment="plain")
    import_file = tmp_path / "import.json"
    import_file.write_text(serialize_services([svc]))

    r = _run(tmp_path, "config", "import", str(import_file), "--plaintext")
    assert r.returncode == 0
    assert "Imported 1" in r.stdout

    r = _run(tmp_path, "config", "list")
    assert r.returncode == 0
    assert "pt_svc" in r.stdout
    assert "hexlong" in r.stdout


# 8. export --to-clipboard (mocked)
def test_export_to_clipboard(tmp_path):
    with patch.dict(os.environ, {"DGP_CONFIG_DIR": str(tmp_path)}):
        svc = new_service("clip_svc")
        store.write_services([svc])

        args = argparse.Namespace(to_clipboard=True, out=None, pin="testpin")

        with patch("dgp.cli.config.shutil.which", return_value="/usr/bin/wl-copy"), \
             patch("dgp.cli.config.subprocess.run") as mock_run:
            result = config._export_cmd(args)

    assert result == 0
    mock_run.assert_called_once()
    cmd = mock_run.call_args[0][0]
    assert cmd[0] == "wl-copy"
    assert mock_run.call_args[1].get("input") is not None


# 9. import --from-clipboard (mocked)
def test_import_from_clipboard(tmp_path):
    svc = new_service("import_clip_svc", type="alnum")
    blob = encrypt_export(serialize_services([svc]), "pin456")

    with patch.dict(os.environ, {"DGP_CONFIG_DIR": str(tmp_path)}):
        mock_result = MagicMock()
        mock_result.stdout = blob

        args = argparse.Namespace(
            from_clipboard=True,
            file=None,
            plaintext=False,
            pin="pin456",
        )

        with patch("dgp.cli.config.shutil.which", return_value="/usr/bin/wl-paste"), \
             patch("dgp.cli.config.subprocess.run", return_value=mock_result):
            result = config._import_cmd(args)

        assert result == 0
        services = store.read_services()
        assert any(s.name == "import_clip_svc" for s in services)


# 10. config edit with stub EDITOR
def test_config_edit(tmp_path):
    _run(tmp_path, "config", "add", "editsvc", "--comment", "original")

    stub_py = tmp_path / "editor_stub.py"
    stub_py.write_text(
        "import sys, json, pathlib\n"
        "path = pathlib.Path(sys.argv[1])\n"
        "data = json.loads(path.read_text())\n"
        'data["comment"] = "edited"\n'
        "path.write_text(json.dumps(data))\n"
    )

    stub_sh = tmp_path / "editor_stub.sh"
    stub_sh.write_text(f"#!/bin/sh\n{sys.executable} {stub_py} \"$1\"\n")
    stub_sh.chmod(0o755)

    r = _run(tmp_path, "config", "edit", "editsvc",
             extra_env={"EDITOR": str(stub_sh)})
    assert r.returncode == 0

    r = _run(tmp_path, "config", "list", "--json")
    assert r.returncode == 0
    services = json.loads(r.stdout)
    svc = next(s for s in services if s["name"] == "editsvc")
    assert svc["comment"] == "edited"


# 11. import without --pin and without TTY
def test_import_no_pin_no_tty(tmp_path):
    dummy_file = tmp_path / "dummy.blob"
    dummy_file.write_text("notanencryptedblobatall")

    r = _run(tmp_path, "config", "import", str(dummy_file), input="")
    assert r.returncode != 0
    assert "pin" in r.stderr.lower() or "PIN" in r.stderr
