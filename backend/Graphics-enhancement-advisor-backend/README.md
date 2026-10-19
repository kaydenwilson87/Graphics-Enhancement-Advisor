# Graphics Enhancement Advisor API

Django REST backend for the Flutter Graphics Enhancement Advisor described in the supplied assessment brief. Catalog and research information is deliberately stored as editable records; the project does not invent benchmark findings or seed unsupported recommendations.

## Run locally

Use Python 3.10 or newer (Python 3.12 is used by the Docker image).

```sh
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
cp .env.example .env
python manage.py migrate
python manage.py createsuperuser
python manage.py runserver
```

The default local database is SQLite. Set `DATABASE_URL` to a PostgreSQL URL in deployment, for example `postgresql://user:password@localhost:5432/gea`. Configure a strong `SECRET_KEY`, `ALLOWED_HOSTS`, `DEBUG=False`, and HTTPS before deployment.

## Deploy with Docker

The repository includes a production Docker image for platforms that accept a Dockerfile. Provide these environment variables in the hosting platform's secret/configuration settings:

- `SECRET_KEY`: unique, random Django secret; never commit it.
- `DEBUG=False`
- `ALLOWED_HOSTS`: comma-separated API hostnames, without schemes.
- `DATABASE_URL`: persistent PostgreSQL connection URL. Enable SSL where your database provider requires it.
- `CORS_ALLOWED_ORIGINS`: comma-separated exact client origins when a web client calls the API; leave empty for native-only clients.
- `CSRF_TRUSTED_ORIGINS`: exact HTTPS origins when using Django admin through a proxy.
- `GROQ_API_KEY`: optional; leave unset to keep AI explanations on the deterministic fallback.

Build the image from this directory and deploy it with port `8000` exposed behind the hosting provider's HTTPS proxy. Set the provider's pre-deploy/release command to `python manage.py migrate`; do not run migrations in every web worker at startup. The image runs as an unprivileged user and includes a container health check at `GET /health/`, which checks database connectivity. Create the first administrator with a one-off command such as `python manage.py createsuperuser` using the platform shell. Django rejects a production configuration with the development secret, a short secret, wildcard/empty hosts, SQLite, or no database URL. Do not use SQLite for production: the container filesystem is ephemeral, so use the persistent PostgreSQL database configured in `DATABASE_URL`. `SECURE_SSL_REDIRECT` and HSTS are enabled when `DEBUG=False`; the hosting proxy must set `X-Forwarded-Proto` correctly. The downloaded `google-services.json` is for the Android client's `app/` directory and is not needed by this Django service.

## API

All API routes are under `/api/v1/`.

| Method | Route | Purpose |
| --- | --- | --- |
| GET | `gpus/`, `games/`, `technologies/` | Browse active catalog entries |
| GET | `compatibility/` | Browse compatibility records; filter by `gpu`, `game`, `technology`, `resolution`, `status` |
| GET | `evidence/` | Browse research evidence; filter/search by technology, game, GPU, resolution, preference or source text |
| POST | `recommendations/` | Generate an evidence-based recommendation |
| POST | `recommendations/explain/` | Authenticated, quota-limited explanation of a supported recommendation |
| POST | `auth/register/` | Create a gamer account for per-user AI quotas |
| POST | `auth/token/`, `auth/token/refresh/` | Obtain/refresh JWT tokens |
| CRUD | `/admin/` | Django administration |

Example recommendation body:

```json
{"gpu_id": 1, "game_id": 1, "resolution": "1440p", "preference": "balanced"}
```

`preference` accepts `performance`, `image_quality`, or `balanced`. The response includes the recommendation (or `null` if evidence is insufficient), score-ranked compatible options, supporting evidence, a reason, confidence, limitations, and the scoring method. Score fields in research records use a 1–5 scale; higher latency score means a more favorable latency result. Technologies without a supported matching compatibility record are not recommended. Add actual source-backed GPU, game, compatibility, and evidence records through `/admin/` before relying on results.

Catalog endpoints allow public reads. Write operations require an authenticated staff account (JWT bearer token or Django admin session). Enable TLS in deployment; CORS origins are configured with `CORS_ALLOWED_ORIGINS`.

## Optional AI explanation

The recommendation remains deterministic and evidence-based in Django. An optional Groq API call only rewrites an already-supported recommendation into a short explanation. The model cannot select a different technology, browse the web, or act as a general chat assistant. It receives only the selected GPU/game/resolution/preference and a small excerpt of the stored compatibility and research records. When the API key is missing, the recommendation has insufficient evidence, or the provider is unavailable, the endpoint returns Django's existing explanation without an AI call or gracefully falls back to it.

### Enable the free-tier provider

1. Create a GroqCloud account and API key on its **Free** plan. The account owner must meet Groq's account age requirement. Do not add billing details or upgrade the project if you want to avoid provider charges.
2. Copy `.env.example` to `.env` and set `GROQ_API_KEY` to the secret key. Keep `.env` out of source control and never put the key in Flutter.
3. Keep `GROQ_MODEL=openai/gpt-oss-20b`, or select a currently available model in your Groq console.
4. Run `python manage.py migrate` and restart Django.
5. Register a gamer account at `POST /api/v1/auth/register/` using `{"email":"gamer@example.com","password":"a-long-unique-password"}`. Use the returned email as the username at `POST /api/v1/auth/token/` to get a JWT.
6. Call `POST /api/v1/recommendations/explain/` with the same JSON body as `/recommendations/` and send `Authorization: Bearer <access-token>`.

Default application caps are **3 AI explanations per account per day**, **3 per account per hour**, and **20 total AI calls per day across the app**. Adjust `GROQ_DAILY_LIMIT_PER_USER` and `GROQ_DAILY_LIMIT_GLOBAL` in `.env` if needed. These limits are enforced by Django and consume a quota slot before each provider attempt. The provider also enforces its own Free-plan model and project limits. If either limit is reached, the AI explanation falls back to the deterministic text or returns a rate-limit response. No AI request is made when the app has no supported, scored recommendation.

Free API quotas and available models can change. These application caps limit requests, but provider Free-plan rate limits still apply at the provider account/project level. Keep the account on the Free plan and do not attach billing if your requirement is zero provider spend. Only non-personal gaming context is sent. Groq says inference inputs/outputs are not retained by default, though limited reliability/abuse monitoring may retain them for up to 30 days. Review provider terms and suitability for the intended audience before public deployment.

### Example recommendation explanation request

```http
POST /api/v1/recommendations/explain/
Authorization: Bearer <access-token>
Content-Type: application/json
```

```json
{"gpu_id": 1, "game_id": 1, "resolution": "1440p", "preference": "balanced"}
```

The response keeps `recommendation`, scores, evidence and limitations from the Django engine and adds an `explanation` string plus an `ai` object showing whether the model was used and the remaining daily account quota.
