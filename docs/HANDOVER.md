# Handover — read this first if you are new to UniTrade (person or Claude Code session)

Written 2026-10-08 at the end of Slice 0. Everything needed to continue is in this repository; nothing depends on the original developer's PC or Claude session.
Read in this order: `CLAUDE.md` (rules, requirements, build order) → this file → `README.md` (how to run) → `docs/EVIDENCE.md` (results, decisions D1–D11, defects) → `docs/DEPLOYMENT.md` (hosting).

## 1. Where things stand

| Item | State |
|---|---|
| Slice 0 — scaffolding | **Done.** Spring Boot API + React app, `GET /api/health`, MySQL verified, H2 for tests, evidence pipeline proven (6/6 tests, see EVIDENCE.md §2). CI green on GitHub. |
| Slice 1 — FR1 Auth | **Built and tested (2026-10-08).** 30/30 automated tests pass; verified against MySQL with curl. Waiting for Collins's commit and manual tests M1-01…M1-05. |
| Slice 2 — FR2 Listings | **Built and tested (2026-10-08).** 54/54 automated tests pass; verified against MySQL with curl. Waiting for Collins's commit and manual tests M2-01…M2-04. |
| Slice 3 — FR3 Search/filter | **Built and tested (2026-10-08).** 72/72 automated tests pass; verified against MySQL. Waiting for Collins's commit and manual tests M3-01…M3-03. |
| Slice 4 — FR4 Cart/checkout/payment | **Built and tested (2026-10-08).** 102/102 automated tests pass (incl. a two-thread race test with a mutation check); verified against MySQL. Waiting for Collins's commit and manual tests M4-01…M4-06. |
| Slice 5 — FR6 Reviews | **Built and tested (2026-10-09).** 120/120 automated tests pass; demo seed now includes one completed order with a review. Waiting for Collins's commit and manual tests M5-01…M5-04. |
| Slice 6 — FR5 Bulletin board | **Built and tested (2026-10-09).** 136/136 automated tests pass. Waiting for Collins's commit and manual tests M6-01…M6-03. |
| Slice 7 — NFR2 Redis + load test | **Next.** Not started: needs the cache, the `perf` profile with 10,000 listings and two measured runs (EVIDENCE §3). Deadline is 9 Oct 2026. |
| Slice 8 — separate payment service | Not started (stretch, only after Slice 7). |
| Deployment | Frontend live on Vercel: https://uni-trade-eight.vercel.app. API https://unitrade-cput-api.onrender.com and DB (Aiven): see DEPLOYMENT.md §1 for the current state. Container and production mode tested locally. |
| Manual tests | M0-01…M0-04 in EVIDENCE.md §2.3 are `NOT RUN` (a person must run them and add screenshots). |

## 2. Set up on a new machine (about 15 minutes)
1. Install: JDK 17+ (21 used), Node 22.12+ (or 20.19+), MySQL 8, Git. Docker Desktop is optional (Redis in Slice 7; local container test).
2. `git clone https://github.com/shibambocollins/UniTrade.git` (you need to be added as a collaborator to push).
3. Create the MySQL database and user: README §2 (the SQL includes `ALTER USER`, which matters if the user already exists).
4. Copy `backend/application-local.properties.example` to `backend/application-local.properties` and put your MySQL password in it (git-ignored).
5. Run: README §3 (backend) and §4 (frontend, `npm ci` — not `npm install`, see §6 below). Open http://localhost:5173 → "API: UP · Database: UP".
6. Tests: README §5. All should pass before you change anything.

## 3. Continue with Claude Code (new session)
Open Claude Code in the cloned repo and paste:

```
Read CLAUDE.md, docs/HANDOVER.md, docs/EVIDENCE.md and docs/DEPLOYMENT.md first.
This machine is new: check the toolchain, help me set up MySQL and application-local.properties
following the README, then run the backend and frontend tests. Tell me briefly what you found.
Then build Slice 1 (FR1 Auth) as described in CLAUDE.md, using the designs in docs/screens,
and stop with a summary. Do not run git add/commit/push unless I ask.
```

To deploy the backend (any time; it does not depend on Slice 1), paste:

```
Read docs/DEPLOYMENT.md. I am creating the Render web service and the Aiven MySQL database
with my own free accounts. Guide me field by field through sections 4.A-4.C, check my values
(I will describe them or share screenshots without passwords), then run scripts/check-deploy.mjs
against the live site and update DEPLOYMENT.md section 1 and EVIDENCE.md (manual test M0-04).
```

