# CityVibe Backend — Deployment Guide (Railway / Render)

The Spring Boot backend ships with a multi-stage `Dockerfile`, so any Docker-capable
host can run it. The `Dockerfile` sets `SPRING_PROFILES_ACTIVE=prod`; everything else
comes from environment variables. No secret has a committed default — the repo is
public, so anything omitted here simply fails rather than falling back.

## Required

The app will not start without these. `spring.jpa.hibernate.ddl-auto=validate` means
an unreachable or unmigrated database is a hard startup failure, not a degraded mode.

| Variable      | Purpose                              | Example                                                          |
|---------------|--------------------------------------|------------------------------------------------------------------|
| `DB_URL`      | JDBC connection string               | `jdbc:mysql://host:3306/cityvibe?useSSL=true&serverTimezone=UTC` |
| `DB_USERNAME` | MySQL user                           | `cityvibe`                                                       |
| `DB_PASSWORD` | MySQL password                       | `********`                                                       |

`PORT` is injected by the platform and defaults to `8080`; you rarely set it yourself.

## Optional — but each one disables a feature when unset

The app boots fine without these. They fail closed: the affected endpoint returns an
error at call time instead of quietly using a baked-in key.

| Variable               | Effect when unset                                                        |
|------------------------|--------------------------------------------------------------------------|
| `TICKETMASTER_API_KEY` | `POST /api/events/seed/ticketmaster` gets a `401` from Ticketmaster.      |
| `SERPAPI_API_KEY`      | The `/api/events/serpapi/*` endpoints raise `SerpApi key not configured`. |
| `ADMIN_SEED_TOKEN`     | **Every guarded endpoint returns `403`** — including SerpApi search. Below. |
| `CORS_ALLOWED_ORIGINS` | Defaults to `*`. Fine locally, but set it to your real origin in prod.    |

### `ADMIN_SEED_TOKEN` deserves attention

It is the *only* guard on three endpoints, which all compare it against an
`X-Admin-Token` request header:

- `POST /api/events/seed/ticketmaster` — writes to the database
- `POST /api/events/serpapi/seed` — writes to the database
- `GET  /api/events/serpapi/search` — read-only, but still guarded because it spends
  your metered SerpApi quota

All three fail closed, so a blank or missing value disables them entirely rather than
leaving them open. The public `GET /api/events` and `GET /api/events/{id}` are
unguarded and keep working regardless.

Generate one with `openssl rand -hex 32`. Never commit it — set it only in the
platform's variable settings.

## Seed data

The 8 Bengaluru launch events are loaded by the Flyway migration
`src/main/resources/db/migration/V3__seed_events.sql`, which runs automatically on
first boot in **every** environment.

They are deliberately not loaded by `DataSeeder.java` — that class is `@Profile("dev")`
while the `Dockerfile` pins the `prod` profile, so it never runs on a deployed host.
Before V3 existed, a container deploy came up with an empty `events` table.

Each `INSERT` is guarded by `NOT EXISTS` on title, so re-running against a database
that already holds these rows inserts nothing.

## Health check

**There is no `/api/health` endpoint** — Actuator is not on the classpath and no
controller defines one. Point your platform's health check at `GET /api/events`
instead, which exercises the database path anyway. Add
`spring-boot-starter-actuator` if you want a real `/actuator/health`.

---

## Option A — Railway (recommended: has managed MySQL)

1. Push this repo to GitHub.
2. Go to https://railway.app → **New Project → Deploy from GitHub repo**.
3. There is no root directory to configure — this repo holds the backend alone, so
   `Dockerfile` and `railway.json` sit at the top level and Railway finds them
   immediately.
4. **+ New → Database → MySQL**, before the first successful boot. Railway exposes
   `MYSQLHOST`, `MYSQLPORT`, `MYSQLDATABASE`, `MYSQLUSER`, `MYSQLPASSWORD`.
