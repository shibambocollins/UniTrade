# Deployment (free tiers)

The marker runs the app locally (see README). The deployment is for the demo video and so problems surface early.
Written so the person taking over can finish the backend deployment with **their own free Render and Aiven accounts** (section 4, about 20 minutes), and change settings later by commit instead of through dashboards.

## 1. Current state (updated 2026-10-09)

| Part | Host | Status | Address |
|---|---|---|---|
| Frontend (React build) | **Vercel**, project `uni-trade` (Collins's account) | **Live**, redeploys on every push to `main`. Nothing to do. | https://uni-trade-eight.vercel.app |
| Backend (Spring Boot) | **Render**, web service `unitrade-cput-api` (Docker, free, Frankfurt), built from GitHub `HumphreyMahlangu/UniTrade` branch `main` | **Live since 2026-10-09.** Redeploys on every push that changes `backend/`. Sleeps after 15 idle minutes (first request then takes 1.5–3 min). | https://unitrade-cput-api.onrender.com |
| Database (MySQL) | **Aiven**, free MySQL service `unitrade-db`, database `defaultdb` | **Live since 2026-10-09.** Tables created and demo data seeded by the API on first start. | (connection details only in Render's environment variables) |

**Check run 2026-10-09 18:10 (+0200):** `node scripts/check-deploy.mjs --web https://uni-trade-eight.vercel.app --api https://unitrade-cput-api.onrender.com` → **all 6 checks PASS** (API and database UP, site and deep links load, the site points at this API, CORS allows the site and refuses others). The live API returned 14 active demo listings and 4 bulletin posts, and the demo login `thabo@mycput.ac.za` worked.

```mermaid
flowchart LR
    U["Browser"] -->|"HTTPS: pages, JS, CSS"| V["Vercel<br/>uni-trade (static React build)"]
    U -->|"HTTPS: /api/* (CORS)"| R["Render<br/>unitrade-cput-api (Docker, Spring Boot, profile prod)"]
    R -->|"JDBC over TLS"| A[("Aiven<br/>MySQL 8, free")]
    G["GitHub main"] -.->|"push = redeploy"| V
    G -.->|"push touching backend/ = redeploy"| R
```

## 2. Configuration rule: set once in dashboards, everything else in git

| Setting | Where it lives | How to change it |
|---|---|---|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET` (secrets) | Render → service → Environment, **set once** at creation | Only if the database or secret is replaced (Render account owner) |
| `SPRING_PROFILES_ACTIVE=prod` | Render → Environment, set once | Never |
| Everything else for the deployed API (allowed frontend address, pool size, future settings) | [`backend/src/main/resources/application-prod.properties`](../backend/src/main/resources/application-prod.properties) — **in git** | Edit and push → Render redeploys |
| Address of the API used by the website | [`frontend/.env.production`](../frontend/.env.production) — **in git** | Edit and push → Vercel redeploys |
| Vercel dashboard settings | Root Directory `frontend`, preset Vite (no environment variables needed) | Never |

So future slices never need dashboard access: new non-secret settings go in `application-prod.properties` (with safe defaults in `application.properties` for local runs). Only add a new *secret* if unavoidable — it needs the Render account owner (or a token, section 7).
If a secret is missing, the API refuses to start and its log says exactly which one (tested: `nfr1_02`).

## 3. Free-tier facts that affect the demo (checked 2026-10-08)
- **Render free web service:** 0.1 CPU, 512 MB RAM; **sleeps after 15 minutes without traffic**; 750 free hours per month; no card needed. ([render.com/docs/free](https://render.com/docs/free))
- **Measured cold start:** the same container limited to 0.1 CPU / 512 MB on the dev PC took **93 s** until `/api/health` answered (167 s before the JVM flag in `backend/Dockerfile`), using ~135 MB RAM. Expect **1.5–3 minutes** for the first request after the API slept; the site shows "the server may be waking up…" after 6 s.
- **Before recording the demo:** open the site (or run the check in section 5) ~3 minutes before.
- **Aiven free MySQL:** 1 CPU, 1 GB RAM, 1 GB storage, no card, no expiry; may be **powered off if unused for a while** (email first; power it on in the Aiven console). Every table must have a **primary key** (`sql_require_primary_key` is on). ([free plan](https://aiven.io/docs/platform/concepts/free-plan), [primary keys](https://aiven.io/docs/products/mysql/howto/create-tables-without-primary-keys))
- Redis is not used in deployment.

## 4. Build it from nothing
Order: **A** Render account → **B** Aiven database → **C** Render service (needs B's values) → check (section 5).
Vercel needs nothing more: `frontend/.env.production` already points at `https://unitrade-cput-api.onrender.com`.

### 4.A Render account and GitHub access (~3 min)
1. https://render.com → **Get Started** → **GitHub** → sign in with your own GitHub account (you must be a collaborator on `shibambocollins/UniTrade`).
2. In **New → Web Service**, check that `shibambocollins/UniTrade` is listed under Git Provider. If it is not:
   - only the repo owner can grant Render access to a personal repo: ask Collins to open https://github.com/apps/render/installations/new → his account → **Only select repositories** → `UniTrade` → Install/Save; or
   - the repo is public, so you can use the **Public Git Repository** tab with `https://github.com/shibambocollins/UniTrade`. Automatic deploys may then not be available; after each push use **Manual Deploy → Deploy latest commit**.
3. No card needed. Create the database (4.B) before the service, because Render asks for its values.

### 4.B Database — Aiven (~5 min)
1. https://aiven.io → **Get started for free** → sign up (GitHub works). Name the organization/project `unitrade` if asked.
2. **Create service** → **MySQL** → plan **Free** → a European region if offered (Render runs in Frankfurt), otherwise any → service name `unitrade-db` → **Create free service**.
3. Wait for **Running**. Open the service → **Overview → Connection information**: note **Host**, **Port**, **User** (`avnadmin`), **Password**, **Database name** (`defaultdb`).
4. Keep the IP allow-list at its default (open); Render's free outbound addresses are not fixed.

### 4.C API service — Render (~10 min, mostly waiting)
**New → Web Service**, then every field:

| Field | Value |
|---|---|
| Source Code | Git Provider → `shibambocollins/UniTrade` |
| Name | `unitrade-cput-api` (this gives https://unitrade-cput-api.onrender.com; if Render shows a different address, put that address in `frontend/.env.production` and push) |
| Project | leave empty |
| Language | **Docker** |
| Branch | `main` |
| Region | **Frankfurt (EU Central)** |
| Root Directory | `backend` |
| Instance Type | **Free** |

**Environment Variables** (+ Add Environment Variable), five in total:

| Key | Value |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `DB_URL` | `jdbc:mysql://HOST:PORT/defaultdb?sslMode=REQUIRED&serverTimezone=UTC` (Host and Port from 4.B) |
| `DB_USERNAME` | `avnadmin` |
| `DB_PASSWORD` | the Aiven password |
| `JWT_SECRET` | click **Generate** next to the value field (Render creates a random value). Without that button, run the PowerShell line below and paste (Ctrl+V); it is copied to the clipboard and never shown. |

```powershell
$b = New-Object byte[] 32; [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b); [Convert]::ToBase64String($b) | Set-Clipboard
```

**Common mistakes on this form (all made once on 2026-10-08):**
- `DB_URL` set to Aiven's **Service URI** (`mysql://avnadmin:PASSWORD@host:port/defaultdb?ssl-mode=REQUIRED`). Wrong: Java needs the `jdbc:mysql://HOST:PORT/defaultdb?sslMode=REQUIRED&serverTimezone=UTC` form, with no user or password inside.
- `DB_USERNAME` set to `defaultdb`. That is the database name; the user is `avnadmin`.
- **Dockerfile Path** left as `.` (shows `backend/ .`). It must be `./Dockerfile`; `.` is a folder and the build fails.
- A screenshot or chat message showing the Aiven password. If that happens, reset it in Aiven (↻ next to Password) and paste the new one into `DB_PASSWORD`.

**Advanced:**

| Field | Value |
|---|---|
| Health Check Path | `/api/health` |
| Dockerfile Path | `./Dockerfile` (shown after a `backend/` prefix; if there is no prefix, `backend/Dockerfile`) |
| Docker Build Context Directory | `.` (after the `backend/` prefix; if there is no prefix, `backend`) |
| Auto-Deploy | **On Commit** |
| Docker Command, Pre-Deploy Command, Secret Files, Disk, Registry Credential, Build Filters | leave empty |

**Deploy Web Service** → open **Logs**: build ~5 min, then `Started UnitradeApplication` (~1.5 min) and "Your service is live". Open https://unitrade-cput-api.onrender.com/api/health → `{"status":"UP","database":"UP",...}`. Then run section 5 and update section 1.

*Alternative:* **New → Blueprint** → **Connect** `UniTrade` → name `unitrade`, branch `main` → fill `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` (`JWT_SECRET` is generated automatically) → **Deploy Blueprint**. [`render.yaml`](../render.yaml) holds the same settings as the table above.

## 5. Check a deployment
```
node scripts/check-deploy.mjs --web https://uni-trade-eight.vercel.app --api https://unitrade-cput-api.onrender.com
```
Checks API + database health, that the site and deep links load, that the website build points at the API, and that CORS allows the site and refuses others. Every failure prints what to fix. Anyone can run it; no account needed.
Verified on 2026-10-08 against a local copy of the same setup: all 6 checks passed; a wrong API address was reported as a failure. Production mode (`prod` profile, secrets from environment variables only) was also started against a real MySQL: database UP, Vercel origin allowed, `localhost` refused.

## 6. Day-to-day (no dashboard access needed)
- `git push` to `main` → Vercel redeploys; Render redeploys when files under `backend/` change.
- Before pushing backend changes: tests pass (`.\mvnw.cmd test`), and optionally the container starts with Render's limits:
  `cd backend; docker build -t unitrade-api:local .` then
  `docker run --rm -p 8081:8080 -m 512m --cpus 0.1 -e SPRING_PROFILES_ACTIVE=h2 unitrade-api:local` → http://localhost:8081/api/health
- After pushing: CI status on GitHub (Actions tab), then `scripts/check-deploy.mjs` (section 5) once Render has redeployed (~7 min).

## 7. If someone else needs to see logs or change a secret
The Vercel account belongs to Collins; the Render and Aiven accounts belong to whoever creates them in section 4. Without sharing passwords, an account owner can give another person **revocable tokens**, sent privately (never in git or a chat with Claude):
- **Render:** Account Settings → **API Keys** → Create. With it, Claude Code can read logs, change environment variables and trigger deploys through Render's API (no plugin needed).
- **Vercel:** Account Settings → **Tokens** → Create (scope: the `uni-trade` project's team). Usable with `npx vercel --token ...`.
- Store a token in a file outside the repo (e.g. `C:\Users\<you>\unitrade-deploy\render-key.txt`) and tell Claude the path, not the value.
The owner can delete the tokens after the project is handed in.

## 8. Troubleshooting
| Symptom | Likely cause | Fix |
|---|---|---|
| Page says "Still working… server may be waking up" for 1–3 min | Render free service was asleep | Wait; warm it up before demos |
| Page says "Cannot reach the UniTrade server" | API not deployed yet, failed deploy, or wrong address in `frontend/.env.production` | `check-deploy.mjs` shows which; fix the address by commit |
| Browser console: "blocked by CORS policy" | Site address not in `app.cors.allowed-origins` | Edit `application-prod.properties` and push |
| Render log: `missing environment variable(s) [...]` | A secret was not set | Add it in Render → Environment (section 4.C) |
| `/api/health` shows `"database":"DOWN"`, or log `Communications link failure` | Aiven service powered off, or wrong `DB_URL` | Power on in Aiven; `DB_URL` must include `sslMode=REQUIRED` |
| Render log: `Access denied for user` | Wrong `DB_USERNAME`/`DB_PASSWORD` (the user is `avnadmin`, not the database name) | Copy them again from Aiven |
| Render log mentions the URL / `No suitable driver` | `DB_URL` was set to Aiven's **Service URI** (`mysql://avnadmin:...@host...`) | Use the JDBC form `jdbc:mysql://HOST:PORT/defaultdb?sslMode=REQUIRED&serverTimezone=UTC` (no user/password inside) |
| Render log: `Unable to create or change a table without a primary key` | An entity/join table without a primary key (Aiven rule) | Add an `@Id` / use a `Set` for `@ManyToMany` |
| Render deploy fails during build | Backend doesn't compile or Dockerfile changed | Run the local `docker build` (section 6) to see the error |
