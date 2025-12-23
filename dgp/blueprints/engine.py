# -*- coding: utf-8 -*-
"""
DGP Password Generation Engine
Modern implementation using hashlib.pbkdf2_hmac
"""
import hashlib
import base64
import os

def bytes_to_int(bytes_rep):
    """Convert bytes to an integer.

    :param bytes_rep: the raw bytes
    :type bytes_rep: bytes
    :return: the unsigned integer
    :rtype: int
    """
    return int.from_bytes(bytes_rep, byteorder='big')

dec_digit_to_base58 = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"

def get_base58(int_data):
    """Convert integer to base58 string."""
    res = ''
    while int_data > 0:
        mod = int_data % 58
        int_data = int_data // 58
        res = res + dec_digit_to_base58[mod]
    return res

# Because password 'strength' checkers are dicks
def is_alnum(string):
    """Check if string has uppercase, lowercase, and digit."""
    has_lower = False
    has_upper = False
    has_digit = False
    for char in string:
        if char >= '0' and char <= '9':
            has_digit = True
        if char >= 'a' and char <= 'z':
            has_lower = True
        if char >= 'A' and char <= 'Z':
            has_upper = True
    return has_digit and has_lower and has_upper

def grab_alnum(int_data, length):
    """Extract alphanumeric password of given length from integer."""
    raw = get_base58(int_data)
    while True:
        # We assume there is a substring that will pass. Otherwise we explode
        assert len(raw) > length, "Not enough entropy for alphanumeric password"
        res = raw[:length]
        if is_alnum(res):
            return res
        raw = raw[1:]

def gen_large_int(seed, name, secret):
    """Generate large integer using PBKDF2-HMAC-SHA256.

    Uses hashlib.pbkdf2_hmac instead of deprecated werkzeug functions.
    """
    # Combine seed and secret, encode to bytes
    password = (seed + secret).encode('utf-8')
    salt = name.encode('utf-8')

    # Use PBKDF2-HMAC-SHA256 with 8192 iterations (matching legacy)
    bin_data = hashlib.pbkdf2_hmac('sha256', password, salt, iterations=8192, dklen=32)
    int_data = bytes_to_int(bin_data)
    return int_data

def get_wordlist():
    """Load wordlist for XKCD-style passwords."""
    # Get english.txt from root directory (not dgp.blueprints directory)
    from flask import current_app
    filename = os.path.join(os.path.dirname(current_app.root_path), 'english.txt')
    if not os.path.exists(filename):
        # Try alternate location in app root
        filename = os.path.join(current_app.root_path, '..', 'english.txt')
    with open(filename) as f:
        lines = f.readlines()
    return [line.rstrip() for line in lines]

def get_xkcd(int_data):
    """Convert integer to XKCD-style word list."""
    wordlist = get_wordlist()
    res = []
    while int_data > 0:
        mod = int_data % 2048
        int_data = int_data // 2048
        res.append(wordlist[mod])
    return res

def grab_xkcd(int_data, count):
    """Get XKCD-style password with specified word count."""
    words = get_xkcd(int_data)
    return ' '.join(words[:count])

def generate(seed, name, entry_type, secret):
    """Generate password based on seed, name, type, and secret.

    Supported types:
    - hex: 8-character hex password
    - hexlong: 16-character hex password
    - alnum: 8-character alphanumeric (base58)
    - alnumlong: 12-character alphanumeric (base58)
    - xkcd: 4-word passphrase
    - xkcdlong: 8-word passphrase

    Note: SSH key generation removed (deprecated pycrypto dependency)
    """
    if entry_type == 'hex':
        # Generate hex password (8 chars)
        password = (seed + secret).encode('utf-8')
        salt = name.encode('utf-8')
        res = hashlib.pbkdf2_hmac('sha256', password, salt, iterations=8192, dklen=32)
        res = res.hex()[:8]
    elif entry_type == 'hexlong':
        # Generate long hex password (16 chars)
        password = (seed + secret).encode('utf-8')
        salt = name.encode('utf-8')
        res = hashlib.pbkdf2_hmac('sha256', password, salt, iterations=8192, dklen=32)
        res = res.hex()[:16]
    elif entry_type == 'alnum':
        int_data = gen_large_int(seed, name, secret)
        res = grab_alnum(int_data, 8)
    elif entry_type == 'alnumlong':
        int_data = gen_large_int(seed, name, secret)
        res = grab_alnum(int_data, 12)
    elif entry_type == 'xkcd':
        int_data = gen_large_int(seed, name, secret)
        res = grab_xkcd(int_data, 4)
    elif entry_type == 'xkcdlong':
        int_data = gen_large_int(seed, name, secret)
        res = grab_xkcd(int_data, 8)
    elif entry_type == 'ssh':
        res = 'SSH key generation not supported (pycrypto removed)'
    else:
        res = 'unknown type'
    return res
