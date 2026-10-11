# Frontend

React + TypeScript + Vite client for *The Dying Flame* (a working title; set in `src/config.ts`): a mobile-first interactive
storybook. The Hollow Chapel is the game's first region.

Requires Node 20.18 or later. Vite is held at 6.x, because Vite 7 needs Node 20.19 or later.

## Commands

Run these from the `frontend/` folder:

| Command | Purpose |
|---|---|
| `npm install` | install dependencies |
| `npm run dev` | dev server at http://localhost:5173; `/api` is proxied to Spring Boot on :8080 |
| `npm test` | unit and component tests (Vitest, jsdom); no network |
| `npm run typecheck` | TypeScript check |
| `npm run e2e:install` | one-time download of Playwright's Chromium |
| `npm run e2e` | browser tests at phone and desktop sizes; starts the dev server and fakes the API |
| `npm run build` | production build into `dist/` (packaging with Spring Boot comes in Stage 15E) |

## Design preview

With `npm run dev` running, open http://localhost:5173/preview.html.

The preview shows the story screen with hand-written **sample** text. It never calls the API or saves anything, and it is not part of the production build.

The `npm run e2e` run saves screenshots to `test-results/screens/`.

## Playing (Stage 15D)

You write an action and send it. The server interprets and resolves it, and the confirmed narration is revealed in the story.

- **Keys:** on a desktop keyboard, Enter sends and Shift+Enter adds a new line. On a phone, Enter adds a new line and the send button sends.
- **Outbox:** every action is saved on the device, under its own idempotency key, before it is sent (`src/storage/outbox.ts`). Retries and recovery after a reload always resend that exact record, so an action is never applied twice.
- **One sender per tab:** only one tab at a time may send for a run (`src/play/turnLock.ts`: Web Locks, or a localStorage lease where Web Locks are unavailable).
- **Retry timings:** set in `src/play/turnOutcome.ts` (`retryTiming`).

## Real-backend check (opt-in)

Two specs run against a local Spring Boot server with AI disabled:
- **`e2e/real-backend.spec.ts`** restores a 25-turn chronicle from PostgreSQL.
- **`e2e/real-play.spec.ts`** plays through the UI: commands, a refused free-text action, an attack, a defense and a counter.

Both are skipped unless `E2E_REAL_API=1`; the commands are in the specs' header comments. Run them with `--workers=1`. The turn-limit override in those commands is a test-only command-line argument; production defaults are unchanged.

The real-OpenAI play test runs only with `E2E_REAL_AI=1` and a server using real AI. It spends a few small model calls.

## Playing a real run

1. Start Spring Boot with an invite code. See `../README_SETUP.md`, "Play Through the API".
2. Open http://localhost:5173.

Creating, resuming and recovering tales work. The story screen restores the full chronicle, and turns are played from it.

## Where things live

- `src/api`: typed API client. Tokens are sent only in the `Authorization` header.
- `src/security`: run tokens and recovery codes.
- `src/storage/vault.ts`: saved runs in localStorage. Credentials are saved before the creation request is sent.
- `src/runs`: create, retry, open and import runs.
- `src/chronicle`: chronicle state (merge, pagination, gap-safe refresh), the scroll behaviour and the live story data.
- `src/reveal`: paragraph reveal (`revealConfig.ts` sets the timing) and reduced motion.
- `src/story`: storybook components shared by the real screens and the preview.
- `src/styles/tokens.css`: every colour, size and timing. Tune the presentation here.
