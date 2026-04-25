import os
import subprocess
import sys
import json
import pytest
from pathlib import Path
from dgp import engine
from dgp.service import serialize_services, new_service


def _run(tmp_path, *args, extra_env=None):
    env = {**os.environ, "DGP_CONFIG_DIR": str(tmp_path)}
    if extra_env:
        env.update(extra_env)
    return subprocess.run(
        [sys.executable, "-m", "dgp", *args],
        env=env, capture_output=True, text=True
    )


def test_gen_alnum_basic(tmp_path):
    r = _run(tmp_path, "gen", "aa", "--seed", "a", "--account", "")
    assert r.returncode == 0
    assert r.stdout.strip() == "oxToKKV2"


def test_gen_type_hexlong(tmp_path):
    expected = engine.generate("a", "aa", "hexlong", "")
    r = _run(tmp_path, "gen", "aa", "--seed", "a", "--account", "", "--type", "hexlong")
    assert r.returncode == 0
    assert r.stdout.strip() == expected


def test_gen_type_xkcdlong(tmp_path):
    expected = engine.generate("a", "aa", "xkcdlong", "")
    r = _run(tmp_path, "gen", "aa", "--seed", "a", "--account", "", "--type", "xkcdlong")
    assert r.returncode == 0
    assert r.stdout.strip() == expected


def test_gen_service_type_lookup(tmp_path):
    svc = new_service("mysvc", type="hexlong")
    (tmp_path / "services.json").write_text(serialize_services([svc]))
    expected = engine.generate("a", "mysvc", "hexlong", "")
    r = _run(tmp_path, "gen", "mysvc", "--seed", "a", "--account", "")
    assert r.returncode == 0
    assert r.stdout.strip() == expected


def test_gen_unknown_service_defaults_alnum(tmp_path):
    expected = engine.generate("a", "nonexistent", "alnum", "")
    r = _run(tmp_path, "gen", "nonexistent", "--seed", "a", "--account", "")
    assert r.returncode == 0
    assert r.stdout.strip() == expected


def test_gen_missing_seed_exits_2(tmp_path):
    r = _run(tmp_path, "gen", "svc")
    assert r.returncode == 2
    assert "no seed configured" in r.stderr


def test_test_vectors_passes(tmp_path):
    r = _run(tmp_path, "test-vectors")
    assert r.returncode == 0
    assert "passed" in r.stdout
