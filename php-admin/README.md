# Graphics Advisor PHP admin portal

A small server-rendered PHP site for managing GPUs, games, compatibility, and research evidence through the Django API. It uses the existing Django accounts and API; it does not have a separate admin password store or connect directly to the database.

## Requirements

- PHP 8.1+ with the `curl` extension
- The Django API running and reachable from the PHP server
- A Django account with `is_staff=True`
- HTTPS for any shared or production deployment

## Run locally

From the repository root, set `GEA_API_BASE_URL` to the Django API base and start PHP's development server:

```sh
export GEA_API_BASE_URL=https://graphics-enhancement-advisor-1.onrender.com/api/v1
php -S 127.0.0.1:8080 -t php-admin
```

Open <http://127.0.0.1:8080>. Create a Django staff account with `python manage.py createsuperuser`, or promote a trusted account in Django admin. Do not expose PHP's built-in development server to a public network.

## Authorization and security

The portal obtains a Django JWT at `/auth/token/`, then verifies `is_staff` through `/auth/me/` before granting access. It keeps the access token in a PHP server-side session; the browser never receives the token. Catalog and research-data writes are sent to the API, where Django independently enforces staff-only writes. The portal also uses CSRF tokens on all forms, regenerates the session ID at sign-in, escapes displayed catalog content, and verifies TLS certificates for HTTPS API URLs.

For recommendations, add a verified compatibility record matching the exact GPU, game, and resolution, with status `supported`. Add active research evidence for the same technology with at least one score from 1 to 5. Do not guess compatibility or scores: include a source and its limitations. API keys enable the optional explanation provider; they do not supply compatibility or benchmark data.

Set `GEA_API_BASE_URL` in the PHP server environment when overriding the default deployed API URL. Keep the portal behind HTTPS in deployment. For reliable session cookies, serve it over HTTPS. Catalog deletion is permanent and may cascade to associated compatibility and research-evidence rows; prefer hiding an entry when it should no longer appear in the mobile app.

## Production configuration

The Netlify URL is the public frontend only. Django is hosted at `https://graphics-enhancement-advisor-1.onrender.com`; the Android project and PHP portal default to its API at `/api/v1`. The deployed PHP admin portal is `https://graphics-enhancement-advisor-3.onrender.com`. Its Render service uses `GEA_API_BASE_URL=https://graphics-enhancement-advisor-1.onrender.com/api/v1` and health check path `/health.php`. The supplied Dockerfile serves the portal over Apache/PHP with cURL. Netlify's static hosting does not execute this PHP file.
