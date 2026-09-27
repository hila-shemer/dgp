import json
from pathlib import Path

import pytest

from dgp.service import DgpService
from dgp.sitematch import match, normalize_host, registrable

_FIXTURE = json.loads(
    (Path(__file__).parent / "fixtures" / "sitematch-cases.json").read_text("utf-8"))
_SERVICES = [
    DgpService(id=o["id"], name=o["name"], sites=o.get("sites", []),
               archived=o.get("archived", False))
    for o in _FIXTURE["services"]
]


@pytest.mark.parametrize("case", _FIXTURE["cases"], ids=lambda c: f'{c["kind"]}:{c["input"]}')
def test_fixture_case(case):
    got = [s.id for s in match(_SERVICES, case["kind"], case["input"])]
    assert got == case["expect"]


def test_normalize_host():
    assert normalize_host("https://WWW.GitHub.com.:443/login?x=1") == "github.com"
    assert normalize_host("github.com") == "github.com"
    assert normalize_host("http://user@www.www.a.com/") == "www.a.com"
    assert normalize_host("android://abc==@com.foo.bar/") == "com.foo.bar"


def test_registrable():
    assert registrable("a.b.co.il") == "b.co.il"
    assert registrable("a.b.example.com") == "example.com"
    assert registrable("a.b.example.io") == "example.io"
    assert registrable("localhost") == "localhost"
