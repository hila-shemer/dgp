# -*- coding: utf-8 -*-
"""
    Dgp
    ~~~~~~

    Stuffs

    :copyright: (c) 2017 by dgp author.
    :license: BSD, see LICENSE for more details.
"""

from sqlite3 import dbapi2 as sqlite3
from flask import Blueprint, request, session, g, redirect, url_for, abort, \
     render_template, flash, current_app
import base64
import os
from . import engine
from dgp.auth import authenticate_user, create_user, get_current_user


# create our blueprint :)
bp = Blueprint('dgp', __name__)


def connect_db():
    """Connects to the specific database."""
    rv = sqlite3.connect(current_app.config['DATABASE'])
    rv.row_factory = sqlite3.Row
    return rv


def init_db():
    """Initializes the database."""
    db = get_db()
    with current_app.open_resource('schema.sql', mode='r') as f:
        db.cursor().executescript(f.read())
    db.commit()


def get_db():
    """Opens a new database connection if there is none yet for the
    current application context.
    """
    if not hasattr(g, 'sqlite_db'):
        g.sqlite_db = connect_db()
    return g.sqlite_db

def query_db(query, args=(), one=False):
    """Queries the database and returns a list of dictionaries."""
    cur = get_db().execute(query, args)
    rv = cur.fetchall()
    return (rv[0] if rv else None) if one else rv


def get_entry(name):
    """Convenience method to look up an entry by name."""
    rv = query_db('select type from entries where name = ?',
                  [name], one=True)
    return rv[0] if rv else None

def get_note(name):
    """Get an entry's note by name."""
    rv = query_db('select note from entries where name = ?',
                  [name], one=True)
    return rv[0] if rv else None

@bp.route('/')
def show_entries():
    if not session.get('logged_in'):
        return redirect(url_for('dgp.login'))

    db = get_db()
    user_id = session.get('user_id')

    # Get entries for current user only
    if user_id:
        cur = db.execute(
            'select name, type from entries where user_id = ? order by id desc',
            [user_id]
        )
    else:
        # Fallback for old-style login (no user_id in session)
        cur = db.execute('select name, type from entries order by id desc')

    entries = cur.fetchall()
    # Get password from session if it exists (from generate)
    generated_password = session.pop('generated_password', None)
    service_name = session.pop('service_name', None)
    return render_template('show_entries.html',
                         entries=entries,
                         generated_password=generated_password,
                         service_name=service_name)


@bp.route('/add', methods=['POST'])
def add_entry():
    if not session.get('logged_in'):
        abort(401)
    db = get_db()
    user_id = session.get('user_id')

    # For legacy mode (no user_id), create or use default user
    if user_id is None:
        # Check if default user exists
        default_user = db.execute('SELECT id FROM users WHERE username = ?', ['admin']).fetchone()
        if default_user:
            user_id = default_user['id']
        else:
            # Create default user
            user_id = create_user('admin', current_app.config.get('PASSWORD', 'default'))
            if user_id is None:
                user_id = 1  # Absolute fallback

    old_entry = get_entry(request.form['name'])
    if old_entry:
        flash('Duplicate entry exists')
        return redirect(url_for('dgp.show_entries'))
    if request.form['type'] == "other":
        entry_type = request.form['other']
    else:
        entry_type = request.form['type']

    db.execute('insert into entries (user_id, name, type, note) values (?, ?, ?, ?)',
               [user_id, request.form['name'], entry_type, request.form['note']])
    db.commit()
    flash('New entry was successfully added')
    return redirect(url_for('dgp.show_entries'))

# TODO
def get_seed():
    filename = os.path.join(current_app.root_path, 'seed')
    with open(filename) as f:
        seed = f.read()
    return seed

def generate(name, entry_type, secret):
    res = engine.generate(get_seed(), name, entry_type, secret)
    # Store password in session instead of flashing
    session['generated_password'] = res
    session['service_name'] = name

@bp.route('/gen', methods=['POST'])
def gen_entry():
    if not session.get('logged_in'):
        abort(401)
    if not request.form.get('name'):
        flash('Nothing chosen')
        return redirect(url_for('dgp.show_entries'))
    entry = get_entry(request.form['name'])
    if not entry:
        flash('Error! Entry does not exist')
        return redirect(url_for('dgp.show_entries'))
    note = get_note(request.form['name'])
    if note and note != '':
        flash(note)
    generate(request.form['name'], entry, request.form['secret'])
    return redirect(url_for('dgp.show_entries'))

@bp.route('/custom', methods=['POST'])
def gen_custom():
    if not session.get('logged_in'):
        abort(401)
    generate(request.form['name'], request.form['type'], request.form['secret'])
    return redirect(url_for('dgp.show_entries'))


@bp.route('/login', methods=['GET', 'POST'])
def login():
    error = None
    if request.method == 'POST':
        username = request.form.get('username', '')
        password = request.form.get('password', '')

        # Try new authentication system first
        user = authenticate_user(username, password)

        if user:
            # New system: store user_id in session
            session['logged_in'] = True
            session['user_id'] = user['id']
            session['username'] = user['username']
            flash('You were logged in')
            return redirect(url_for('dgp.show_entries'))
        else:
            # Fallback to old hardcoded credentials for backward compatibility
            if username == current_app.config.get('USERNAME') and \
               password == current_app.config.get('PASSWORD'):
                session['logged_in'] = True
                session['user_id'] = None  # No user_id for legacy login
                flash('You were logged in (legacy mode)')
                return redirect(url_for('dgp.show_entries'))
            else:
                error = 'Invalid username or password'

    return render_template('login.html', error=error)


@bp.route('/register', methods=['GET', 'POST'])
def register():
    error = None
    if request.method == 'POST':
        username = request.form.get('username', '').strip()
        password = request.form.get('password', '')
        confirm_password = request.form.get('confirm_password', '')
        email = request.form.get('email', '').strip() or None

        # Validation
        if not username:
            error = 'Username is required'
        elif len(username) < 3:
            error = 'Username must be at least 3 characters'
        elif not password:
            error = 'Password is required'
        elif len(password) < 8:
            error = 'Password must be at least 8 characters'
        elif password != confirm_password:
            error = 'Passwords do not match'
        else:
            # Try to create user
            user_id = create_user(username, password, email)
            if user_id:
                flash('Account created successfully! Please log in.')
                return redirect(url_for('dgp.login'))
            else:
                error = 'Username already exists'

    return render_template('register.html', error=error)


@bp.route('/logout')
def logout():
    session.pop('logged_in', None)
    flash('You were logged out')
    return redirect(url_for('dgp.show_entries'))
