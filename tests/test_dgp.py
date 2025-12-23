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
def legacy_app(request):
    """App fixture configured for legacy authentication mode tests."""
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
def legacy_client(request, legacy_app):
    """Client fixture for legacy authentication mode tests."""
    client = legacy_app.test_client()

    def teardown():
        os.close(legacy_app.config['DB_FD'])
        os.unlink(legacy_app.config['DATABASE'])
    request.addfinalizer(teardown)

    return client


@pytest.fixture
def app(request):
    """App fixture configured for new user system tests."""
    db_fd, temp_db_location = tempfile.mkstemp()
    config = {
        'DATABASE': temp_db_location,
        'TESTING': True,
        'DB_FD': db_fd
    }

    app = create_app(config=config)

    with app.app_context():
        init_db()
        # Create a test user for new user system tests
        from dgp.auth import create_user
        create_user('testuser', 'testpass123', 'test@example.com')
        yield app


@pytest.fixture
def client(request, app):
    """Client fixture for new user system tests."""
    client = app.test_client()

    def teardown():
        os.close(app.config['DB_FD'])
        os.unlink(app.config['DATABASE'])
    request.addfinalizer(teardown)

    return client


def login(client, username, password):
    return client.post('/login', data=dict(
        username=username,
        password=password
    ), follow_redirects=True)


def logout(client):
    return client.get('/logout', follow_redirects=True)


def test_empty_db(legacy_client, legacy_app):
    """Start with a blank database (legacy mode)."""
    login(legacy_client, legacy_app.config['USERNAME'], legacy_app.config['PASSWORD'])
    rv = legacy_client.get('/')
    assert b'No entries here so far' in rv.data


def test_login_logout(legacy_client, legacy_app):
    """Make sure login and logout works (legacy mode)"""
    rv = login(legacy_client, legacy_app.config['USERNAME'],
               legacy_app.config['PASSWORD'])
    assert b'You were logged in (legacy mode)' in rv.data
    rv = logout(legacy_client)
    assert b'You were logged out' in rv.data
    rv = login(legacy_client, legacy_app.config['USERNAME'] + 'x',
               legacy_app.config['PASSWORD'])
    assert b'Invalid username or password' in rv.data
    rv = login(legacy_client, legacy_app.config['USERNAME'],
               legacy_app.config['PASSWORD'] + 'x')
    assert b'Invalid username or password' in rv.data


def test_messages(legacy_client, legacy_app):
    """Test that messages work (legacy mode)"""
    login(legacy_client, legacy_app.config['USERNAME'],
          legacy_app.config['PASSWORD'])
    rv = legacy_client.post('/add', data=dict(
        name='test-service',
        type='hex',
        note='Test note with <strong>HTML</strong>'
    ), follow_redirects=True)
    assert b'No entries here so far' not in rv.data
    assert b'test-service' in rv.data
    assert b'New entry was successfully added' in rv.data


def test_user_registration(client):
    """Test user registration"""
    rv = client.post('/register', data=dict(
        username='newuser',
        password='password123',
        confirm_password='password123',
        email='new@example.com'
    ), follow_redirects=True)
    assert b'Account created successfully' in rv.data


def test_user_login(client):
    """Test new user system login"""
    rv = login(client, 'testuser', 'testpass123')
    assert b'You were logged in' in rv.data


def test_duplicate_username(client):
    """Test that duplicate usernames are rejected"""
    rv = client.post('/register', data=dict(
        username='testuser',  # Already exists from fixture
        password='password123',
        confirm_password='password123',
        email='other@example.com'
    ), follow_redirects=True)
    assert b'Username already exists' in rv.data
