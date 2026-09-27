import base64
import hashlib
import json
import os
import stat
import subprocess
import sys
from pathlib import Path

from dgp.cli.chrome import EXTENSION_ID, HOST_NAME

EXT_DIR = Path(__file__).parent.parent / "chrome-extension"


def _run(home, *args):
    env = {**os.environ, "HOME": str(home)}
    return subprocess.run([sys.executable, "-m", "dgp", *args],
                          env=env, capture_output=True, text=True)


def test_extension_id_matches_manifest_key():
    m = json.loads((EXT_DIR / "manifest.json").read_text("utf-8"))
    der = base64.b64decode(m["key"])
    eid = "".join(chr(ord("a") + int(c, 16)) for c in hashlib.sha256(der).hexdigest()[:32])
    assert eid == EXTENSION_ID


def test_manifest_shape():
    m = json.loads((EXT_DIR / "manifest.json").read_text("utf-8"))
    assert m["manifest_version"] == 3
    assert sorted(m["permissions"]) == ["activeTab", "nativeMessaging", "scripting"]
    assert "_execute_action" in m["commands"]
    assert "host_permissions" not in m
    popup = (EXT_DIR / "popup.js").read_text("utf-8")
    assert f"'{HOST_NAME}'" in popup
    assert "http" not in (EXT_DIR / "popup.html").read_text("utf-8")  # no remote code


def test_chrome_install(tmp_path):
    r = _run(tmp_path, "chrome-install")
    assert r.returncode == 0, r.stderr
    launcher = tmp_path / ".local/share/dgp/native-host"
    assert launcher.stat().st_mode & stat.S_IXUSR
    text = launcher.read_text()
    assert text.startswith("#!/bin/sh\n")
    assert f"{sys.executable} -m dgp native-host" in text
    for browser in ("google-chrome", "chromium"):
        m = json.loads((tmp_path / ".config" / browser / "NativeMessagingHosts" /
                        f"{HOST_NAME}.json").read_text())
        assert m == {
            "name": HOST_NAME,
            "description": "DGP password host",
            "path": str(launcher),
            "type": "stdio",
            "allowed_origins": [f"chrome-extension://{EXTENSION_ID}/"],
        }


def test_chrome_install_custom_id_and_dry_run(tmp_path):
    r = _run(tmp_path, "chrome-install", "--extension-id", "a" * 32, "--dry-run")
    assert r.returncode == 0
    assert f"chrome-extension://{'a' * 32}/" in r.stdout
    assert not (tmp_path / ".local").exists() and not (tmp_path / ".config").exists()


def test_chrome_install_rejects_bad_id(tmp_path):
    r = _run(tmp_path, "chrome-install", "--extension-id", "xyz")
    assert r.returncode == 1


def test_launcher_runs_host(tmp_path):
    r = _run(tmp_path, "chrome-install")
    assert r.returncode == 0
    body = b'{"op":"list"}'
    p = subprocess.run([str(tmp_path / ".local/share/dgp/native-host"), "chrome-extension://x/"],
                       input=len(body).to_bytes(4, sys.byteorder) + body,
                       capture_output=True, env={**os.environ, "HOME": str(tmp_path)})
    assert p.stderr == b""
    assert p.stdout[4:] == b'{"ok":true,"services":[]}'
