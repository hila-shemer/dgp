from __future__ import annotations
import argparse
import difflib
from dgp import testvectors


def register(subparsers: argparse._SubParsersAction) -> None:
    p = subparsers.add_parser("test-vectors", help="Run built-in test vectors")
    p.add_argument("--all", action="store_true", dest="run_all",
                   help="Continue past first failure instead of stopping")
    p.set_defaults(func=_cmd)


def _cmd(args: argparse.Namespace) -> int:
    results = testvectors.run_all()
    failures = [r for r in results if not r.passed]
    passed = len(results) - len(failures)
    if not failures:
        print(f"All {passed} vectors passed.")
        return 0
    to_show = failures if args.run_all else failures[:1]
    for r in to_show:
        print(f"FAIL: {r.label}")
        diff = list(difflib.unified_diff(
            [r.expected], [r.actual],
            fromfile="expected", tofile="actual", lineterm=""))
        if diff:
            print("\n".join(diff))
        else:
            print(f"  expected: {r.expected!r}")
            print(f"  actual:   {r.actual!r}")
    if not args.run_all and len(failures) > 1:
        print(f"  ... {len(failures) - 1} more failure(s). Use --all to see all.")
    print(f"{passed}/{len(results)} passed.")
    return 1
