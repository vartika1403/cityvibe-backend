# CityVibe — Backend (Spring Boot + MySQL)

REST API built with **Spring Boot 3.2 (Java 17)** and **Spring Data JPA / Hibernate**
over **MySQL**, with schema and seed data managed by **Flyway**.

The Android client lives in a separate repo: `vartika1403/CityVibe`.

## Entity: `Event`

`id, title, category, description, imageUrl, dateTime, venueName, venueAddress,
organizer, price`

`dateTime` is an ISO-8601 **string**, e.g. `2026-07-18T19:00:00`, not a temporal type.

## Endpoints

| Method | Path                             | Auth         | Description                                        |
|--------|----------------------------------|--------------|----------------------------------------------------|
| GET    | `/api/events`                    | public       | List events. Optional `?category=`; `All` = no filter |
| GET    | `/api/events/{id}`               | public       | Single event, `404` if missing                     |
| GET    | `/api/events/serpapi/search`     | `X-Admin-Token` | Search Google Events via SerpApi (no writes)    |
| POST   | `/api/events/serpapi/seed`       | `X-Admin-Token` | Search and persist new events, deduped by title |
| POST   | `/api/events/seed/ticketmaster`  | `X-Admin-Token` | Import from Ticketmaster, deduped by title      |

There is **no** `/api/health` endpoint — Actuator is not a dependency. Use
`GET /api/events` as a liveness check; it exercises the database path.

The guarded endpoints fail closed: if `ADMIN_SEED_TOKEN` is unset, they return `403`
rather than running unauthenticated.

## Schema and seed data

Flyway owns the schema; Hibernate only validates against it
(`spring.jpa.hibernate.ddl-auto=validate`), so an unmigrated database is a hard
startup failure rather than a silent auto-create.

| Migration               | Effect                                                    |
|-------------------------|-----------------------------------------------------------|
| `V1__init.sql`          | Creates `events`                                          |
| `V2__drop_duration.sql` | Drops the unused `duration` column                        |
| `V3__seed_events.sql`   | Inserts the 8 Bengaluru launch events, guarded by `NOT EXISTS` |

`DataSeeder.java` also holds those 8 events, but it is `@Profile("dev")` and the
`Dockerfile` pins the `prod` profile — so on any deployed host the data comes from
V3, not from that class. Categories: `Music`, `Comedy`, `Meetup`, `Gathering`.

## Configuration

No secret has a committed default; this repo is public and anything unset fails
rather than falling back.

**Required** — the app will not start without them:

- `DB_URL` (dev default: `jdbc:mysql://localhost:3306/cityvibe?...`)
- `DB_USERNAME` (dev default: `cityvibe`)
- `DB_PASSWORD` (dev default: `cityvibe123`)

The dev defaults above apply only under the `dev` profile. The `prod` profile
(`application-prod.properties`) supplies no defaults at all.

**Optional** — each disables a feature when unset:

- `TICKETMASTER_API_KEY` — Ticketmaster import returns `401`
- `SERPAPI_API_KEY` — SerpApi endpoints raise `SerpApi key not configured`
- `ADMIN_SEED_TOKEN` — all three guarded endpoints return `403`
- `CORS_ALLOWED_ORIGINS` — defaults to `*`; set a real origin in production
- `PORT` — HTTP port, defaults to `8080` (note: `PORT`, not `SERVER_PORT`)
- `SPRING_PROFILES_ACTIVE` — defaults to `dev` locally; the `Dockerfile` sets `prod`

## Run locally

```bash
# 1. Create the database
mysql -u root -e "CREATE DATABASE cityvibe; \
  CREATE USER 'cityvibe'@'localhost' IDENTIFIED BY 'cityvibe123'; \
  GRANT ALL ON cityvibe.* TO 'cityvibe'@'localhost';"

# 2. Run (there is no Maven wrapper in this repo — use a local mvn)
mvn spring-boot:run

# or build a jar:
mvn -DskipTests clean package && java -jar target/cityvibe-backend-1.0.0.jar
```

Flyway applies V1–V3 on first start, so `GET /api/events` returns the 8 seed events
straight away.

To exercise the Ticketmaster or SerpApi endpoints locally, export the matching keys
plus `ADMIN_SEED_TOKEN` first. Note that Ticketmaster has no India inventory, so
`city=Bangalore` legitimately returns 0 results.

## Deployment

See [DEPLOYMENT.md](DEPLOYMENT.md) for Railway and Render, including the full
variable list and key rotation.
