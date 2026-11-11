# Architecture

## System overview

```mermaid
flowchart LR
    subgraph Clients
        A["Android app (Java)"]
        W["Web frontend (Netlify)"]
        P["PHP admin portal"]
    end
    API["Django REST API<br/>JWT authentication"]
    DB[("PostgreSQL in production<br/>SQLite locally")]
    SRC["Public compatibility source<br/>(optional lookup)"]
    AI["AI provider<br/>(optional explanations)"]

    A -->|HTTPS + JSON| API
    W -->|HTTPS + JSON| API
    P -->|HTTPS + JSON| API
    API --> DB
    API -.-> SRC
    API -.-> AI
```

The Android app and the two web clients all use the same API. The API holds every rule and every record, so
the clients stay thin.

## Inside the Android app

```mermaid
flowchart TD
    UI["ui/ : Activities, Fragments, adapters"]
    IF["data/ : repository interfaces<br/>AuthRepository, CatalogRepository,<br/>ComparisonRepository, RecommendationRepository"]
    API["data/api/ : HTTP implementations<br/>ApiClient, Api*Repository"]
    SES["Session + TokenStore<br/>(access token in memory,<br/>refresh token encrypted)"]
    MOD["model/ : plain data classes"]

    UI --> IF
    IF --> API
    API --> SES
    UI --> MOD
    API --> MOD
```

- Screens only know the repository **interfaces**. Swapping the data source (an in-memory fake, a different server)
  means changing one line in `Repos.java`.
- `ApiClient` runs every request on a background thread, delivers results on the main thread, and refreshes an
  expired access token once before giving up.
- `TokenStore` keeps the refresh token encrypted with a key held in the Android Keystore, so users stay signed in
  without the token ever being stored as plain text.

## How a recommendation is produced

```mermaid
sequenceDiagram
    participant App as Android app
    participant API as Django API
    participant DB as Database
    App->>API: POST /recommendations/ (gpu, game, resolution, preference)
    API->>API: Validate the request
    API-->>API: Optionally refresh compatibility suggestions from a public source (kept unverified)
    API->>DB: Supported and verified compatibility rows
    API->>DB: Matching research evidence
    API->>API: Score each option, weighted by the preference
    API-->>App: Recommendation, reason, confidence, options, sources, limitations
```

The result is **explainable**: every option carries its evidence and sources. When there is no scored evidence the
server says so instead of guessing.

## Data model

```mermaid
erDiagram
    GPU ||--o{ COMPATIBILITY : has
    GAME ||--o{ COMPATIBILITY : has
    TECHNOLOGY ||--o{ COMPATIBILITY : "applies to"
    TECHNOLOGY ||--o{ RESEARCH_EVIDENCE : "described by"
    GPU |o--o{ RESEARCH_EVIDENCE : "may narrow to"
    GAME |o--o{ RESEARCH_EVIDENCE : "may narrow to"
    USER ||--o{ SAVED_COMPARISON : owns
    USER ||--o{ USER_AI_USAGE : "daily quota"

    GPU {
        string name
        string manufacturer
        string architecture
    }
    GAME {
        string name
        string developer
        string engine
        int release_year
    }
    TECHNOLOGY {
        string key "dlss, fsr, xess"
        string name
    }
    COMPATIBILITY {
        string resolution
        string status
        bool is_verified
        bool is_rejected
    }
    RESEARCH_EVIDENCE {
        string source_title
        string source_url
        string preference
        int performance_score
        int image_quality_score
        int latency_score
        int confidence
    }
    SAVED_COMPARISON {
        string gpu_name
        string game_name
        string resolution
        string preference
        string recommendation
        string note
    }
```

## Security notes

- Passwords are checked by Django's validators (minimum 10 characters) and stored hashed.
- Writes to the GPU and game catalogue are limited to staff accounts. Saved comparisons are visible only to their owner.
- Registration and AI endpoints are rate limited.
- Secrets (`SECRET_KEY`, database address, API keys) come from environment variables and are never committed.
- The app contains no secrets. Anything sensitive stays on the server.
