# -*- coding: utf-8 -*-
"""
    Dgp Tests
    ~~~~~~~~~~~~

    Tests the Dgp application.

    :copyright: (c) 2015 by dgp author.
    :license: BSD, see LICENSE for more details.
"""

import os
import tempfile
import pytest
from dgp.factory import create_app
from dgp.blueprints.dgp import init_db


@pytest.fixture
def app_legacy(request):
    """Test fixture for legacy authentication mode.
    
    Sets up app with USERNAME/PASSWORD config for testing backward compatibility.
    Does not create any users in the new user system.
    """
    db_fd, temp_db_location = tempfile.mkstemp()
    config = {
        'DATABASE': temp_db_location,
        'TESTING': True,
        'DB_FD': db_fd,
        'USERNAME': 'admin',
        'PASSWORD': 'default'
    }

    app = create_app(config=config)

    with app.app_context():
        init_db()
        yield app


@pytest.fixture
def client_legacy(request, app_legacy):
    """Test client for legacy authentication mode."""
    client = app_legacy.test_client()

    def teardown():
        os.close(app_legacy.config['DB_FD'])
        os.unlink(app_legacy.config['DATABASE'])
    request.addfinalizer(teardown)

    return client


@pytest.fixture
def app_userdb(request):
    """Test fixture for new user database system.
    
    Sets up app with test users for testing the new authentication system.
    Does not set USERNAME/PASSWORD config (no legacy mode).
    """
    db_fd, temp_db_location = tempfile.mkstemp()
    config = {
        'DATABASE': temp_db_location,
        'TESTING': True,
        'DB_FD': db_fd
    }

    app = create_app(config=config)

    with app.app_context():
        init_db()
        # Create test users for new system
        from dgp.auth import create_user
        create_user('testuser', 'testpass123', 'test@example.com')
        create_user('admin', 'adminpass456', 'admin@example.com')
        yield app


@pytest.fixture
def client_userdb(request, app_userdb):
    """Test client for new user database system."""
    client = app_userdb.test_client()

    def teardown():
        os.close(app_userdb.config['DB_FD'])
        os.unlink(app_userdb.config['DATABASE'])
    request.addfinalizer(teardown)

    return client


def login(client, username, password):
    return client.post('/login', data=dict(
        username=username,
        password=password
    ), follow_redirects=True)


def logout(client):
    return client.get('/logout', follow_redirects=True)


# ============================================================================
# Legacy Authentication Mode Tests
# ============================================================================
# These tests verify backward compatibility with the legacy authentication
# system using hardcoded USERNAME/PASSWORD config values.

def test_legacy_empty_db(client_legacy, app_legacy):
    """Test empty database with legacy authentication."""
    login(client_legacy, app_legacy.config['USERNAME'], app_legacy.config['PASSWORD'])
    rv = client_legacy.get('/')
    assert b'No entries here so far' in rv.data


def test_legacy_login_logout(client_legacy, app_legacy):
    """Test legacy authentication login and logout."""
    rv = login(client_legacy, app_legacy.config['USERNAME'],
               app_legacy.config['PASSWORD'])
    assert b'You were logged in' in rv.data
    rv = logout(client_legacy)
    assert b'You were logged out' in rv.data
    rv = login(client_legacy, app_legacy.config['USERNAME'] + 'x',
               app_legacy.config['PASSWORD'])
    assert b'Invalid username or password' in rv.data
    rv = login(client_legacy, app_legacy.config['USERNAME'],
               app_legacy.config['PASSWORD'] + 'x')
    assert b'Invalid username or password' in rv.data


def test_legacy_messages(client_legacy, app_legacy):
    """Test adding entries with legacy authentication."""
    login(client_legacy, app_legacy.config['USERNAME'],
          app_legacy.config['PASSWORD'])
    rv = client_legacy.post('/add', data=dict(
        name='test-service',
        type='hex',
        note='Test note with <strong>HTML</strong>'
    ), follow_redirects=True)
    assert b'No entries here so far' not in rv.data
    assert b'test-service' in rv.data
    assert b'New entry was successfully added' in rv.data


# ============================================================================
# New User Database System Tests
# ============================================================================
# These tests verify the new user authentication system with database-backed
# user accounts, password hashing, and registration.


def test_user_registration(client_userdb):
    """Test user registration in new system."""
    rv = client_userdb.post('/register', data=dict(
        username='newuser',
        password='password123',
        confirm_password='password123',
        email='new@example.com'
    ), follow_redirects=True)
    assert b'Account created successfully' in rv.data


def test_user_login(client_userdb):
    """Test login with new user database system."""
    rv = login(client_userdb, 'testuser', 'testpass123')
    assert b'You were logged in' in rv.data


def test_user_logout(client_userdb):
    """Test logout with new user database system."""
    login(client_userdb, 'testuser', 'testpass123')
    rv = logout(client_userdb)
    assert b'You were logged out' in rv.data


def test_user_invalid_credentials(client_userdb):
    """Test invalid login attempts with new user system."""
    rv = login(client_userdb, 'testuser', 'wrongpassword')
    assert b'Invalid username or password' in rv.data
    rv = login(client_userdb, 'nonexistent', 'testpass123')
    assert b'Invalid username or password' in rv.data


def test_duplicate_username(client_userdb):
    """Test that duplicate usernames are rejected."""
    rv = client_userdb.post('/register', data=dict(
        username='testuser',  # Already exists from fixture
        password='password123',
        confirm_password='password123'
    ), follow_redirects=True)
    assert b'Username already exists' in rv.data


def test_user_messages(client_userdb):
    """Test adding entries with new user authentication."""
    login(client_userdb, 'testuser', 'testpass123')
    rv = client_userdb.post('/add', data=dict(
        name='test-service',
        type='hex',
        note='Test note for user system'
    ), follow_redirects=True)
    assert b'No entries here so far' not in rv.data
    assert b'test-service' in rv.data
    assert b'New entry was successfully added' in rv.data
