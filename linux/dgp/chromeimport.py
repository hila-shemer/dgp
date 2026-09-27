"""Import a Google Password Manager CSV export into DGP entries.

For each row: if any existing entry already derives the same password, DGP
generated it and only the site is recorded on that entry. Every entry is tried
(site matches first), because Hila's entry names are arbitrary and the name
fallback in sitematch is deliberately strict; password equality is the evidence.
Otherwise the password becomes a `vault` entry. Re-running the import is
harmless: a vault entry for the site that already decrypts to the row's
password makes the row a no-op. Nothing here prints or logs a password.
"""
from __future__ import annotations

import csv
from dataclasses import dataclass, field
from typing import Iterable, TextIO

from dgp import engine, vault
from dgp.cli import USER_VISIBLE_TYPES
from dgp.service import DgpService, new_service
from dgp.sitematch import match, normalize_host

IMPORT_TAG = "chrome-import"


@dataclass
class ImportReport:
    generated: int = 0          # rows DGP already derives
    already_imported: int = 0   # rows matching an existing vault entry
    skipped: int = 0            # rows with no password or no usable url
    new_entries: list[str] = field(default_factory=list)
    sites_added: list[str] = field(default_factory=list)  # "name: site"


class CsvFormatError(Exception):
    pass


def read_rows(f: TextIO) -> list[dict[str, str]]:
    """Rows keyed by lowercased header name. Only `url` is required."""
    reader = csv.reader(f)
    try:
        header = [h.strip().lower() for h in next(reader)]
    except StopIteration:
        raise CsvFormatError("empty CSV")
    if "url" not in header:
        raise CsvFormatError("CSV has no 'url' column")
    rows = []
    for rec in reader:
        rows.append({h: (rec[i] if i < len(rec) else "") for i, h in enumerate(header)})
    return rows


def site_of(url: str) -> tuple[str, str] | None:
    """(kind, site) for a CSV url: ("app", package) or ("web", host)."""
    url = url.strip()
    if url.lower().startswith("android://"):
        rest = url[len("android://"):]
        pkg = rest.rsplit("@", 1)[-1].split("/", 1)[0].strip().lower()
        return ("app", pkg) if pkg else None
    host = normalize_host(url)
    return ("web", host) if host else None


def _unique_name(site: str, username: str, taken: set[str]) -> str:
    base = site if site not in taken else (f"{site} ({username})" if username else site)
    name, n = base, 2
    while name in taken:
        name = f"{base} #{n}"
        n += 1
    return name


def import_rows(rows: Iterable[dict[str, str]], services: list[DgpService],
                seed: str, account: str) -> ImportReport:
    """Apply the rows to `services` in place and report what changed."""
    rep = ImportReport()
    derived: dict[str, str] = {}

    def derive(s: DgpService) -> str:
        if s.id not in derived:
            derived[s.id] = engine.generate(seed, s.name, s.type, account)
        return derived[s.id]

    for row in rows:
        password = row.get("password", "")
        where = site_of(row.get("url", ""))
        if not password or where is None:
            rep.skipped += 1
            continue
        kind, site = where
        username = row.get("username", "").strip()

        matched = match(services, kind, site)
        candidates = matched + [s for s in services if s not in matched]
        hit = next((s for s in candidates
                    if s.type in USER_VISIBLE_TYPES and derive(s) == password), None)
        if hit is not None:
            rep.generated += 1
            if site not in hit.sites:
                hit.sites.append(site)
                rep.sites_added.append(f"{hit.name}: {site}")
            continue

        if any(s.type == "vault" and site in s.sites and s.encrypted_secret and
               vault.decrypt_vault(s.encrypted_secret, seed, s.name, account) == password
               for s in services):
            rep.already_imported += 1
            continue

        name = _unique_name(site, username, {s.name for s in services})
        services.append(new_service(
            name=name,
            type="vault",
            comment=username,
            tags=[IMPORT_TAG],
            sites=[site],
            encrypted_secret=vault.encrypt_vault(password, seed, name, account),
        ))
        rep.new_entries.append(name)
    return rep
