# jhipsterSampleApplication — JHipster + MCPHub tenant

A [JHipster 9](https://www.jhipster.tech/documentation-archive/v9.0.0) application (Spring Boot 4 + Angular) that also acts as an **MCPHub `TENANT_DELEGATED` tenant**: MCPHub sends users to this app's login page, then exchanges and validates sessions through HMAC-signed API calls.

This guide covers four ways to run it:

| Option                                                               | Use it for                               | Database                      | URL                              |
| -------------------------------------------------------------------- | ---------------------------------------- | ----------------------------- | -------------------------------- |
| [1. Run locally](#1-run-locally)                                     | Day-to-day development                   | H2 in-memory                  | http://localhost:8085            |
| [2. Docker Compose](#2-run-with-docker-compose)                      | Production-like run on your machine      | PostgreSQL container          | http://localhost:8080            |
| [3. Render](#3-deploy-to-render)                                     | Hosted deployment                        | Render PostgreSQL             | `https://<service>.onrender.com` |
| [4. Cloudflare Tunnel](#4-expose-a-local-app-with-cloudflare-tunnel) | Making a local app reachable from MCPHub | (whatever is running locally) | `https://<your-host>`            |

---

## Contents

- [Prerequisites](#prerequisites)
- [Project setup](#project-setup)
- [Configuration](#configuration)
- [1. Run locally](#1-run-locally)
- [2. Run with Docker Compose](#2-run-with-docker-compose)
- [3. Deploy to Render](#3-deploy-to-render)
- [4. Expose a local app with Cloudflare Tunnel](#4-expose-a-local-app-with-cloudflare-tunnel)
- [Registering the app in MCPHub](#registering-the-app-in-mcphub)
- [Users, roles, and MCP tool scopes](#users-roles-and-mcp-tool-scopes)
- [Common commands](#common-commands)
- [Troubleshooting](#troubleshooting)
- [Security notes](#security-notes)
- [Testing](#testing)
- [References](#references)

---

## Prerequisites

| Tool                                                                                                    | Version            | Needed for                                                                                    |
| ------------------------------------------------------------------------------------------------------- | ------------------ | --------------------------------------------------------------------------------------------- |
| [Git](https://git-scm.com/)                                                                             | any                | Cloning                                                                                       |
| [JDK](https://adoptium.net/)                                                                            | **21**             | Options 1 and the Jib image build                                                             |
| [Node.js](https://nodejs.org/)                                                                          | ≥ 24.14 (optional) | Only if you run `npm` directly. Maven downloads its own Node 24.14 / npm 11.11 into `target/` |
| [Docker](https://docs.docker.com/get-docker/) + Compose v2                                              | recent             | Option 2 (and building the image yourself)                                                    |
| [cloudflared](https://developers.cloudflare.com/cloudflare-one/connections/connect-networks/downloads/) | recent             | Option 4                                                                                      |
| A [Render](https://render.com/) account                                                                 | —                  | Option 3                                                                                      |

You do **not** need to install Maven: the repo ships the Maven wrapper (`./mvnw`, or `.\mvnw.cmd` on Windows PowerShell/cmd).

> **Windows:** the examples use bash syntax. In **Git Bash** they work as written. In **PowerShell**, use `.\mvnw.cmd` instead of `./mvnw`, `.\npmw.cmd` instead of `./npmw`, and `$env:NAME = "value"` instead of `export NAME=value`.

---

## Project setup

```bash
git clone https://github.com/aircwou/jhipster-mcp-demo.git
cd jhipster-mcp-demo

# Optional: install client dependencies up front (Maven also does this on first build).
# CYPRESS_INSTALL_BINARY=0 skips the ~500 MB Cypress download if you don't run e2e tests.
CYPRESS_INSTALL_BINARY=0 ./npmw install
```

The first build downloads Maven dependencies, Node, and npm packages, so it takes a few minutes. Later builds are much faster.

---

## Configuration

### Spring profiles

| Profile          | Activated by                  | What it does                                                                                                                                      |
| ---------------- | ----------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------- |
| `dev`            | Default for `./mvnw`, `-Pdev` | H2 in-memory DB, port **8085**, debug logging. Also pulls in `secret-samples` and `api-docs`                                                      |
| `secret-samples` | Included by `dev`             | Supplies the JWT secret and the MCPHub settings from [`application-secret-samples.yml`](src/main/resources/config/application-secret-samples.yml) |
| `prod`           | `-Pprod`, the Docker image    | PostgreSQL, port **8080**, all secrets **must** come from environment variables                                                                   |

In `dev`, values in `application-secret-samples.yml` **override** the `MCP_CONTRACT_*` environment variables (the profile file wins over the `${…}` placeholders in `application.yml`). To change them in dev, edit that file, or use Spring's own variable names, which take priority over YAML files: e.g. `MCP_CONTRACT_ALLOWEDCALLBACKHOSTS` (no underscores between words).

### Environment variables (prod / Docker / Render)

| Variable                                                    | Required           | Description                                                                                                                                                                  |
| ----------------------------------------------------------- | ------------------ | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `JHIPSTER_SECURITY_AUTHENTICATION_JWT_BASE64_SECRET`        | **Yes**            | Base64 of ≥ 64 random bytes; signs user JWTs (HS512).                                                                                                                        |
| `MCP_CONTRACT_HMAC_KEY`                                     | **Yes**            | Exactly 64 hex characters. Must equal the key configured for this tenant in MCPHub. The app refuses to start in prod if it is missing, malformed, or the sample placeholder. |
| `MCP_CONTRACT_ALLOWED_CALLBACK_HOSTS`                       | Yes, for MCP login | Comma-separated **host names** MCPHub may redirect back to, e.g. `lockmcp.ai,www.lockmcp.ai`. Host only: no `https://` and no path.                                          |
| `MCP_CONTRACT_DOMAIN_VERIFICATION_TOKEN`                    | No                 | Served at `/.well-known/mcp-hub-verification.json` for domain verification.                                                                                                  |
| `MCP_CONTRACT_SESSION_TTL_SEC`                              | No                 | Session lifetime, default `3600`.                                                                                                                                            |
| `MCP_CONTRACT_AUTH_CODE_TTL_SEC`                            | No                 | Auth-code lifetime, default `60`.                                                                                                                                            |
| `SPRING_DATASOURCE_URL`                                     | Yes (prod)         | JDBC URL, e.g. `jdbc:postgresql://host:5432/dbname`.                                                                                                                         |
| `SPRING_DATASOURCE_USERNAME` / `SPRING_DATASOURCE_PASSWORD` | Yes (prod)         | Database credentials.                                                                                                                                                        |
| `PORT`                                                      | No                 | Port the Docker image listens on (default `8080`). Render sets this automatically.                                                                                           |

Generate secrets:

```bash
openssl rand -base64 64 | tr -d '\n'   # JHIPSTER_SECURITY_AUTHENTICATION_JWT_BASE64_SECRET
openssl rand -hex 32                   # MCP_CONTRACT_HMAC_KEY (only if you are creating the key; otherwise copy it from MCPHub)
```

For Docker Compose, put these in a `.env` file. Start from the template; `.env` is git-ignored:

```bash
cp .env.example .env
```

---

## 1. Run locally

Uses the `dev` profile: an in-memory H2 database (data, including MCP sessions, is lost on restart) and sample users loaded at startup.

### Option A: single process (simplest)

Builds the Angular client once and serves everything from Spring Boot:

```bash
CYPRESS_INSTALL_BINARY=0 ./mvnw spring-boot:run -Pdev,webapp -ntp
```

PowerShell:

```powershell
$env:CYPRESS_INSTALL_BINARY = "0"; .\mvnw.cmd spring-boot:run "-Pdev,webapp" -ntp
```

Open **http://localhost:8085** and sign in with `admin` / `admin` or `user` / `user`.

### Option B: live reload for front-end work

Run the back end and the Angular dev server in two terminals:

```bash
./npmw run backend:start   # Spring Boot on :8085 (skips the client build)
./npmw start               # Angular dev server, proxies API calls to :8085
```

Open **http://localhost:9000**. The browser refreshes when you change client files.

### Option C: production profile on your machine

```bash
./npmw run docker:db:up                      # PostgreSQL in Docker on 127.0.0.1:5432
./mvnw -Pprod -DskipTests -ntp clean verify  # builds target/*.jar

export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/jhipsterSampleApplication
export SPRING_DATASOURCE_USERNAME=jhipsterSampleApplication
export JHIPSTER_SECURITY_AUTHENTICATION_JWT_BASE64_SECRET="$(openssl rand -base64 64 | tr -d '\n')"
export MCP_CONTRACT_HMAC_KEY=<64-hex-key>
export MCP_CONTRACT_ALLOWED_CALLBACK_HOSTS=lockmcp.ai,www.lockmcp.ai

java -jar target/*.jar                       # http://localhost:8080
```

---

## 2. Run with Docker Compose

[`src/main/docker/app.yml`](src/main/docker/app.yml) starts the app (`prod` profile) and PostgreSQL.

**Step 1: create `.env`** with at least the two required secrets (see [Configuration](#environment-variables-prod--docker--render)):

```bash
cp .env.example .env
# edit .env
```

**Step 2: build the image** (either way produces `jhipstersampleapplication:latest`):

```bash
# Option 1: Dockerfile. Needs only Docker.
docker build -t jhipstersampleapplication .

# Option 2: Jib. Needs JDK 21, no Dockerfile involved.
./npmw run java:docker            # amd64
./npmw run java:docker:arm64      # Apple Silicon / arm64
```

**Step 3: start it.** Pass `--env-file .env`. Compose does **not** pick up the repo-root `.env` automatically when the compose file is in another folder.

```bash
docker compose --env-file .env -f src/main/docker/app.yml up -d --wait
```

Open **http://localhost:8080**. `--wait` returns once the app's health check (`/management/health`) passes. First start takes about 30 to 60 seconds while Liquibase creates the schema.

```bash
docker compose --env-file .env -f src/main/docker/app.yml logs -f app   # follow logs
docker compose --env-file .env -f src/main/docker/app.yml down          # stop
docker compose --env-file .env -f src/main/docker/app.yml down -v       # stop and delete the database
```

Notes:

- Ports are bound to `127.0.0.1` only. Use [Cloudflare Tunnel](#4-expose-a-local-app-with-cloudflare-tunnel) to expose the app.
- The PostgreSQL container uses `trust` auth and has no volume by default. Fine locally, **not** for production (see the comments in [`postgresql.yml`](src/main/docker/postgresql.yml)).
- Only need the database (e.g. for local Option C)? `./npmw run docker:db:up` / `./npmw run docker:db:down`.

---

## 3. Deploy to Render

Render has no native Java runtime, so the service is built from the repo's [`Dockerfile`](Dockerfile). It compiles the client and server inside Docker and listens on Render's `PORT`.

### Step 1: create the database

1. Render Dashboard → **New** → **Postgres**.
2. Pick a name and a **region**. Use the same region for the web service so they can talk over the private network.
3. Create it, then open **Connections** and note **Hostname** (internal), **Port**, **Database**, **Username**, and **Password**.

### Step 2: create the web service

1. Push this repo to GitHub/GitLab. Render deploys from your remote, not from your machine.
2. Render Dashboard → **New** → **Web Service** → connect the repository.
3. Settings:
   - **Language / Runtime:** `Docker`
   - **Branch:** `main`
   - **Dockerfile Path:** `./Dockerfile` (Root Directory: leave empty)
   - **Region:** same as the database
   - **Instance type:** at least **512 MB RAM**. The free tier works for a demo but sleeps after inactivity (see [Troubleshooting](#troubleshooting)).
4. **Environment Variables:** add these. Use the internal hostname from Step 1:

   | Key                                                  | Value                                                   |
   | ---------------------------------------------------- | ------------------------------------------------------- |
   | `SPRING_DATASOURCE_URL`                              | `jdbc:postgresql://<internal-hostname>:5432/<database>` |
   | `SPRING_DATASOURCE_USERNAME`                         | `<username>`                                            |
   | `SPRING_DATASOURCE_PASSWORD`                         | `<password>`                                            |
   | `JHIPSTER_SECURITY_AUTHENTICATION_JWT_BASE64_SECRET` | output of `openssl rand -base64 64 \| tr -d '\n'`       |
   | `MCP_CONTRACT_HMAC_KEY`                              | the 64-hex key from MCPHub                              |
   | `MCP_CONTRACT_ALLOWED_CALLBACK_HOSTS`                | e.g. `lockmcp.ai,www.lockmcp.ai`                        |
   | `MCP_CONTRACT_DOMAIN_VERIFICATION_TOKEN`             | (optional) token from MCPHub                            |

   Render shows a _postgresql://…_ URL. Spring needs the **`jdbc:postgresql://`** form with the username and password in separate variables, so don't paste the Render URL as-is.

   Don't set `PORT`. Render provides it and the image binds to it.

5. **Advanced → Health Check Path:** `/management/health`
6. Click **Create Web Service**. The first build takes several minutes (Maven + Angular production build). Watch the **Logs** tab until you see `Application 'jhipsterSampleApplication' is running!`.

### Step 3: after the first deploy

- Your app is at `https://<service-name>.onrender.com`. Optionally add a custom domain under **Settings → Custom Domains**.
- **Change the `admin` and `user` passwords immediately.** The sample accounts are created in every profile.
- Register the public URL in MCPHub (see [Registering the app in MCPHub](#registering-the-app-in-mcphub)).
- Every push to `main` triggers a redeploy (Auto-Deploy is on by default).

---

## 4. Expose a local app with Cloudflare Tunnel

Use this when MCPHub (or anyone else) needs to reach an app running on your machine. The tunnel connects outbound to Cloudflare, so there are no open ports and no port forwarding.

Pick the local port that matches how you run the app:

| How it runs                        | Local URL to expose     |
| ---------------------------------- | ----------------------- |
| Option 1A / 1B (dev)               | `http://localhost:8085` |
| Option 1C or Docker Compose (prod) | `http://localhost:8080` |

### Install cloudflared

```bash
# Windows
winget install --id Cloudflare.cloudflared

# macOS
brew install cloudflared

# Debian / Ubuntu (amd64)
curl -L -o cloudflared.deb https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-amd64.deb
sudo dpkg -i cloudflared.deb

cloudflared --version
```

### Quick tunnel (no account, random URL)

```bash
cloudflared tunnel --url http://localhost:8085
```

`cloudflared` prints a URL like `https://<random-words>.trycloudflare.com`. It changes every time you restart, so it's good for a quick test and bad for anything you register in MCPHub.

### Named tunnel on your own domain (stable URL)

Requires a domain whose DNS is managed by Cloudflare. The example uses `jhipster-raw.example.com`; replace it with yours.

```bash
# 1. Authenticate. Opens a browser; pick the zone (domain). Saves ~/.cloudflared/cert.pem
cloudflared tunnel login

# 2. Create the tunnel. Prints a tunnel UUID and writes ~/.cloudflared/<UUID>.json
cloudflared tunnel create jhipster-raw

# 3. Point a hostname at it (creates a proxied CNAME in Cloudflare DNS)
cloudflared tunnel route dns jhipster-raw jhipster-raw.example.com
```

4. Create `~/.cloudflared/config.yml` (Windows: `%USERPROFILE%\.cloudflared\config.yml`):

```yaml
tunnel: jhipster-raw
credentials-file: /home/<you>/.cloudflared/<UUID>.json # Windows: C:\Users\<you>\.cloudflared\<UUID>.json
ingress:
  - hostname: jhipster-raw.example.com
    service: http://localhost:8085
  - service: http_status:404
```

5. Validate and run:

```bash
cloudflared tunnel ingress validate
cloudflared tunnel run jhipster-raw
```

The app is now at `https://jhipster-raw.example.com`. Cloudflare terminates TLS; your app still serves plain HTTP locally. Keep the terminal open, or install it as a background service with `cloudflared service install` (see [Cloudflare's docs](https://developers.cloudflare.com/cloudflare-one/connections/connect-networks/configure-tunnels/local-management/as-a-service/)).

> **Dashboard-managed alternative:** in Cloudflare **Zero Trust → Networks → Tunnels**, create a tunnel, add a _Public Hostname_ pointing to `http://localhost:8085`, and run the command it shows: `cloudflared tunnel run --token <TOKEN>`.

The **app** must be running before traffic arrives. If it isn't, Cloudflare returns a `502 Bad Gateway`.

---

## Registering the app in MCPHub

Replace `https://<your-host>` with your Render URL or tunnel hostname.

| MCPHub field        | URL                                                          | Method            |
| ------------------- | ------------------------------------------------------------ | ----------------- |
| Login URL           | `https://<your-host>/mcp-login` (dash, **not** `/mcp/login`) | GET (browser)     |
| Exchange URL        | `https://<your-host>/api/mcp/exchange`                       | POST, HMAC-signed |
| Validate URL        | `https://<your-host>/api/mcp/validate`                       | POST, HMAC-signed |
| Refresh             | `https://<your-host>/api/mcp/refresh`                        | POST, HMAC-signed |
| Logout              | `https://<your-host>/api/mcp/logout`                         | POST, HMAC-signed |
| Users directory     | `https://<your-host>/api/mcp/users`                          | GET, HMAC-signed  |
| Roles directory     | `https://<your-host>/api/mcp/roles`                          | GET, unsigned     |
| Domain verification | `https://<your-host>/.well-known/mcp-hub-verification.json`  | GET               |

Checklist:

- The HMAC key in MCPHub and in the app are **identical**.
- The host of MCPHub's `callback_url` is listed in `MCP_CONTRACT_ALLOWED_CALLBACK_HOSTS`.
- The OpenAPI contract the hub uses is [`mcp-openapi.yaml`](mcp-openapi.yaml).

---

## Users, roles, and MCP tool scopes

### Sample accounts

Created by Liquibase on first start, in **every** profile (dev, Docker, Render):

| Login   | Password | Email             | Roles                     |
| ------- | -------- | ----------------- | ------------------------- |
| `admin` | `admin`  | `admin@localhost` | `ROLE_ADMIN`, `ROLE_USER` |
| `user`  | `user`   | `user@localhost`  | `ROLE_USER`               |

### Roles

| Role           | Exists in the database? | In the app                                                                                       | MCP tool scopes (from `application.yml`)                                                                          |
| -------------- | ----------------------- | ------------------------------------------------------------------------------------------------ | ----------------------------------------------------------------------------------------------------------------- |
| `ROLE_ADMIN`   | Yes (seeded)            | Full access, including the **Administration** menu (user management, authorities, metrics, logs) | All 31 operations: account, users, bank accounts, labels, operations, authorities                                 |
| `ROLE_USER`    | Yes (seeded)            | Normal user: own account plus bank accounts, labels, operations                                  | `getAccount`, `saveAccount`, `changePassword`, `getAllUsers`, `createUser`, `updateUser`, `getUser`, `deleteUser` |
| `ROLE_MANAGER` | **No**                  | Not usable until you create it (see below)                                                       | `getAccount`, `saveAccount`, `changePassword`, `getAllUsers`                                                      |

How the scopes work:

- The scopes are configured under `mcp.contract.tool-scopes` in [`application.yml`](src/main/resources/config/application.yml), keyed by role name. Each name is an `operationId` from [`mcp-openapi.yaml`](mcp-openapi.yaml).
- A user's `toolScopes`, returned by `/api/mcp/exchange` and `/api/mcp/validate`, is the **union** of the scopes of all their roles. For example, `admin` gets the `ROLE_ADMIN` and `ROLE_USER` scopes combined.
- `/api/mcp/roles` lists the roles that **exist in the database** with their scopes. Roles that appear only in `application.yml` (like `ROLE_MANAGER` today) are not reported to MCPHub.
- **Scopes are not permissions.** The app still enforces its own access rules. The user-management operations (`getAllUsers`, `createUser`, `updateUser`, `getUser`, `deleteUser`) call `/api/admin/users`, which requires `ROLE_ADMIN`. A `ROLE_USER` or `ROLE_MANAGER` session is offered those tools but gets **`403 Forbidden`** when it calls them. Remove them from those roles if you don't want MCPHub to offer them.

### Adding a role (e.g. `ROLE_MANAGER`)

1. **Create the role.** Sign in as `admin`, go to **Administration → Authority** (`/authority`), and create `ROLE_MANAGER`. Or call the API:
   ```bash
   curl -X POST http://localhost:8085/api/authorities \
     -H "Authorization: Bearer <admin JWT>" -H "Content-Type: application/json" \
     -d '{"name":"ROLE_MANAGER"}'
   ```
   Don't edit `liquibase/data/authority.csv` on an existing database. Liquibase has already applied it, and changing the file makes startup fail with a checksum error.
2. **Assign it.** Go to **Administration → User management → Edit user → Profiles**, and tick the role.
3. **Set its tool scopes** in `application.yml` under `tool-scopes`. Keep the bracket-and-quotes form, which preserves the exact key:
   ```yaml
   '[ROLE_MANAGER]':
     - getAccount
     - getAllBankAccounts
   ```
   Restart the app.
4. **Re-import roles in MCPHub** so it picks up the new role from `/api/mcp/roles`.

In dev, the H2 database is in-memory. Roles and users created through the UI or API disappear on restart, but changes to `application.yml` stay.

---

## Common commands

| Task                            | Command                                                                  |
| ------------------------------- | ------------------------------------------------------------------------ |
| Run dev (single process)        | `./mvnw spring-boot:run -Pdev,webapp -ntp`                               |
| Run back end only (dev)         | `./npmw run backend:start`                                               |
| Run Angular dev server          | `./npmw start`                                                           |
| Build production jar            | `./mvnw -Pprod -DskipTests -ntp clean verify`                            |
| Build Docker image (Dockerfile) | `docker build -t jhipstersampleapplication .`                            |
| Build Docker image (Jib)        | `./npmw run java:docker`                                                 |
| Start app + DB in Docker        | `docker compose --env-file .env -f src/main/docker/app.yml up -d --wait` |
| Start only PostgreSQL           | `./npmw run docker:db:up`                                                |
| Back-end tests                  | `./mvnw verify`                                                          |
| Front-end tests                 | `./npmw test`                                                            |
| Lint client                     | `./npmw run lint`                                                        |
| Health check                    | `curl http://localhost:8080/management/health`                           |

---

## Troubleshooting

| Symptom                                                                                      | Cause / fix                                                                                                                                                                                                                                                                                                |
| -------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `MCP_CONTRACT_HMAC_KEY is not set` / `…must be exactly 64 hexadecimal characters` at startup | Running a non-dev profile without a valid key. Set it to 64 hex chars (no quotes, no spaces).                                                                                                                                                                                                              |
| `Could not resolve placeholder 'jhipster.security.authentication.jwt.base64-secret'`         | `prod` profile without `JHIPSTER_SECURITY_AUTHENTICATION_JWT_BASE64_SECRET`.                                                                                                                                                                                                                               |
| `required variable … is missing a value: set it in .env`                                     | You ran Docker Compose without `--env-file .env`, or the value is empty in `.env`.                                                                                                                                                                                                                         |
| MCP login page says _"The callback URL is not permitted for this tenant."_                   | The `callback_url` host isn't in `MCP_CONTRACT_ALLOWED_CALLBACK_HOSTS`. List host names only (`lockmcp.ai`, not `https://lockmcp.ai/...`). In dev, edit `application-secret-samples.yml`.                                                                                                                  |
| Exchange/validate return `401 unauthorized`                                                  | Signature check failed. Either (a) the key differs from MCPHub's; (b) the URL registered in MCPHub differs from the one the request arrives on (scheme, host, and path are all signed, e.g. `www.` vs bare domain); or (c) the server clock is off, since requests outside the replay window are rejected. |
| `/mcp/login` shows the normal JHipster UI                                                    | Wrong path. It's `/mcp-login`.                                                                                                                                                                                                                                                                             |
| `Port 8085 (or 8080) was already in use`                                                     | Stop the other process, or change `server.port` (dev: `application-dev.yml`; also update `webpack/proxy.conf.js`).                                                                                                                                                                                         |
| Docker: `Bind for 127.0.0.1:5432 failed: port is already allocated` (or `:8080`)             | Another container or a local PostgreSQL is using the port. Stop it, or change the host side of `ports:` in `src/main/docker/app.yml` / `postgresql.yml` (e.g. `127.0.0.1:18080:8080`).                                                                                                                     |
| `/bin/sh^M: bad interpreter` running `./mvnw` in Linux/WSL                                   | Windows line endings: `sed -i 's/\r$//' mvnw`. The Dockerfile already does this.                                                                                                                                                                                                                           |
| npm install is very slow or fails downloading Cypress                                        | Set `CYPRESS_INSTALL_BINARY=0`.                                                                                                                                                                                                                                                                            |
| Cloudflare `502 Bad Gateway` / `Unable to reach the origin service`                          | App not running, or the tunnel points at the wrong port (8085 dev vs 8080 prod).                                                                                                                                                                                                                           |
| Render deploy fails with _No open ports detected_ or health check failures                   | The app is still starting (Liquibase + JVM). Check the logs for the real error, usually a missing env var or a wrong `SPRING_DATASOURCE_URL` (must start with `jdbc:postgresql://`).                                                                                                                       |
| Render: `Connection refused` / `UnknownHostException` to the database                        | Web service and database are in different regions, or you used the external hostname without SSL. Use the **internal** hostname in the same region.                                                                                                                                                        |
| Render free tier: first request after idle takes about a minute                              | Free services spin down when idle. MCPHub calls during spin-up may time out; use a paid instance for anything real.                                                                                                                                                                                        |
| Render build killed / out of memory                                                          | The Angular production build is memory-hungry. Retry, or use a larger instance type.                                                                                                                                                                                                                       |
| `git push` → `403 Permission … denied to <user>`                                             | Git is signed in as a GitHub account without write access to the remote. Get added as a collaborator, or sign in with the right account.                                                                                                                                                                   |

---

## Security notes

- **Never commit real secrets.** `application-secret-samples.yml` is for local development only. In prod, use environment variables (`.env` locally, the Render dashboard on Render). `.env` is git-ignored.
- If a real HMAC key or JWT secret was ever committed or pushed, **rotate it**. Removing it in a later commit does not remove it from git history.
- The sample accounts `admin/admin` and `user/user` exist in every profile. Change those passwords on any publicly reachable deployment, including Cloudflare quick tunnels.
- `/api/mcp/roles` and `/.well-known/mcp-hub-verification.json` are intentionally unauthenticated. Every other MCP back-channel endpoint requires a valid HMAC signature.

---

## Testing

```bash
./mvnw verify          # Spring Boot unit + integration tests
./npmw test            # Angular unit tests (Vitest)
./npmw run e2e         # Cypress e2e; start the app first with ./npmw run app:start
./mvnw gatling:test    # Gatling performance tests
```

For e2e, set `CYPRESS_E2E_USERNAME` / `CYPRESS_E2E_PASSWORD` to override the default credentials.

---

## References

- [JHipster 9.0.0 documentation](https://www.jhipster.tech/documentation-archive/v9.0.0)
- [Using JHipster in development](https://www.jhipster.tech/documentation-archive/v9.0.0/development/) / [in production](https://www.jhipster.tech/documentation-archive/v9.0.0/production/)
- [Docker and Docker Compose with JHipster](https://www.jhipster.tech/documentation-archive/v9.0.0/docker-compose)
- [Render: Docker deploys](https://render.com/docs/docker) / [Environment variables](https://render.com/docs/configure-environment-variables) / [Postgres](https://render.com/docs/postgresql)
- [Cloudflare Tunnel documentation](https://developers.cloudflare.com/cloudflare-one/connections/connect-networks/)
