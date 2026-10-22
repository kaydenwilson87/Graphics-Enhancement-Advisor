# Deployment URLs

The Netlify deployment is the public frontend:

```text
https://graphic-enhancement-advicer.netlify.app
```

It does not run Django or PHP by itself. Keep three service URLs distinct:

| Service | Host | Configuration |
|---|---|---|
| Frontend | Netlify | `https://graphic-enhancement-advicer.netlify.app` |
| REST API | Django hosting service | `https://<your-django-host>/api/v1/` |
| Admin portal | PHP-capable HTTPS host | Set `GEA_API_BASE_URL` to `https://<your-django-host>/api/v1` |

## Django host environment

Set these variables in the Django host's private environment settings. Use the actual Django hostname (hostname only, no scheme) for `ALLOWED_HOSTS`, and a managed PostgreSQL database for `DATABASE_URL`.

```dotenv
DEBUG=False
SECRET_KEY=<unique secret with at least 50 characters>
ALLOWED_HOSTS=<your-django-host>
DATABASE_URL=<managed PostgreSQL connection URL>
CORS_ALLOWED_ORIGINS=https://graphic-enhancement-advicer.netlify.app
CSRF_TRUSTED_ORIGINS=https://graphic-enhancement-advicer.netlify.app
```

Keep `GROQ_API_KEY` private in the Django host. Never put it in Android, Netlify client variables, PHP files, or source control. Run `python manage.py migrate` after deploying new backend code and create a staff account before using the PHP portal.

## Android API URL

The Android app needs the Django URL, not the Netlify URL. From the repository root, set it at build time:

```sh
./gradlew assembleRelease -PapiBaseUrl=https://<your-django-host>/api/v1/
```

For local Android emulator development, the default remains `http://10.0.2.2:8000/api/v1/`.

## PHP admin portal

Deploy `php-admin/` to a host that runs PHP 8.1+ with the cURL extension and supports HTTPS. Configure this server-side environment variable on that host:

```dotenv
GEA_API_BASE_URL=https://<your-django-host>/api/v1
```

Do not use the Netlify URL here. Do not expose PHP's built-in development server to the internet.

## Smoke checks

After configuring the Django URL, verify:

```text
https://<your-django-host>/health/
https://<your-django-host>/api/v1/gpus/
```

The first should return `{"status":"ok","database":"ok"}`. The second should return the public GPU catalog. Then test Android registration/login and PHP staff login. If a browser frontend calls Django directly, its exact Netlify origin must be listed in `CORS_ALLOWED_ORIGINS`.
