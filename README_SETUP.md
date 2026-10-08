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

## Play Through the API

The game is played through a small REST API. Run creation needs an invite code. With none configured, creation is closed.

```powershell
$env:GAME_INVITE_CODES = "local-invite"      # comma-separated; never commit real codes
.\mvnw.cmd spring-boot:run
```

**Client-generated tokens.** The client generates its own secret run token before creating a run: 32 random bytes as unpadded base64url, exactly 43 characters. The server stores only its SHA-256 hash, never returns it and never rotates it. Keep the token: losing it means losing access to the run.

Every state-changing request carries an `Idempotency-Key` UUID. Retrying with the same key replays the stored answer and never applies anything twice.

```bash
TOKEN=$(openssl rand 32 | basenc --base64url | tr -d '=')
# Create (or resume creating) a run
curl -s -X POST localhost:8080/api/v1/runs -H "X-Invite-Code: local-invite" \
  -H "Idempotency-Key: $(uuidgen)" -H "Authorization: Bearer $TOKEN"
# Read the current view (never calls AI, never writes)
curl -s localhost:8080/api/v1/runs/$RUN -H "Authorization: Bearer $TOKEN"
# Take a turn on the view's stateVersion (free text needs AI; slash commands always work)
curl -s -X POST localhost:8080/api/v1/runs/$RUN/turns -H "Authorization: Bearer $TOKEN" \
  -H "Idempotency-Key: $(uuidgen)" -H "Content-Type: application/json" \
  -d '{"input": "/hold", "stateVersion": 0}'
```

Slash commands are listed in `docs/AI_CONTRACTS.md`, for example:
- `/attack entity_1 slash with weapon_1`;
- `/defend parry`;
- `/move zone_2`;
- `/move exit_1`.

When the view says `"awaiting": "DEFENSE"`, the next turn must begin with a defense.

API settings, with defaults:

| Variable / property | Default |
|---|---|
| `GAME_INVITE_CODES` | empty (creation closed) |
| `GAME_CORS_ORIGINS` | empty (CORS off) |
| `GAME_AI_DAILY_CALL_LIMIT` | `500` provider calls per UTC day |
| `game.api.limits.turns-per-minute-per-run` | `12` |
| `game.api.limits.creations-per-hour-per-invite` / `creations-per-day` | `5` / `20` |
| `game.api.limits.invalid-invites-per-hour-per-address` / `invalid-invites-per-hour` | `10` / `30` |
| `game.api.lease` | `2m` |

Limits are kept in memory and reset on restart. Only the socket address identifies a client; forwarding headers are ignored. Behind a local proxy, all clients therefore share one address, and the global limits are the real protection.

## Optional: AI

The game runs fully without AI. AI is **off by default**, and every AI role then uses its deterministic fallback. Actions use slash commands such as `/attack entity_1 slash` (see `docs/AI_CONTRACTS.md`), and narration uses factual templates. The tests never call a real model and need no key.

To enable the OpenAI provider for the current PowerShell session:

```powershell
$env:GAME_AI_ENABLED = "true"
$env:OPENAI_API_KEY = "<your key>"
$env:GAME_AI_MODEL = "<an OpenAI model ID that supports structured outputs>"
.\mvnw.cmd spring-boot:run
```

| Variable | Default | Meaning |
|---|---|---|
| `GAME_AI_ENABLED` | `false` | Switches the AI roles on |
| `OPENAI_API_KEY` | empty | API key. Never commit it; it is never logged |
| `GAME_AI_MODEL` | empty | Model ID for all roles. There is deliberately no default |
| `OPENAI_BASE_URL` | the SDK default | Optional API base URL |

If AI is enabled but the key or model is missing, the application still starts. It logs one warning naming the missing variable, and the roles use their fallbacks.

Further optional settings go in `application.properties` or as environment variables:
- `game.ai.timeout` (default `20s`) and `game.ai.max-retries` (default `1`).
- Per role: `game.ai.roles.<role>.model`, `.max-output-tokens` and `.temperature`. The roles are `action-interpreter`, `outcome-narrator`, `enemy-attack-narrator` and `character-introduction`.

Default output-token limits are 1500 for the interpreter, 400 for the outcome narrator, 200 for the enemy attack narrator and 600 for the introduction. Reasoning models spend output tokens on reasoning, so raise these if responses come back truncated; a truncated response falls back.

### Manual AI smoke test

One opt-in test calls the real API. The normal test run excludes it. With `OPENAI_API_KEY` and `GAME_AI_MODEL` set:

```powershell
.\mvnw.cmd test "-Dgroups=ai-smoke" "-DexcludedGroups=none" "-Dtest=OpenAiSmokeTest"
```
