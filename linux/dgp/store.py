from __future__ import annotations
import os
import tempfile
from pathlib import Path

from .service import DgpService, parse_services, serialize_services


def _config_dir() -> Path:
    env = os.environ.get("DGP_CONFIG_DIR")
    if env:
        return Path(env)
    return Path.home() / ".config" / "dgp"


def _ensure_config_dir() -> Path:
    d = _config_dir()
    d.mkdir(mode=0o700, parents=True, exist_ok=True)
    return d


def read_text_file(path: Path) -> str:
    return path.read_bytes().decode("utf-8")


def write_text_file(path: Path, content: str) -> None:
    _ensure_config_dir()
    parent = path.parent
    parent.mkdir(mode=0o700, parents=True, exist_ok=True)
    fd, tmp = tempfile.mkstemp(dir=parent)
    try:
        with os.fdopen(fd, "wb") as f:
            f.write(content.encode("utf-8"))
        os.chmod(tmp, 0o600)
        os.replace(tmp, path)
    except Exception:
        os.unlink(tmp)
        raise


def seed_path() -> Path:
    return _config_dir() / "seed"


def account_path() -> Path:
    return _config_dir() / "account"


def read_services() -> list[DgpService]:
    path = _config_dir() / "services.json"
    try:
        return parse_services(read_text_file(path))
    except FileNotFoundError:
        return []


def write_services(services: list[DgpService]) -> None:
    path = _config_dir() / "services.json"
    write_text_file(path, serialize_services(services))
