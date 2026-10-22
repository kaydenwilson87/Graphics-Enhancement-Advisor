# Graphics Enhancement Advisor API

Django REST backend for the Android (Java) Graphics Enhancement Advisor described in the supplied assessment brief. Catalog and research information is deliberately stored as editable records; the project does not invent benchmark findings or seed unsupported recommendations.

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

The default local database is SQLite (`db.sqlite3`). In deployment, set `DATABASE_URL` to the connection URL for a persistent, managed PostgreSQL database; the application then uses PostgreSQL as its shared database. These databases are alternatives for each running backend, not a live SQLite-to-PostgreSQL replication pair: local SQLite records are not automatically copied to production. Configure a strong `SECRET_KEY`, `ALLOWED_HOSTS`, `DEBUG=False`, and HTTPS before deployment.

Saved comparisons are already associated with the authenticated account and stored through the backend API. When the Android app is connected and signed in, its saved-comparison operations use that API, so those records are shared through the deployment's PostgreSQL database. The app does not currently maintain an offline SQLite copy of those records or automatically merge pre-existing device-local records; SQLite here is the backend's local-development database.

### Optional demo fixture

For a disposable demo database, load `advisor/fixtures/demo_data.json` with `python manage.py loaddata demo_data`. It contains three clearly named synthetic GPUs, three synthetic games, 81 compatibility rows marked `unknown`, and three inactive `DEMO PLACEHOLDER` evidence rows with no scores. These records support UI and API demonstrations only; they do not assert actual compatibility or benchmark results. Replace them with verified, cited data before submission. Do not rename placeholder evidence as a real source or infer compatibility from these rows.

## Deploy with Docker

The repository includes a production Docker image for platforms that accept a Dockerfile. Provide these environment variables in the hosting platform's secret/configuration settings:

- `SECRET_KEY`: unique, random Django secret; never commit it.
- `DEBUG=False`
- `ALLOWED_HOSTS`: comma-separated API hostnames, without schemes.
- `DATABASE_URL`: persistent PostgreSQL connection URL. Enable SSL where your database provider requires it.
- `CORS_ALLOWED_ORIGINS`: comma-separated exact client origins when a web client calls the API; leave empty for native-only clients.
- `CSRF_TRUSTED_ORIGINS`: exact HTTPS origins when using Django admin through a proxy.
- `GROQ_API_KEY`: optional; leave unset to keep AI explanations on the deterministic fallback.

Create a PostgreSQL database with your hosting provider, then add its connection URL as the deployment's `DATABASE_URL` secret/configuration value (never commit production credentials). Build the image from this directory and deploy it with port `8000` exposed behind the hosting provider's HTTPS proxy. Set the provider's pre-deploy/release command to `python manage.py migrate`; do not run migrations in every web worker at startup. The image runs as an unprivileged user and includes a container health check at `GET /health/`, which checks database connectivity. Create the first administrator with a one-off command such as `python manage.py createsuperuser` using the platform shell. Django rejects a production configuration with the development secret, a short secret, wildcard/empty hosts, SQLite, or no database URL. Do not use SQLite for production: the container filesystem is ephemeral, so use the persistent PostgreSQL database configured in `DATABASE_URL`. `SECURE_SSL_REDIRECT` and HSTS are enabled when `DEBUG=False`; the hosting proxy must set `X-Forwarded-Proto` correctly. The Django service is used by the native Android client in the repository root.

## API

All API routes are under `/api/v1/`.

| Method | Route | Purpose |
| --- | --- | --- |
| GET | `gpus/`, `games/`, `technologies/` | Browse active catalog entries |
| GET | `compatibility/` | Browse compatibility records; filter by `gpu`, `game`, `technology`, `resolution`, `status` |
| GET | `evidence/` | Browse research evidence; filter/search by technology, game, GPU, resolution, preference or source text |
| POST | `recommendations/` | Refresh game-upscaler compatibility from PCGamingWiki when available, then generate an evidence-based recommendation |
| POST | `recommendations/explain/` | Authenticated, quota-limited explanation of a supported recommendation |
| POST | `auth/register/` | Create a gamer account for per-user AI quotas |
| POST | `auth/token/`, `auth/token/refresh/` | Obtain/refresh JWT tokens |
| CRUD | `/admin/` | Django administration |
| CRUD | `saved-comparisons/` | Authenticated per-user comparison history (JWT required) |

Example recommendation body:

```json
{"gpu_id": 1, "game_id": 1, "resolution": "1440p", "preference": "balanced"}
```

`preference` accepts `performance`, `image_quality`, or `balanced`. The response includes the recommendation (or `null` if evidence is insufficient), score-ranked compatible options, supporting evidence, a reason, confidence, limitations, and the scoring method. Score fields in research records use a 1–5 scale; higher latency score means a more favorable latency result. Technologies without a supported matching compatibility record are not recommended. Add actual source-backed GPU, game, compatibility, and evidence records through `/admin/` before relying on results.

### Automatic game-upscaler compatibility lookup

When staff add a game or GPU, the backend checks the maintained source for the new catalog pairs; each comparison request also refreshes its selected pair. [PCGamingWiki's high-fidelity upscaling list](https://www.pcgamingwiki.com/wiki/List_of_games_that_support_high-fidelity_upscaling) results are cached for six hours; a failed source request is briefly cached to avoid repeatedly delaying requests. The sync records a citation and explicit limitations, does not overwrite compatibility rows entered or reviewed by an administrator, and does not create performance scores. Automatically generated compatibility rows are pending suggestions, excluded from user recommendations until a staff administrator approves them in the PHP admin portal. Dismissed suggestions remain dismissed across future syncs. A missing/unconfirmed title remains `unknown`; it is not treated as unsupported. PCGamingWiki is community-maintained and its content is CC BY-NC-SA unless otherwise noted; retain attribution and review its terms before redistributing synced data.

This source establishes listed game integrations, not benchmark performance, support at a particular resolution, or a guarantee for every GPU/driver/version. Admins should review each suggestion's source and limitations before approval. Compatible options without scored research evidence are shown to users as compatible but unranked. Admins still need trustworthy benchmark evidence before the app can rank options.

Catalog endpoints allow public reads. Write operations require an authenticated staff account (JWT bearer token or Django admin session). Enable TLS in deployment; CORS origins are configured with `CORS_ALLOWED_ORIGINS`.

## Optional AI explanation

The recommendation remains deterministic and evidence-based in Django. An optional Groq API call only rewrites an already-supported recommendation into a short explanation. The model cannot select a different technology, browse the web, or act as a general chat assistant. It receives only the selected GPU/game/resolution/preference and a small excerpt of the stored compatibility and research records. When the API key is missing, the recommendation has insufficient evidence, or the provider is unavailable, the endpoint returns Django's existing explanation without an AI call or gracefully falls back to it.

### Enable the free-tier provider

1. Create a GroqCloud account and API key on its **Free** plan. The account owner must meet Groq's account age requirement. Do not add billing details or upgrade the project if you want to avoid provider charges.
2. Copy `.env.example` to `.env` and set `GROQ_API_KEY` to the secret key. Keep `.env` out of source control and never put the key in the Android app.
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
