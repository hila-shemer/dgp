import os
import pytest
from dgp.service import DgpService, parse_services, serialize_services, new_service
from dgp import store


def _make_full_service() -> DgpService:
    return DgpService(
        id="test-id-1",
        name="myservice",
        type="alnum",
        comment="c",
        archived=False,
        pinned=True,
        tags=["a", "b"],
        encrypted_secret="xyz",
    )


def test_round_trip_idempotency():
    s = _make_full_service()
    j = serialize_services([s])
    result = parse_services(j)
    assert len(result) == 1
    j2 = serialize_services(result)
    assert j == j2


def test_omit_tags_when_empty():
    j = serialize_services([new_service("foo")])
    assert "tags" not in j


def test_omit_encrypted_secret_when_none():
    j = serialize_services([new_service("foo")])
    assert "encryptedSecret" not in j


def test_include_tags_when_present():
    s = DgpService(id="x", name="y", tags=["work"])
    j = serialize_services([s])
    assert "tags" in j
    parsed = parse_services(j)
    assert parsed[0].tags == ["work"]


def test_include_encrypted_secret_when_present():
    s = DgpService(id="x", name="y", encrypted_secret="abc")
    j = serialize_services([s])
    assert "encryptedSecret" in j
    parsed = parse_services(j)
    assert parsed[0].encrypted_secret == "abc"


def test_parse_services_minimal_input():
    result = parse_services('[{"id":"x","name":"y"}]')
    assert len(result) == 1
    assert result[0].name == "y"
    assert result[0].tags == []


def test_parse_services_malformed_json():
    assert parse_services("not json") == []


def test_parse_services_missing_required_field():
    assert parse_services('[{"name":"x"}]') == []


def test_serialize_compact_format():
    j = serialize_services([new_service("foo")])
    assert ", " not in j
    assert ": " not in j


def test_write_services_file_permissions(tmp_path, monkeypatch):
    monkeypatch.setenv("DGP_CONFIG_DIR", str(tmp_path))
    store.write_services([new_service("bar")])
    path = tmp_path / "services.json"
    assert path.exists()
    assert oct(os.stat(path).st_mode & 0o777) == oct(0o600)


def test_read_services_missing_file_returns_empty(tmp_path, monkeypatch):
    monkeypatch.setenv("DGP_CONFIG_DIR", str(tmp_path))
    assert store.read_services() == []


def test_read_write_services_round_trip(tmp_path, monkeypatch):
    monkeypatch.setenv("DGP_CONFIG_DIR", str(tmp_path))
    services = [new_service("alpha"), new_service("beta")]
    store.write_services(services)
    result = store.read_services()
    assert len(result) == 2
    assert {r.name for r in result} == {"alpha", "beta"}
