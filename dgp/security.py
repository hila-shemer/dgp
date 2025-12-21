# -*- coding: utf-8 -*-
"""
Security middleware and utilities for DGP
"""
from flask import request, redirect, current_app
from functools import wraps


class SecurityHeaders:
    """Middleware to add security headers to all responses."""

    def __init__(self, app):
        self.app = app

    def __call__(self, environ, start_response):
        def custom_start_response(status, headers, exc_info=None):
            # Add security headers
            security_headers = [
                # Prevent clickjacking
                ('X-Frame-Options', 'DENY'),
                # Prevent MIME type sniffing
                ('X-Content-Type-Options', 'nosniff'),
                # Enable XSS protection
                ('X-XSS-Protection', '1; mode=block'),
                # Referrer policy
                ('Referrer-Policy', 'strict-origin-when-cross-origin'),
                # Content Security Policy
                ('Content-Security-Policy',
                 "default-src 'self'; "
                 "script-src 'self' 'unsafe-inline'; "
                 "style-src 'self' 'unsafe-inline'; "
                 "img-src 'self' data:; "
                 "font-src 'self'; "
                 "connect-src 'self'; "
                 "frame-ancestors 'none';"),
            ]

            # Add HSTS header if HTTPS is enabled
            if environ.get('wsgi.url_scheme') == 'https' or \
               environ.get('HTTP_X_FORWARDED_PROTO') == 'https':
                security_headers.append(
                    ('Strict-Transport-Security', 'max-age=31536000; includeSubDomains')
                )

            # Merge with existing headers
            headers.extend(security_headers)
            return start_response(status, headers, exc_info)

        return self.app(environ, custom_start_response)


class HTTPSRedirect:
    """Middleware to redirect HTTP to HTTPS."""

    def __init__(self, app, enabled=True):
        self.app = app
        self.enabled = enabled

    def __call__(self, environ, start_response):
        # Check if HTTPS enforcement is enabled
        if not self.enabled:
            return self.app(environ, start_response)

        # Check if request is already HTTPS
        scheme = environ.get('wsgi.url_scheme', 'http')
        forwarded_proto = environ.get('HTTP_X_FORWARDED_PROTO', '')

        if scheme == 'https' or forwarded_proto == 'https':
            # Already HTTPS, proceed normally
            return self.app(environ, start_response)

        # Redirect to HTTPS
        host = environ.get('HTTP_HOST', environ.get('SERVER_NAME', 'localhost'))
        path = environ.get('PATH_INFO', '/')
        query = environ.get('QUERY_STRING', '')

        https_url = f'https://{host}{path}'
        if query:
            https_url += f'?{query}'

        # Return 301 redirect
        status = '301 Moved Permanently'
        headers = [
            ('Location', https_url),
            ('Content-Type', 'text/html'),
        ]

        start_response(status, headers)
        return [b'<h1>Redirecting to HTTPS...</h1>']


def require_https(f):
    """Decorator to require HTTPS for a specific route."""
    @wraps(f)
    def decorated_function(*args, **kwargs):
        if not request.is_secure and not request.headers.get('X-Forwarded-Proto') == 'https':
            # In development, you might want to allow HTTP
            # Remove this check in production
            if not current_app.debug:
                return redirect(request.url.replace('http://', 'https://'), code=301)
        return f(*args, **kwargs)
    return decorated_function


def get_client_ip():
    """Get the real client IP address, accounting for proxies."""
    if request.headers.get('X-Forwarded-For'):
        # Get the first IP in the X-Forwarded-For chain
        return request.headers.get('X-Forwarded-For').split(',')[0].strip()
    elif request.headers.get('X-Real-IP'):
        return request.headers.get('X-Real-IP')
    else:
        return request.remote_addr
