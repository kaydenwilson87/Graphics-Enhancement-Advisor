#!/bin/sh
# Startup script for the hosted server. It runs every time the service starts, so it must be safe to repeat.
set -e

echo "Applying database migrations..."
python manage.py migrate --noinput

# Create the first admin account (only if these variables are set). If it already exists, carry on.
# DJANGO_SUPERUSER_USERNAME must be the admin's email in lowercase, because the app signs in with the email.
if [ -n "$DJANGO_SUPERUSER_USERNAME" ] && [ -n "$DJANGO_SUPERUSER_PASSWORD" ]; then
  python manage.py createsuperuser --noinput || echo "Admin account already exists, skipping."
fi

# Load the demo data only while the database has no GPUs, so later edits are never overwritten.
if python manage.py shell -c "import sys; from advisor.models import GPU; sys.exit(0 if GPU.objects.exists() else 1)"; then
  echo "GPUs already in the database, skipping demo data."
else
  python manage.py loaddata demo_data
fi

# Render tells the service which port to use through PORT.
exec gunicorn config.wsgi:application --bind "0.0.0.0:${PORT:-8000}" --workers 2 --timeout 30 --graceful-timeout 30 --access-logfile -
