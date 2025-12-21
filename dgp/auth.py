# -*- coding: utf-8 -*-
"""
User authentication and management for DGP
"""
import hashlib
import os
from datetime import datetime
from flask import current_app, g


def hash_password(password):
    """Hash a password using PBKDF2-HMAC-SHA256.

    Uses a random salt and strong iteration count for security.
    Format: algorithm$iterations$salt$hash
    """
    # Generate a random salt
    salt = os.urandom(32)

    # Use PBKDF2-HMAC-SHA256 with 260000 iterations (OWASP recommendation 2023)
    iterations = 260000
    pwd_hash = hashlib.pbkdf2_hmac('sha256', password.encode('utf-8'), salt, iterations)

    # Store as: algorithm$iterations$salt$hash (all hex-encoded)
    stored = f"pbkdf2_sha256${iterations}${salt.hex()}${pwd_hash.hex()}"
    return stored


def verify_password(password, stored_hash):
    """Verify a password against a stored hash.

    Args:
        password: The plaintext password to verify
        stored_hash: The stored hash in format algorithm$iterations$salt$hash

    Returns:
        bool: True if password matches, False otherwise
    """
    try:
        algorithm, iterations_str, salt_hex, hash_hex = stored_hash.split('$')

        if algorithm != 'pbkdf2_sha256':
            return False

        iterations = int(iterations_str)
        salt = bytes.fromhex(salt_hex)
        stored_pwd_hash = bytes.fromhex(hash_hex)

        # Hash the provided password with the same salt and iterations
        pwd_hash = hashlib.pbkdf2_hmac('sha256', password.encode('utf-8'), salt, iterations)

        # Constant-time comparison to prevent timing attacks
        return pwd_hash == stored_pwd_hash
    except (ValueError, AttributeError):
        return False


def create_user(username, password, email=None):
    """Create a new user with hashed password.

    Args:
        username: Unique username
        password: Plaintext password (will be hashed)
        email: Optional email address

    Returns:
        int: User ID if successful, None if username exists
    """
    from dgp.blueprints.dgp import get_db

    db = get_db()

    # Check if username already exists
    existing = db.execute('SELECT id FROM users WHERE username = ?', [username]).fetchone()
    if existing:
        return None

    # Hash the password
    password_hash = hash_password(password)

    # Create the user
    cursor = db.execute(
        'INSERT INTO users (username, email, password_hash) VALUES (?, ?, ?)',
        [username, email, password_hash]
    )
    db.commit()

    return cursor.lastrowid


def authenticate_user(username, password):
    """Authenticate a user by username and password.

    Args:
        username: The username
        password: The plaintext password

    Returns:
        dict: User info if successful (id, username, email), None otherwise
    """
    from dgp.blueprints.dgp import get_db

    db = get_db()

    # Get user from database
    user = db.execute(
        'SELECT id, username, email, password_hash FROM users WHERE username = ?',
        [username]
    ).fetchone()

    if not user:
        return None

    # Verify password
    if not verify_password(password, user['password_hash']):
        return None

    # Update last login time
    db.execute(
        'UPDATE users SET last_login = ? WHERE id = ?',
        [datetime.now(), user['id']]
    )
    db.commit()

    # Return user info (without password hash)
    return {
        'id': user['id'],
        'username': user['username'],
        'email': user['email']
    }


def get_user_by_id(user_id):
    """Get user information by ID.

    Args:
        user_id: The user ID

    Returns:
        dict: User info (id, username, email) or None
    """
    from dgp.blueprints.dgp import get_db

    db = get_db()
    user = db.execute(
        'SELECT id, username, email FROM users WHERE id = ?',
        [user_id]
    ).fetchone()

    if not user:
        return None

    return {
        'id': user['id'],
        'username': user['username'],
        'email': user['email']
    }


def get_current_user():
    """Get the currently logged-in user from session.

    Returns:
        dict: User info or None if not logged in
    """
    from flask import session

    user_id = session.get('user_id')
    if not user_id:
        return None

    return get_user_by_id(user_id)


def change_password(user_id, old_password, new_password):
    """Change a user's password.

    Args:
        user_id: The user ID
        old_password: Current password (for verification)
        new_password: New password

    Returns:
        bool: True if successful, False if old password incorrect
    """
    from dgp.blueprints.dgp import get_db

    db = get_db()

    # Get current password hash
    user = db.execute(
        'SELECT password_hash FROM users WHERE id = ?',
        [user_id]
    ).fetchone()

    if not user:
        return False

    # Verify old password
    if not verify_password(old_password, user['password_hash']):
        return False

    # Hash new password
    new_hash = hash_password(new_password)

    # Update password
    db.execute(
        'UPDATE users SET password_hash = ? WHERE id = ?',
        [new_hash, user_id]
    )
    db.commit()

    return True
