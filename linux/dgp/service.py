from __future__ import annotations
import json
import uuid
from dataclasses import dataclass, field


@dataclass
class DgpService:
    id: str
    name: str
    type: str = "alnum"
    comment: str = ""
    archived: bool = False
    pinned: bool = False
    tags: list[str] = field(default_factory=list)
    encrypted_secret: str | None = None


def parse_services(json_str: str) -> list[DgpService]:
    try:
        arr = json.loads(json_str)
        if not isinstance(arr, list):
            return []
        services = []
        for obj in arr:
            if not isinstance(obj, dict):
                return []
            sid = obj.get("id")
            name = obj.get("name")
            if not isinstance(sid, str) or not isinstance(name, str):
                return []
            tags = obj.get("tags") or []
            if not isinstance(tags, list):
                tags = []
            encrypted_secret = obj.get("encryptedSecret") or None
            services.append(DgpService(
                id=sid,
                name=name,
                type=obj.get("type", "alnum"),
                comment=obj.get("comment", ""),
                archived=obj.get("archived", False),
                pinned=obj.get("pinned", False),
                tags=tags,
                encrypted_secret=encrypted_secret,
            ))
        return services
    except Exception:
        return []


def serialize_services(services: list[DgpService]) -> str:
    arr = []
    for s in services:
        d: dict = {
            "id": s.id,
            "name": s.name,
            "type": s.type,
            "comment": s.comment,
            "archived": s.archived,
            "pinned": s.pinned,
        }
        if s.tags:
            d["tags"] = s.tags
        if s.encrypted_secret is not None:
            d["encryptedSecret"] = s.encrypted_secret
        arr.append(d)
    return json.dumps(arr, separators=(",", ":"))


def new_service(name: str, type: str = "alnum", **kwargs) -> DgpService:
    return DgpService(id=str(uuid.uuid4()), name=name, type=type, **kwargs)
