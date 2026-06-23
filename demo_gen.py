#!/usr/bin/env python3
"""Faithful Python-3 port of the dgp-mobile / HashDroid derivation, used by demo.sh.

simple.py is the project's original reference, but it is Python 2 (print
statements, long(), werkzeug.security.pbkdf2_*), so it will not run on a modern
interpreter. The Android app (UtilServices.generate_password) and simple.py both
compute the SAME function:

    dk = PBKDF2-HMAC-SHA1(password = seed + account,
                          salt     = name,
                          iters    = 42000,
                          dklen    = 40 bytes)         # == 320 bits in the app

then encode dk as base58 (alnum is a sliding-window substring that has at least
one lower, one upper, one digit). This file reproduces that exactly with the
standard library, so the numbers printed are the project's real output -- in
particular the a/""/aa vectors hardcoded in simple.py lines 124-126.
"""
import hashlib

_DIGITS = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"


def base58(n):
    out = ""
    while n > 0:
        n, m = divmod(n, 58)
        out += _DIGITS[m]
    return out


def _is_alnum(s):
    return (any(c.isdigit() for c in s)
            and any(c.islower() for c in s)
            and any(c.isupper() for c in s))


def grab_alnum(n, length):
    raw = base58(n)
    while len(raw) > length:
        if _is_alnum(raw[:length]):
            return raw[:length]
        raw = raw[1:]
    return "FailedGrabAlnum"


def derive(seed, account, name):
    dk = hashlib.pbkdf2_hmac("sha1", (seed + account).encode(),
                             name.encode(), 42000, dklen=40)
    n = int.from_bytes(dk, "big")
    return {
        "hexlong":    dk.hex()[:16],
        "alnum":      grab_alnum(n, 8),
        "alnumlong":  grab_alnum(n, 12),
        "base58":     base58(n)[:8],
        "base58long": base58(n)[:12],
    }


if __name__ == "__main__":
    # seed="a", account="", name="aa": the project's own published test vectors.
    seed, account, name = "a", "", "aa"
    print("seed=%r  account=%r  name=%r" % (seed, account, name))
    for fmt, val in derive(seed, account, name).items():
        print("  %-11s %s" % (fmt + ":", val))
    print("  (simple.py asserts a:aa:alnum, a:aa:base58, a:aa:alnumlong)")
