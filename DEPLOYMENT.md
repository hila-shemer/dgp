# DGP Deployment Guide

## Security Configuration

### HTTPS Enforcement

DGP includes built-in HTTPS enforcement and security headers. To enable in production:

#### Environment Variables

```bash
# Enable HTTPS redirect (redirects all HTTP traffic to HTTPS)
export HTTPS_REDIRECT=true

# Enable secure session cookies (requires HTTPS)
export SESSION_COOKIE_SECURE=true
```

#### Using a Configuration File

Create a file (e.g., `production_config.py`):

```python
HTTPS_REDIRECT = True
SESSION_COOKIE_SECURE = True
SESSION_COOKIE_HTTPONLY = True
SESSION_COOKIE_SAMESITE = 'Lax'
DEBUG = False
SECRET_KEY = 'your-random-secret-key-here'  # CHANGE THIS!
```

Then set the environment variable:

```bash
export DGP_SETTINGS=/path/to/production_config.py
```

### Security Headers

The following security headers are automatically added to all responses:

- **X-Frame-Options**: DENY (prevents clickjacking)
- **X-Content-Type-Options**: nosniff (prevents MIME sniffing)
- **X-XSS-Protection**: 1; mode=block (enables XSS filter)
- **Referrer-Policy**: strict-origin-when-cross-origin
- **Content-Security-Policy**: Restricts resource loading
- **Strict-Transport-Security** (HSTS): max-age=31536000 (only when HTTPS is detected)

### Nginx Configuration Example

For production deployment behind Nginx:

```nginx
server {
    listen 80;
    server_name your-domain.com;

    # Redirect HTTP to HTTPS
    return 301 https://$server_name$request_uri;
}

server {
    listen 443 ssl http2;
    server_name your-domain.com;

    # SSL certificates
    ssl_certificate /path/to/cert.pem;
    ssl_certificate_key /path/to/key.pem;

    # SSL configuration
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_ciphers HIGH:!aNULL:!MD5;
    ssl_prefer_server_ciphers on;

    location / {
        proxy_pass http://127.0.0.1:5000;
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
```

### Production Checklist

- [ ] Set `DEBUG = False` in production
- [ ] Generate a strong random `SECRET_KEY`
- [ ] Enable `HTTPS_REDIRECT=true`
- [ ] Enable `SESSION_COOKIE_SECURE=true`
- [ ] Use SSL/TLS certificates (Let's Encrypt recommended)
- [ ] Configure Nginx or another reverse proxy
- [ ] Use Gunicorn or uWSGI instead of Flask dev server
- [ ] Set up regular database backups
- [ ] Secure the seed file with appropriate permissions (chmod 600)
- [ ] Run DGP as a non-root user
- [ ] Enable firewall (allow only ports 80, 443, and SSH)

### Generating a Secret Key

```bash
python -c 'import os; print(os.urandom(24).hex())'
```

### Running with Gunicorn

```bash
pip install gunicorn

# Run with 4 workers
gunicorn -w 4 -b 127.0.0.1:5000 'dgp.factory:create_app()'
```

### Systemd Service Example

Create `/etc/systemd/system/dgp.service`:

```ini
[Unit]
Description=DGP Password Manager
After=network.target

[Service]
User=dgp
Group=dgp
WorkingDirectory=/opt/dgp
Environment="HTTPS_REDIRECT=true"
Environment="SESSION_COOKIE_SECURE=true"
Environment="DGP_SETTINGS=/opt/dgp/production_config.py"
ExecStart=/opt/dgp/venv/bin/gunicorn -w 4 -b 127.0.0.1:5000 'dgp.factory:create_app()'
Restart=always

[Install]
WantedBy=multi-user.target
```

Enable and start:

```bash
sudo systemctl enable dgp
sudo systemctl start dgp
sudo systemctl status dgp
```

## Development Mode

For local development without HTTPS:

```bash
export HTTPS_REDIRECT=false
export SESSION_COOKIE_SECURE=false
export FLASK_ENV=development

flask --app dgp.factory run
```

## Testing HTTPS Locally

Use a tool like `mkcert` to generate local SSL certificates:

```bash
# Install mkcert
brew install mkcert  # macOS
# or
apt install mkcert   # Linux

# Generate certificates
mkcert -install
mkcert localhost 127.0.0.1

# Run with SSL
gunicorn -w 1 -b 127.0.0.1:5000 \
  --certfile=localhost+1.pem \
  --keyfile=localhost+1-key.pem \
  'dgp.factory:create_app()'
```

Then set:
```bash
export HTTPS_REDIRECT=true
export SESSION_COOKIE_SECURE=true
```
