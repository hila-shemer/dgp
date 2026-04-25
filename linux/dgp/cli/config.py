from __future__ import annotations
import argparse
import getpass
import json
import os
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

from dgp import store
from dgp.service import DgpService, new_service, parse_services, serialize_services
from dgp.exportcrypto import encrypt_export, decrypt_export
from dgp.vault import encrypt_vault, decrypt_vault
from dgp.cli import USER_VISIBLE_TYPES, resolve_seed, resolve_account, _add_common_args


def _clipboard_read() -> str:
    if shutil.which("wl-paste"):
        r = subprocess.run(["wl-paste"], capture_output=True, text=True)
        return r.stdout
    elif shutil.which("xclip"):
        r = subprocess.run(
            ["xclip", "-selection", "clipboard", "-o"], capture_output=True, text=True
        )
        return r.stdout
    print("Error: neither wl-paste nor xclip found.", file=sys.stderr)
    sys.exit(1)


def _clipboard_write(content: str) -> None:
    if shutil.which("wl-copy"):
        subprocess.run(["wl-copy"], input=content, text=True)
    elif shutil.which("xclip"):
        subprocess.run(["xclip", "-selection", "clipboard", "-i"], input=content, text=True)
    else:
        print("Error: neither wl-copy nor xclip found.", file=sys.stderr)
        sys.exit(1)


def register(subparsers: argparse._SubParsersAction) -> None:
    config_parser = subparsers.add_parser("config", help="Manage service configuration")
    sub = config_parser.add_subparsers(dest="config_cmd", required=True)

    list_p = sub.add_parser("list", help="List services")
    list_p.add_argument("--archived", action="store_true", help="Include archived services")
    list_p.add_argument("--json", action="store_true", help="Output raw JSON")
    list_p.set_defaults(func=_list_cmd)

    add_p = sub.add_parser("add", help="Add a service")
    add_p.add_argument("name", help="Service name")
    add_p.add_argument(
        "--type", choices=USER_VISIBLE_TYPES + ["vault"], default="alnum", dest="entry_type"
    )
    add_p.add_argument("--vault", action="store_true", help="Shorthand for --type vault")
    add_p.add_argument("--comment", default="")
    add_p.add_argument("--pin", action="store_true", dest="pinned", help="Mark as pinned")
    add_p.add_argument("--tag", action="append", dest="tags", metavar="X", default=[])
    add_p.add_argument("--secret-file", metavar="PATH")
    _add_common_args(add_p)
    add_p.set_defaults(func=_add_cmd)

    remove_p = sub.add_parser("remove", help="Remove a service")
    remove_p.add_argument("name", help="Service name")
    remove_p.set_defaults(func=_remove_cmd)

    edit_p = sub.add_parser("edit", help="Edit a service in $EDITOR")
    edit_p.add_argument("name", help="Service name")
    _add_common_args(edit_p)
    edit_p.set_defaults(func=_edit_cmd)

    import_p = sub.add_parser("import", help="Import services")
    import_p.add_argument("file", nargs="?", default=None, metavar="FILE")
    import_p.add_argument("--plaintext", action="store_true", help="Content is raw JSON")
    import_p.add_argument("--from-clipboard", action="store_true")
    import_p.add_argument("--pin", default=None, help="Decryption PIN")
    import_p.set_defaults(func=_import_cmd)

    export_p = sub.add_parser("export", help="Export services")
    export_p.add_argument("--out", metavar="PATH", default=None, help="Output file (- for stdout)")
    export_p.add_argument("--to-clipboard", action="store_true")
    export_p.add_argument("--pin", default=None, help="Encryption PIN")
    export_p.set_defaults(func=_export_cmd)


def _list_cmd(args: argparse.Namespace) -> int:
    services = store.read_services()
    if args.json:
        print(serialize_services(services))
        return 0
    for s in services:
        if s.archived and not args.archived:
            continue
        pin_mark = " [P]" if s.pinned else ""
        comment = f"  {s.comment}" if s.comment else ""
        print(f"{s.name}  {s.type}{pin_mark}{comment}")
    return 0


def _add_cmd(args: argparse.Namespace) -> int:
    entry_type = "vault" if args.vault else args.entry_type
    services = store.read_services()
    if any(s.name == args.name for s in services):
        print(f"Error: service '{args.name}' already exists.", file=sys.stderr)
        return 1

    encrypted_secret = None
    if entry_type == "vault":
        seed = resolve_seed(args)
        account = resolve_account(args)
        if args.secret_file:
            secret = Path(args.secret_file).read_text("utf-8").strip()
        elif not sys.stdin.isatty():
            print("Error: vault type requires --secret-file when not a TTY.", file=sys.stderr)
            return 1
        else:
            s1 = getpass.getpass("Secret: ")
            s2 = getpass.getpass("Confirm secret: ")
            if s1 != s2:
                print("Error: secrets do not match.", file=sys.stderr)
                return 1
            secret = s1
        encrypted_secret = encrypt_vault(secret, seed, args.name, account)

    svc = new_service(
        name=args.name,
        type=entry_type,
        comment=args.comment,
        pinned=args.pinned,
        tags=args.tags,
        encrypted_secret=encrypted_secret,
    )
    services.append(svc)
    store.write_services(services)
    return 0


