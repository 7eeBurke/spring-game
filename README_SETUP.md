# Local Setup

Commands are for PowerShell on Windows. Run them from the repository root.

## Prerequisites

- JDK 21
- Docker Desktop, running. It is needed both for the local database and for the test suite, which starts its own PostgreSQL container.

You never create tables by hand: Flyway applies the migrations in `src/main/resources/db/migration/` when the application starts.

## Start PostgreSQL

```powershell
docker compose up -d --wait
```

This starts PostgreSQL 18.4 (`compose.yaml`) with database `spring_game`, user `spring_game` and password `spring_game` on host port **5433**. These are local development values only.

The host port is 5433, not PostgreSQL's usual 5432, so it does not conflict with a locally installed PostgreSQL service (for example the Windows service `postgresql-x64-14`), which normally owns 5432. Inside the container PostgreSQL still listens on 5432. The application's default datasource URL already points at 5433, so no extra configuration is needed.

### Using a different host port

If 5433 is also taken, choose another host port for the container and point the application at it:

```powershell
$env:SPRING_GAME_DB_PORT = "5434"
docker compose up -d --wait
$env:SPRING_DATASOURCE_URL = "jdbc:postgresql://localhost:5434/spring_game"
```

Environment variables last for the current PowerShell session. Set them again in each new session, before running `docker compose` and the application.

If the application reports `password authentication failed for user "spring_game"`, it is probably reaching a different PostgreSQL server on that port rather than the container.

## Stop PostgreSQL

```powershell
docker compose down
```

Data is kept in the `spring-game-postgres` Docker volume. To delete it and start from an empty database:

```powershell
docker compose down -v
```

## Run the Tests

```powershell
.\mvnw.cmd test
```

Docker must be running. Integration tests start their own `postgres:18.4` container through Testcontainers; they do not use the Compose database. The first run downloads the image and is slower.

## Run the Application

With PostgreSQL started:

```powershell
.\mvnw.cmd spring-boot:run
```

Stop it with `Ctrl+C`.

## Configuration Overrides

The datasource defaults are in `src/main/resources/application.properties`. Override them with environment variables:

| Variable | Default |
|---|---|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5433/spring_game` |
| `SPRING_DATASOURCE_USERNAME` | `spring_game` |
| `SPRING_DATASOURCE_PASSWORD` | `spring_game` |
| `SPRING_GAME_DB_PORT` (Compose host port) | `5433` |

Do not commit real credentials.
