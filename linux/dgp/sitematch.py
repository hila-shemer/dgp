"""Match a web host or Android package against service entries.

Rules are shared with the Android engine (com.dgp.engine.SiteMatcher); the case
table tests/fixtures/sitematch-cases.json is run by both test suites. There is
no public-suffix list, on purpose.
"""
from __future__ import annotations

from dgp.service import DgpService

_SLD_WORDS = {"co", "com", "net", "org", "ac", "gov", "edu", "ltd", "plc"}
_PKG_NOISE = {"com", "org", "net", "io", "app", "apps", "android", "www", "mobile"}


def normalize_host(h: str) -> str:
    """Lowercase; strip scheme, userinfo, path, port, trailing dot, one leading www."""
    h = h.strip().lower()
    if "://" in h:
        h = h.split("://", 1)[1]
    for sep in "/?#":
        h = h.split(sep, 1)[0]
    if "@" in h:
        h = h.rsplit("@", 1)[1]
    if h.startswith("["):  # IPv6 literal
        h = h[1:].split("]", 1)[0]
    elif ":" in h:
        h = h.split(":", 1)[0]
    h = h.rstrip(".")
    if h.startswith("www."):
        h = h[4:]
    return h


def registrable(h: str) -> str:
    labels = h.split(".")
    if len(labels) >= 3 and len(labels[-1]) == 2 and labels[-2] in _SLD_WORDS:
        return ".".join(labels[-3:])
    return ".".join(labels[-2:])


def _name_key(name: str) -> str:
    return name.lower().replace(" ", "")


def match(services: list[DgpService], kind: str, value: str) -> list[DgpService]:
    """kind is "web" (value: host or URL) or "app" (value: package name).

    Site matches first, then name-fallback matches, each in list order.
    Archived entries never match.
    """
    live = [s for s in services if not s.archived]
    if kind == "web":
        host = normalize_host(value)
        if not host:
            return []
        reg = registrable(host)
        names = {reg, reg.split(".")[0]}

        def site_hit(s: DgpService) -> bool:
            return any(host == x or host.endswith("." + x) for x in s.sites)
    elif kind == "app":
        pkg = value.strip().lower()
        if not pkg:
            return []
        names = {lab for lab in pkg.split(".") if lab and lab not in _PKG_NOISE}

        def site_hit(s: DgpService) -> bool:
            return pkg in s.sites
    else:
        raise ValueError(f"unknown kind {kind!r}")

    by_site = [s for s in live if s.sites and site_hit(s)]
    by_name = [s for s in live if not s.sites and _name_key(s.name) in names]
    return by_site + by_name
