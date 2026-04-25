from __future__ import annotations
import argparse
import os
import sys
import tempfile

from dgp.cli import _add_common_args, resolve_seed, resolve_account


def register(subparsers) -> None:
    p_ssh = subparsers.add_parser("ssh", help="Derive Ed25519 SSH key pair")
    p_ssh.add_argument("service", help="Service name (used as derivation salt)")
    p_ssh.add_argument("--out", metavar="PATH", help="Write private key to PATH, public to PATH.pub")
    p_ssh.add_argument("--comment", metavar="C", default=None, help="Key comment (default: dgp:<service>)")
    _add_common_args(p_ssh)
    p_ssh.set_defaults(func=_cmd_ssh)

    p_btc = subparsers.add_parser("btc-key", help="Derive secp256k1 Bitcoin key (WIF + bech32)")
    p_btc.add_argument("service", help="Service name")
    _add_common_args(p_btc)
    p_btc.set_defaults(func=_cmd_btc_key)

    p_mnemo = subparsers.add_parser("btc-mnemonic", help="Derive 24-word BIP-39 mnemonic")
    p_mnemo.add_argument("service", help="Service name")
    _add_common_args(p_mnemo)
    p_mnemo.set_defaults(func=_cmd_btc_mnemonic)

    p_prng = subparsers.add_parser("prng", help="ChaCha20 PRNG output")
    p_prng.add_argument("service", help="Service name")
    p_prng.add_argument("--bytes", dest="nbytes", type=int, required=True,
                        help="Number of bytes to generate")
    p_prng.add_argument("--out", metavar="PATH", help="Write output to PATH (mode 0600)")
    _add_common_args(p_prng)
    p_prng.set_defaults(func=_cmd_prng)


def _atomic_write(path: str, data: bytes, mode: int) -> None:
    dir_ = os.path.dirname(os.path.abspath(path))
    with tempfile.NamedTemporaryFile(dir=dir_, delete=False) as tmp:
        tmp.write(data)
        tmp_path = tmp.name
    os.chmod(tmp_path, mode)
    os.replace(tmp_path, path)


def _cmd_ssh(args: argparse.Namespace) -> int:
    from dgp.ssh import derive_ed25519, serialize_openssh_private, serialize_openssh_public
    seed = resolve_seed(args)
    account = resolve_account(args)
    comment = args.comment if args.comment is not None else f"dgp:{args.service}"
    signing_key = derive_ed25519(seed, args.service, account)
    priv_pem = serialize_openssh_private(signing_key, comment)
    pub_str = serialize_openssh_public(signing_key.verify_key, comment)
    if args.out is None:
        sys.stdout.buffer.write(priv_pem)
        sys.stderr.write(pub_str + "\n")
    else:
        _atomic_write(args.out, priv_pem, 0o600)
        _atomic_write(args.out + ".pub", (pub_str + "\n").encode("utf-8"), 0o644)
    return 0


def _cmd_btc_key(args: argparse.Namespace) -> int:
    from dgp.btc import derive_secp256k1_priv, wif_compressed_mainnet, bech32_p2wpkh_mainnet
    from coincurve import PrivateKey
    seed = resolve_seed(args)
    account = resolve_account(args)
    priv = derive_secp256k1_priv(seed, args.service, account)
    pub = PrivateKey(priv).public_key.format(compressed=True)
    print(wif_compressed_mainnet(priv))
    print(bech32_p2wpkh_mainnet(pub))
    return 0


def _cmd_btc_mnemonic(args: argparse.Namespace) -> int:
    from dgp.btc import derive_bip39_mnemonic
    seed = resolve_seed(args)
    account = resolve_account(args)
    words = derive_bip39_mnemonic(seed, args.service, account)
    print(" ".join(words))
    return 0


def _cmd_prng(args: argparse.Namespace) -> int:
    from dgp.prng import chacha20_zeros
    seed = resolve_seed(args)
    account = resolve_account(args)
    if not (1 <= args.nbytes <= 2**32):
        print(f"Error: --bytes must be 1..2**32, got {args.nbytes}", file=sys.stderr)
        return 1
    data = chacha20_zeros(seed, args.service, account, args.nbytes)
    if args.out is None:
        sys.stdout.buffer.write(data)
    else:
        _atomic_write(args.out, data, 0o600)
    return 0
