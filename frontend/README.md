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

## Playing a real run

1. Start Spring Boot with an invite code. See `../README_SETUP.md`, "Play Through the API".
2. Open http://localhost:5173.

Stage 15B covers creating, resuming and recovering tales, and a story view of the current state. The full chronicle (15C) and playing turns (15D) come next.

## Where things live

- `src/api`: typed API client. Tokens are sent only in the `Authorization` header.
- `src/security`: run tokens and recovery codes.
- `src/storage/vault.ts`: saved runs in localStorage. Credentials are saved before the creation request is sent.
- `src/runs`: create, retry, open and import runs.
- `src/story`: storybook components shared by the real screens and the preview.
- `src/styles/tokens.css`: every colour, size and timing. Tune the presentation here.
