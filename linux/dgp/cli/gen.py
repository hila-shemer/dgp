from __future__ import annotations
import argparse
from dgp import engine, store
from dgp.cli import USER_VISIBLE_TYPES, resolve_seed, resolve_account, _add_common_args


def register(subparsers: argparse._SubParsersAction) -> None:
    p = subparsers.add_parser("gen", help="Generate a password for a service")
    p.add_argument("service", help="Service name (used as PBKDF2 salt)")
    p.add_argument("--type", choices=USER_VISIBLE_TYPES, dest="entry_type",
                   help="Output format (default: alnum, or the type stored in services.json)")
    _add_common_args(p)
    p.set_defaults(func=_cmd)


def _cmd(args: argparse.Namespace) -> int:
    seed = resolve_seed(args)
    account = resolve_account(args)
    entry_type = args.entry_type
    match = None
    if entry_type is None:
        services = store.read_services()
        match = next((s for s in services if s.name == args.service), None)
        entry_type = match.type if match else "alnum"

    if entry_type == "vault":
        from dgp.vault import decrypt_vault
        if match is None:
            services = store.read_services()
            match = next((s for s in services if s.name == args.service), None)
        if match is None or not match.encrypted_secret:
            print(f"Error: no vault secret stored for '{args.service}'.", file=sys.stderr)
            return 1
        result = decrypt_vault(match.encrypted_secret, seed, args.service, account)
        if result is None:
            print("Error: failed to decrypt vault secret.", file=sys.stderr)
            return 1
        print(result)
        return 0

    print(engine.generate(seed, args.service, entry_type, account))
    return 0