## 4. Decisions made in the first session (also in EVIDENCE.md §9)
- **Git:** the person commits and pushes; Claude does not run `git add/commit/push` unless asked in that message. Commit once per finished slice (EVIDENCE.md §11 uses the timestamps).
- **UI designs:** Figma file https://www.figma.com/design/c3ea5kHwbYj3ZEBtGzSPte/Markplace (Page 1, 17 mobile frames at 440×956). 10 are exported in `docs/screens/`. Not yet exported: Search & Filters (`32:13`), Product Detail (`35:14`), My Listings (`42:580`), Order Confirmed (`64:63`), Bulletin Board (`66:255`), plus Vendor (`42:702`) and Chat (`78:350`), which are out of scope. The claude.ai Figma connector works, but its free (Starter) plan allows only about 10 calls before a limit, so prefer the PNGs; export missing frames manually in Figma (select the frame → Export → PNG) into `docs/screens/`.
- **Design approach (D11):** keep the wireframes' screens and flows; polishing the visuals is approved (the wireframes are rough greyscale). Do **not** build: Google sign-in, "Forgot password", Student/Vendor toggle, Vendor screen, Notifications, Chat. These are logged as limitation L8 / L1 / L4.
- **Versions (D4, D5):** Spring Boot 3.5.16 (Initializr only offers 4.x now; pom is hand-written). Frontend pinned to React 19, React Router 7, Vite 7, Vitest 4. Don't upgrade majors casually.
- **Deployment (D7, D9, D10):** Vercel (frontend) + Render Docker free (API `unitrade-cput-api`) + Aiven free MySQL. `render.yaml` recreates the API service; `scripts/check-deploy.mjs` verifies a deployment. JVM flag `-XX:TieredStopAtLevel=1` in the Dockerfile halves cold start at 0.1 CPU.
- **Configuration rule (D12) — important because the Vercel account is not yours:** the hosting dashboards hold only secrets, set once: `SPRING_PROFILES_ACTIVE=prod`, `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`. Every other deployed setting is in git: `backend/src/main/resources/application-prod.properties` (API) and `frontend/.env.production` (API address for the website). Change them by commit; the push redeploys. New settings: put a safe default in `application.properties` and the production value in `application-prod.properties`. Details: DEPLOYMENT.md §2.
- **Slice 1 note:** `JWT_SECRET` is one of the five Render variables (create it with Render's **Generate** button when you create the service) and is mapped to `app.jwt.secret` in the prod profile. Read it from `app.jwt.secret`; derive the HMAC key from it (e.g. SHA-256 of its UTF-8 bytes) so any long string works; for local runs, if it is blank, generate a random key at start-up and log a warning (tokens then expire on restart).

## 5. Accounts and access

| What | Owner | What the next person needs |
|---|---|---|
| GitHub repo `shibambocollins/UniTrade` (public) | Collins | To be added as collaborator (repo → Settings → Collaborators) to push |
| Vercel project `uni-trade` | Collins's Vercel account | Nothing: every push to `main` redeploys automatically. Logs/settings: ask Collins |
| Render service `unitrade-cput-api` | **You** — create a free Render account (sign in with GitHub) | Create the service once (DEPLOYMENT.md §4.C). If the repo isn't listed in Render, Collins must allow the Render GitHub app on it (§4.A) |
| Aiven MySQL | **You** — create a free Aiven account | Create the database once (DEPLOYMENT.md §4.B). If it is powered off after a quiet period, power it on in the Aiven console |
| Figma file | Collins | View access via the link above |
| Claude / Claude Code | shared account | New session per machine; this file replaces the old session's memory |

If the hosting must move to someone else's accounts, follow DEPLOYMENT.md §3 from scratch with their own accounts (about 20 minutes). Their GitHub user must be able to install the Vercel/Render GitHub apps on the repo, which for a personal repo means the owner (Collins) does it, or the repo is transferred.

## 6. Gotchas already hit (details in EVIDENCE.md §8 and §13)
- **npm 10 crashes on `npm install`** for this dependency set (DEF-02): use `npm ci` (reads `package-lock.json`). If the lockfile must be regenerated, use `npx npm@11 install`.
- **PowerShell and `-D` arguments:** quote them: `.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=h2"`.
- **Port 8080 still in use** after stopping a background backend on Windows: `Get-NetTCPConnection -LocalPort 8080` → stop that `java` process.
- **MySQL "Access denied (using password: YES)":** the DB user's password differs from the config; re-run the README SQL (it has `ALTER USER`) (DEF-03).
- **API client rule (DEF-04):** a successful answer that is not JSON is treated as an error; backend endpoints must always return JSON or 204.
- **Aiven MySQL requires a primary key on every table** (local MySQL does not): every entity needs an `@Id`; use a `Set` for `@ManyToMany` (composite key) or a join entity. Otherwise the deployed API fails at start-up while local runs work.
- **Free hosting sleeps:** first request after 15 idle minutes takes ~1.5–3 min; warm up before demos.

- **Port 8080 may be taken** by another project on the developer's PC: run the API with `PORT=8081` and the dev server with `API_PROXY_TARGET=http://localhost:8081` (README §4). The default local database login is in the git-ignored `backend/application-local.properties` (it may use the MySQL `root` user on this machine).
- **Auth for later slices:** controllers get the logged-in student with `@AuthenticationPrincipal AuthenticatedUser user` (`user.id()`); errors are thrown as `ApiException.notFound/forbidden/conflict/badRequest(...)`; public endpoints (e.g. `GET /api/listings`) must be added to the `permitAll` list in `SecurityConfig`; the frontend sends the token with `apiRequest(path, { token })` and `useAuth()` gives `user` and `token`; wrap protected pages in the `<RequireAuth>` route in `App.jsx`.

## 7. Evidence rules (short version of CLAUDE.md §7)
Update `docs/EVIDENCE.md` at the end of every slice; never invent results; run `node scripts/test-report.mjs` after tests; manual tests start as `NOT RUN`; name tests with the requirement ID (`fr1_01_...`, class `Fr1AuthTest`).
