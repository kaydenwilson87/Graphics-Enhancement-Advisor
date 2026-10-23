# Graphics Enhancement Advisor

An Android app that helps gamers choose between **NVIDIA DLSS**, **AMD FSR** and **Intel XeSS**.
The user picks a GPU, a game, a target resolution and a preference (performance, image quality or
balanced). The app then recommends a technology and explains why, using stored research evidence.

The app does **not** run live benchmarks and does not detect hardware. Every recommendation comes
from compatibility records and research evidence held on the server, and the app shows the sources,
confidence level and known limitations.

> College group project, Deliverable 4 (implementation).
> Team: **Kayden Wilson, Donel Kia Nzambi, Ismail Peterson, Kabanga Kalonji Christian Kabedi, Sebastian Duvenhage, Sinaso Martins** | Course: **ITMDA**

## Features

- **Accounts:** register, log in and log out. The login is kept encrypted on the phone, so users stay signed in.
- **Comparison wizard:** choose a GPU, then a game, then resolution and preference, with search on the lists.
- **Recommendation:** the technology, a confidence level, the reasons, the research sources, a comparison of every option and the limitations.
- **Saved comparisons (CRUD module 1):** save a result, browse and search the list, filter by technology, edit a note, delete.
- **GPU and game management (CRUD module 2):** admin-only screens to add, edit and delete GPUs and games.
- **Feedback everywhere:** form validation, loading spinners, confirmation dialogs, and error messages with a Retry button.

## How it fits together

```
Android app (Java)  --HTTPS/JSON-->  Django REST API  -->  PostgreSQL (production) / SQLite (local)
```

| Part | Folder | Technology |
|---|---|---|
| Mobile app | `app/` | Java, Android Views, Material Components (min SDK 24) |
| Backend API | `backend/Graphics-enhancement-advisor-backend/` | Python, Django, Django REST Framework, JWT login |
| Web admin | `php-admin/` | PHP site that uses the same API ([live portal](https://graphics-enhancement-advisor-3.onrender.com)) |

Inside the app, all screens talk to three small interfaces in `data/` (`AuthRepository`,
`CatalogRepository`, `ComparisonRepository`) plus `RecommendationRepository`. The HTTP code lives in `data/api/`.

## Run the backend locally

Requires Python 3.10 or newer.

```sh
cd backend/Graphics-enhancement-advisor-backend
python -m venv .venv
.venv\Scripts\activate            # Windows  (macOS/Linux: source .venv/bin/activate)
pip install -r requirements.txt
cp .env.example .env              # Windows: copy .env.example .env
python manage.py migrate
python manage.py createsuperuser  # this account is the app's admin
python manage.py runserver 0.0.0.0:8000
```

Check it at `http://127.0.0.1:8000/health/`. The default local database is SQLite and needs no setup.

## Deployment

The deployed services are [Netlify frontend](https://graphic-enhancement-advicer.netlify.app), [Django API](https://graphics-enhancement-advisor-1.onrender.com), and [PHP admin portal](https://graphics-enhancement-advisor-3.onrender.com). Both the Android app and PHP admin use the Django API. Deployment settings and local development overrides are documented in [DEPLOYMENT.md](DEPLOYMENT.md).

**Demo data (optional).** If `advisor/fixtures/demo_data.json` is present, load it on a fresh database with
`python manage.py loaddata demo_data`. Otherwise add GPUs and games in the app's admin screens, and
compatibility and research evidence rows at `http://127.0.0.1:8000/admin/`. The server only recommends
a technology when it has compatibility and evidence data for the chosen GPU and game.

## Run the Android app

1. Install **Android Studio** and open the repository's top-level folder (the one containing `settings.gradle.kts`).
2. Wait for the Gradle sync to finish.
3. The checked-in Gradle property points to the deployed API at `https://graphics-enhancement-advisor-1.onrender.com/api/v1/`. To use a local backend from an Android emulator, override it with `-PapiBaseUrl=http://10.0.2.2:8000/api/v1/` when building or running Gradle.
4. Create an emulator in *Device Manager* (or plug in a phone with USB debugging) and press **Run**.

**Accounts.** Register in the app to get a normal user. The account created with `createsuperuser` is a
staff account, so the *Manage GPUs* and *Manage games* buttons appear on its Profile tab.

## API used by the app

All paths are under `/api/v1/`.

| Purpose | Request |
|---|---|
| Register / log in | `POST auth/register/`, `POST auth/token/`, `POST auth/token/refresh/` |
| Current user and admin flag | `GET auth/me/` |
| GPUs and games | `GET gpus/`, `GET games/` (staff only: `POST`, `PUT`, `DELETE`) |
| Recommendation | `POST recommendations/` with `gpu_id`, `game_id`, `resolution`, `preference` |
| Saved comparisons | `GET`, `POST saved-comparisons/`, `PUT`, `DELETE saved-comparisons/<id>/` |

The backend's own README has the full API and deployment details.

## Tests

- Backend: `python manage.py test` (run from the backend folder).
- App: run through the flows below on an emulator.
  1. Register, log in, close the app, reopen it (still signed in), log out.
  2. Run the wizard to a recommendation, save it, edit its note on the Comparisons tab, delete it.
  3. As admin: add, edit and delete a GPU and a game.
  4. Stop the server and open a list: an error with a Retry button appears.

## Deployment

The backend ships with a `Dockerfile` for platforms such as Render or Railway. Set these as
environment variables on the host, never in the repository: `SECRET_KEY`, `DEBUG=False`,
`ALLOWED_HOSTS`, `DATABASE_URL` (PostgreSQL), and optionally `GROQ_API_KEY`. After the first deploy run
`migrate` and `createsuperuser`. Then change `BASE_URL` in the app to the hosted address.
Hosts on free plans may sleep when idle, so the first request after a quiet period can take about a minute.

## Known limitations

- There is no forgot-password flow, because the API has no endpoint for it.
- The app stores the user's email only, not a name.
- Compatibility and evidence records are managed on the server's `/admin/` page, not in the app.
- Recommendations are only as good as the evidence entered. Placeholder or demo evidence must be replaced with real, cited sources.
- Compatibility suggestions fetched automatically from a public source stay out of results until staff verify them.