5. On the **backend** service (not the MySQL one) → **Variables**:
   ```
   DB_URL=jdbc:mysql://${{MySQL.RAILWAY_TCP_PROXY_DOMAIN}}:${{MySQL.RAILWAY_TCP_PROXY_PORT}}/${{MySQL.MYSQLDATABASE}}?useSSL=true&allowPublicKeyRetrieval=true&serverTimezone=UTC
   DB_USERNAME=${{MySQL.MYSQLUSER}}
   DB_PASSWORD=${{MySQL.MYSQLPASSWORD}}
   TICKETMASTER_API_KEY=<your key>
   SERPAPI_API_KEY=<your key>
   ADMIN_SEED_TOKEN=<openssl rand -hex 32>
   CORS_ALLOWED_ORIGINS=https://<your-app-origin>
   ```
   The proxy variables come from the **MySQL** service's own Variables tab;
   `RAILWAY_TCP_PROXY_PORT` is a random high port, never 3306. Referencing them
   rather than pasting literals keeps the config correct if Railway reassigns
   the proxy.

   The private host `${{MySQL.MYSQLHOST}}` (`mysql.railway.internal`) is the
   alternative, and avoids sending traffic over the internet — but Railway's
   private network is IPv6-only, and the JVM does not prefer IPv6 by default,
   so it fails with "The driver has not received any packets from the server".
   Use it only alongside `JAVA_TOOL_OPTIONS=-Djava.net.preferIPv6Addresses=true`
   on the backend service.

6. **Settings → Networking → Generate Domain** for a public URL.
7. Verify: `curl https://<your-domain>/api/events` → should return **8 events**.
   An empty array means Flyway did not apply; check the deploy logs.

## Option B — Render (bring your own MySQL)

Render offers only managed PostgreSQL, so pair it with an external MySQL such as
**Aiven** (free tier) or **Clever Cloud**.

1. Create a MySQL instance, and a database named `cityvibe`.
2. On https://render.com → **New → Web Service → Build from a Git repository**.
   Render picks up `render.yaml` / the `Dockerfile` at the repo root.
3. Set the same variables as Railway step 5, with `DB_URL` pointing at your instance:
   ```
   DB_URL=jdbc:mysql://<host>:<port>/cityvibe?useSSL=true&serverTimezone=UTC
   ```
4. Deploy and verify `https://<service>.onrender.com/api/events`.
   (Free tier sleeps after 15 min idle — the first request may take ~30s.)

---

## Rotating a leaked key

Removing a key from the source does **not** invalidate it, and it survives in git
history. Rotation has to happen at the vendor:

- **Ticketmaster** — https://developer.ticketmaster.com → My Apps → regenerate.
- **SerpApi** — https://serpapi.com/manage-api-key → regenerate.
- **`ADMIN_SEED_TOKEN`** — no vendor; just generate a new one and update the variable.

Then update the platform variables. Once rotated, the old strings in git history are
inert, which is why rewriting history is usually not worth the disruption.

## Point the Android app at the new backend

The Android client lives in a **separate repo** (`vartika1403/CityVibe`, under
`cityvibe-android/`). Edit `app/src/main/java/com/cityvibe/app/util/Constants.kt`
there:

```kotlin
const val BASE_URL = "https://<your-deployed-domain>/"
```

Then rebuild the APK:

```bash
./gradlew assembleDebug
```

## Test the Docker image locally (optional)

```bash
docker build -t cityvibe-backend .
docker run -p 8080:8080 \
  -e DB_URL="jdbc:mysql://host.docker.internal:3306/cityvibe?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC" \
  -e DB_USERNAME=cityvibe -e DB_PASSWORD=cityvibe123 \
  -e ADMIN_SEED_TOKEN=local-dev-token \
  cityvibe-backend
```

The image defaults to the `prod` profile; add `-e SPRING_PROFILES_ACTIVE=dev` to run
the dev profile instead.
