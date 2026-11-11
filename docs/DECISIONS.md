# Design decisions

Short notes on why the project is built the way it is.

## 1. Native Android with Java and Views
The project started as a college assignment taught in Java, so the first version used Java and classic Views.
Cost: more boilerplate than Kotlin with Jetpack Compose. Plan: write new screens in Kotlin and Compose alongside it.

## 2. A separate REST API instead of logic in the app
The server owns accounts, data and recommendation rules. That keeps the app thin, lets a web client and an admin
portal share the same rules, and means rules can change without an app update.

## 3. Repository interfaces between screens and networking
Screens talk to interfaces such as `CatalogRepository`. The first version used in-memory fakes, then the HTTP
implementations replaced them by changing one line. Tests and future data sources use the same seam.

## 4. Evidence-based, explainable recommendations
The server scores options from stored compatibility records and cited research evidence. It shows sources, confidence
and limitations, and returns no recommendation when it has no evidence. Cost: results are only as good as the data.
Benefit: the app never presents a guess as a fact.

## 5. Short-lived access token, encrypted refresh token
The access token lives in memory only. The refresh token is encrypted with a key in the Android Keystore so users stay
signed in. If decryption fails, the app clears it and asks for a new login.

## 6. Free hosting trade-offs
The API runs on a free host that sleeps when idle, so the first request can be slow. The app uses long timeouts and
shows a Retry button. Moving to a paid always-on instance is the production path.

## 7. AI features stay on the server (planned)
Any chatbot will run through the API, never inside the app, so provider keys cannot be extracted from the APK. Answers
will be grounded in the project's own database to avoid invented settings.
