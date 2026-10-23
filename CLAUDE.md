# Admin logic & how to add an admin (Nashvilles-Line-Dancing, OKD)

> Note: this documents the **Nashvilles-Line-Dancing** Flask app, not Javalanche.
> Saved here at James's request. Source paths below are in that project
> (`/Users/james/School/Other/Nashvilles-Line-Dancing`).

## How admins are determined (post-EC2→OKD migration)

The old EC2/compose stack read admins from an `admin_credentials.txt` file baked
into the image. **That is no longer the source of truth on OKD.**

`load_admin_users()` in `app.py:31`:

- Prefers the **`ADMIN_USERS`** env var (comma-separated usernames).
- Falls back to `admin_credentials.txt` **only when `ADMIN_USERS` is unset**
  (legacy compose; the container FS isn't writable by the assigned UID on OKD anyway).

On OKD, `ADMIN_USERS` is set from a Secret — `nld-secrets`, key `admin-users`
(`k8s/manifest.yaml:171`). So the **Secret is the source of truth**; the txt file
is dead weight there.

Two separate mechanisms:

1. **Who is treated as admin** is computed *live on every login and session load*:
   `'admin' if email in admin_list else 'user'` (`app.py:316` and `app.py:334`).
   `admin_list` is loaded **once at process start** from `ADMIN_USERS`.
2. **Startup seeding** (`app.py:142`) only *creates* an account for an admin who
   has **no DB row yet**; it skips anyone who already exists. Bootstrap only, not
   the admin gate.

Key consequence: a running process will **not** notice a Secret change until it
**restarts** — but a restart is all it takes. **No image rebuild is needed.**

## Adding an admin (e.g. kathy) via the OKD web console

Administrator perspective, the app's project:

1. **Workloads → Secrets → `nld-secrets`** → Actions → **Edit Secret**. Add the
   username to the `admin-users` value, comma-separated (e.g. `alice,bob,kathy`).
   Save.
2. **Workloads → Deployments → [web/app deployment]** → Actions → **Restart
   rollout**. This re-runs `load_admin_users()`, so the new name enters
   `admin_list`.

Then, depending on whether the user already has an account:

- **Already has an account** → nothing else needed. On next login they get the
  admin role; existing password unchanged.
- **No account yet** → the startup seeder creates one. Set
  **`admin-initial-password`** in `nld-secrets` first for a known password
  (`app.py:147`); otherwise a random one is generated and printed **only** to the
  pod log (**Workloads → Pods → [app pod] → Logs**, look for "Generated password").

Caveat: `admin-initial-password` only seeds admins with **no row yet**. If the
user already has a non-admin row, seeding won't touch them — that's fine, because
the live role check (after the restart) promotes them regardless of how the row
was created.

## Where the DB password lives (Crunchy PGO)

Not in the repo. PGO generates it and stores it in the Secret **`nld-db-pguser-nld`**
(the app reads `DATABASE_URL` from its `pgbouncer-uri` key, `k8s/manifest.yaml:159`).

- **Console:** Workloads → Secrets → `nld-db-pguser-nld` → **Reveal values**.
  Useful keys: `password` (raw), `user`, `dbname`, `host`, `port`, `uri` (DSN to
  primary), `pgbouncer-uri` (DSN via pgBouncer).
- **From the primary Postgres pod:** local trust auth, so no password needed —
  `psql -U nld -d nld`.
- **From the app pod:** `echo "$DATABASE_URL"` (pgbouncer DSN with the password embedded).