def _remove_cmd(args: argparse.Namespace) -> int:
    services = store.read_services()
    new_services = [s for s in services if s.name != args.name]
    if len(new_services) == len(services):
        print(f"Error: service '{args.name}' not found.", file=sys.stderr)
        return 1
    store.write_services(new_services)
    return 0


def _edit_cmd(args: argparse.Namespace) -> int:
    services = store.read_services()
    idx = next((i for i, s in enumerate(services) if s.name == args.name), None)
    if idx is None:
        print(f"Error: service '{args.name}' not found.", file=sys.stderr)
        return 1

    svc = services[idx]
    is_vault = svc.type == "vault"

    data: dict = {
        "id": svc.id,
        "name": svc.name,
        "type": svc.type,
        "comment": svc.comment,
        "archived": svc.archived,
        "pinned": svc.pinned,
        "tags": svc.tags,
    }
    seed = account = None
    if is_vault:
        seed = resolve_seed(args)
        account = resolve_account(args)
        plaintext = decrypt_vault(svc.encrypted_secret, seed, svc.name, account)
        if plaintext is None:
            print("Error: failed to decrypt vault entry.", file=sys.stderr)
            return 1
        data["secret"] = plaintext

    fd, tmp_file = tempfile.mkstemp(suffix=".json")
    try:
        with os.fdopen(fd, "w") as f:
            json.dump(data, f, indent=2)

        editor = os.environ.get("EDITOR", "vi")
        subprocess.run([editor, tmp_file])

        with open(tmp_file) as f:
            edited = json.load(f)

        encrypted_secret = svc.encrypted_secret
        if is_vault:
            secret = edited.pop("secret", "")
            encrypted_secret = encrypt_vault(
                secret, seed, edited.get("name", svc.name), account
            )

        new_svc = DgpService(
            id=edited.get("id", svc.id),
            name=edited.get("name", svc.name),
            type=edited.get("type", svc.type),
            comment=edited.get("comment", svc.comment),
            archived=edited.get("archived", svc.archived),
            pinned=edited.get("pinned", svc.pinned),
            tags=edited.get("tags", svc.tags),
            encrypted_secret=encrypted_secret,
        )
        services[idx] = new_svc
        store.write_services(services)
    finally:
        try:
            os.unlink(tmp_file)
        except FileNotFoundError:
            pass

    return 0


def _import_cmd(args: argparse.Namespace) -> int:
    if args.from_clipboard and args.file is not None:
        print("Error: cannot use both <file> and --from-clipboard.", file=sys.stderr)
        return 1

    if not args.from_clipboard and args.file is None:
        print("Error: specify a file or use --from-clipboard.", file=sys.stderr)
        return 1

    if args.from_clipboard:
        blob = _clipboard_read()
    else:
        blob = Path(args.file).read_text("utf-8").strip()

    if args.plaintext:
        plaintext = blob
    else:
        pin = args.pin
        if pin is None:
            if not sys.stdin.isatty():
                print("Error: --pin required when not a TTY.", file=sys.stderr)
                return 1
            pin = getpass.getpass("PIN: ")
        plaintext = decrypt_export(blob, pin)
        if plaintext is None:
            print("Error: failed to decrypt import.", file=sys.stderr)
            return 1

    incoming = parse_services(plaintext)
    current = store.read_services()
    current_by_id = {s.id: i for i, s in enumerate(current)}
    n_new = 0
    n_updated = 0
    for svc in incoming:
        if svc.id in current_by_id:
            current[current_by_id[svc.id]] = svc
            n_updated += 1
        else:
            current.append(svc)
            n_new += 1
    store.write_services(current)
    print(f"Imported {len(incoming)} services ({n_new} new, {n_updated} updated).")
    return 0


def _export_cmd(args: argparse.Namespace) -> int:
    services = store.read_services()
    plaintext = serialize_services(services)

    pin = args.pin
    if pin is None:
        if not sys.stdin.isatty():
            print("Error: --pin required when not a TTY.", file=sys.stderr)
            return 1
        pin = getpass.getpass("PIN: ")

    blob = encrypt_export(plaintext, pin)

    if args.to_clipboard:
        _clipboard_write(blob)
    elif args.out is not None and args.out != "-":
        Path(args.out).write_text(blob)
    else:
        print(blob)

    print(f"Exported {len(services)} services.", file=sys.stderr)
    return 0
