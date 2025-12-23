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
def app(request):

    db_fd, temp_db_location = tempfile.mkstemp()
    config = {
        'DATABASE': temp_db_location,
        'TESTING': True,
        'DB_FD': db_fd,
        'USERNAME': 'admin',  # Keep for legacy mode tests
        'PASSWORD': 'default'
    }

    app = create_app(config=config)

    with app.app_context():
        init_db()
        # Create a test user
        from dgp.auth import create_user
        create_user('testuser', 'testpass123', 'test@example.com')
        yield app


@pytest.fixture
def client(request, app):

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


def test_empty_db(client, app):
    """Start with a blank database."""
    login(client, app.config['USERNAME'], app.config['PASSWORD'])
    rv = client.get('/')
    assert b'No entries here so far' in rv.data


def test_login_logout(client, app):
    """Make sure login and logout works"""
    rv = login(client, app.config['USERNAME'],
               app.config['PASSWORD'])
    assert b'You were logged in' in rv.data
    rv = logout(client)
    assert b'You were logged out' in rv.data
    rv = login(client,app.config['USERNAME'] + 'x',
               app.config['PASSWORD'])
    assert b'Invalid username or password' in rv.data
    rv = login(client, app.config['USERNAME'],
               app.config['PASSWORD'] + 'x')
    assert b'Invalid username or password' in rv.data


def test_messages(client, app):
    """Test that messages work"""
    login(client, app.config['USERNAME'],
          app.config['PASSWORD'])
    rv = client.post('/add', data=dict(
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
