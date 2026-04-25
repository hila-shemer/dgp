from __future__ import annotations
import argparse
import sys
from pathlib import Path
from dgp import store

USER_VISIBLE_TYPES = ["alnum", "alnumlong", "base58", "base58long",
                      "hex", "hexlong", "xkcd", "xkcdlong"]


def resolve_seed(args: argparse.Namespace) -> str:
    if getattr(args, "seed", None) is not None:
        return args.seed
    if getattr(args, "seed_file", None) is not None:
        return Path(args.seed_file).read_text("utf-8").rstrip()
    try:
        return store.read_text_file(store.seed_path()).rstrip()
    except FileNotFoundError:
        print("Error: no seed configured. Use --seed or --seed-file.", file=sys.stderr)
        sys.exit(2)


def resolve_account(args: argparse.Namespace) -> str:
    if getattr(args, "account", None) is not None:
        return args.account
    if getattr(args, "account_file", None) is not None:
        return Path(args.account_file).read_text("utf-8").rstrip()
    try:
        return store.read_text_file(store.account_path()).rstrip()
    except FileNotFoundError:
        return ""


def _add_common_args(parser: argparse.ArgumentParser) -> None:
    parser.add_argument("--seed",
                        help="Seed (TESTING ONLY — exposed in /proc/<pid>/cmdline)")
    parser.add_argument("--seed-file", metavar="PATH", help="Read seed from file")
    parser.add_argument("--account", help="Account string (TESTING ONLY)")
    parser.add_argument("--account-file", metavar="PATH", help="Read account from file")


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        prog="dgp", description="Deterministically Generated Passwords"
    )
    _add_common_args(parser)
    subparsers = parser.add_subparsers(dest="cmd", required=True)

    from dgp.cli import gen as gen_mod
    from dgp.cli import vectors as vec_mod
    from dgp.cli import config as config_mod
    from dgp.cli import extras as extras_mod
    gen_mod.register(subparsers)
    vec_mod.register(subparsers)
    config_mod.register(subparsers)
    extras_mod.register(subparsers)

    args = parser.parse_args(argv)
    return args.func(args)
