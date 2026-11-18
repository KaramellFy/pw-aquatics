# PW Aquatics — Running the shop

How to put the shop on a server, keep it running, back it up and get it back after a problem.
Written for whoever looks after the server; the owner should keep a copy of the secrets
(§3.2) in a password manager.

Placeholders: `shop.example` is the shop's domain, `mail.example` the mail provider.

---

## 1. What runs where

One server runs everything with Docker Compose (`docker-compose.prod.yml`):

| Service | What it does | Reachable from outside |
|---|---|---|
| `edge` | Caddy: HTTPS certificates from Let's Encrypt (renewed on its own), HTTP → HTTPS, HSTS; passes `/api` and `/media` to the API and everything else to `frontend` | Ports 80 and 443 only |
| `frontend` | Caddy with the built app and its security headers (CSP); asks the API for the first page load of public pages | No |
| `backend` | The Spring Boot API; runs database migrations on start; sends emails through the provider's SMTP server | No |
| `postgres` | PostgreSQL 18; data in the volume `postgres-data` | No |
| `backup` | Every night at `BACKUP_TIME`: database dump and photo archive into `./backups` | No |
| `restore` | Only started by hand to restore a backup (§6.4) | — |

Volumes: `postgres-data` (database), `media` (product and gallery photos), `caddy-data`
(certificates — keep it, or Let's Encrypt's limits may block new ones for a while).

Logs: each service keeps at most 5 files of 10 MB (`docker compose logs`). Caddy writes no
access log, so no visitor IP addresses are kept. The application logs no personal data.

---

## 2. The server

- **A Linux VPS in the EU** (data protection: customer data stays in the EU), e.g. Debian 13
  or Ubuntu 24.04 LTS, **2 vCPU, 4 GB RAM, 40 GB disk** to start. Photos and backups grow the
  disk; watch it (§7).
- **DNS:** an `A` record (and `AAAA` for IPv6) for `shop.example` pointing at the server, before
  the first start — Caddy needs it to get the certificate.
- **Firewall:** allow only SSH and the web:

  ```bash
  sudo ufw allow OpenSSH && sudo ufw allow 80/tcp && sudo ufw allow 443/tcp && sudo ufw allow 443/udp && sudo ufw enable
  ```

  Docker publishes ports past `ufw`; that is why only the `edge` service publishes any.
- **SSH:** sign in with keys only (`PasswordAuthentication no` in `/etc/ssh/sshd_config`), no
  root login.
- **Security updates:** `sudo apt install unattended-upgrades` and enable it; reboot when it
  asks (the shop comes back on its own: `restart: unless-stopped`).
- **Docker:** Docker Engine with the Compose plugin from Docker's own packages
  (docs.docker.com → Install Docker Engine).

---

## 3. First start

### 3.1 Get the code and fill in the settings

```bash
git clone git@github.com:<owner>/<repository>.git pw-aquatics
```

The repository is private: give the server a read-only **deploy key** (`ssh-keygen -t ed25519`
on the server, the public key under GitHub → repository → Settings → Deploy keys).

```bash
cd pw-aquatics && cp .env.production.example .env && chmod 600 .env
```

Fill in `.env` (every line is explained there). Generate the two random secrets with:

```bash
openssl rand -base64 32
```

once for `POSTGRES_PASSWORD` and once for `APP_ENCRYPTION_KEY`. For the first administrator set
`APP_ADMIN_BOOTSTRAP_EMAIL` and a long `APP_ADMIN_BOOTSTRAP_PASSWORD`.

Without an IBAN (`BANK_*`) no orders can be placed. Stripe, PayPal and Google are optional; leave
them empty until their accounts are ready (§5).

### 3.2 Secrets

Keep these in the password manager. `.env` exists only on the server; never commit it, email it
or paste it into a chat.

| Secret | If it is lost | To change it |
|---|---|---|
| `POSTGRES_PASSWORD` | Read it from `.env` | Change it in PostgreSQL (`ALTER USER pwaquatics PASSWORD '…'`) and in `.env`, then `docker compose up -d` |
| `APP_ENCRYPTION_KEY` | Administrators sign in with a recovery code and set up their app again | Same as lost: new key in `.env`, restart, every administrator sets up the app again |
| `SMTP_PASSWORD` | Create a new one at the mail provider | At the provider, then `.env`, restart |
| Stripe, PayPal, Google keys | Create new ones in their dashboards | Roll the key there, update `.env`, restart |
| Administrator passwords | "Forgot password" on the sign-in page | Profile page |

### 3.3 Start

```bash
docker compose up -d --build
```

The first build takes a few minutes. Then:

```bash
docker compose ps
```

```bash
curl -s https://shop.example/health
```

`{"status":"UP"}` means the API and the database work. Open `https://shop.example` in a browser.

### 3.4 First administrator

1. Sign in at `https://shop.example/login` with the bootstrap address and password.
2. Open `https://shop.example/admin`: the shop asks you to set up **two-step sign-in** — scan the
   QR code with an authenticator app (Google Authenticator, Microsoft Authenticator, 2FAS, the
   iPhone's Passwords app …), enter its code, and **save the ten recovery codes** in the password
   manager.
3. Remove both `APP_ADMIN_BOOTSTRAP_*` lines from `.env` and apply it:

   ```bash
   docker compose up -d backend
   ```

More administrators: register them as customers in the shop, then give them the role on the
server (they set up two-step sign-in at their next sign-in):

```bash
docker compose exec postgres psql -U pwaquatics -c "UPDATE users SET role = 'ADMIN' WHERE email = 'name@shop.example';"
```

---

## 4. Email that arrives

The shop sends order confirmations, payment and shipping notices and account emails through the
SMTP server of a mail provider (`SMTP_*` in `.env`, port 587 with STARTTLS). Use an address on the
shop's own domain as sender (`MAIL_FROM`, e.g. `PW Aquatics <shop@shop.example>`); a free-mail
address (Gmail and similar) as sender will be rejected or land in spam.

Choose a provider that sends from the EU and offers SMTP (the domain's mail host, or a
transactional service such as Brevo, Mailjet or Postmark). The provider tells you the exact DNS
records; they always come down to these three, set at the domain's DNS:

| Record | Name | Value (example) | What it does |
|---|---|---|---|
| SPF (`TXT`) | `shop.example` | `v=spf1 include:mail.example ~all` | Says which servers may send for the domain. **One** SPF record only: merge the `include:`s if there are several senders |
| DKIM (`TXT` or `CNAME`) | e.g. `mail._domainkey.shop.example` | The key the provider shows | Signs every email, so receivers can check it was not changed |
| DMARC (`TXT`) | `_dmarc.shop.example` | `v=DMARC1; p=none; rua=mailto:dmarc@shop.example` | Tells receivers what to do when SPF/DKIM fail, and where to send reports |

Start DMARC with `p=none`, read the reports for two to four weeks, then tighten to
`p=quarantine` (and later `p=reject`) once all real emails pass.

**Test:** send an email from the shop (e.g. the contact form, or register a test account) to
the address shown at mail-tester.com; aim for 9/10 or better. In Gmail, "Show original" should
say `SPF: PASS`, `DKIM: PASS`, `DMARC: PASS`.

Emails that could not be sent after 10 attempts are kept as `FAILED`:

```bash
docker compose exec postgres psql -U pwaquatics -c "SELECT count(*) FROM outbox_messages WHERE status = 'FAILED';"
```

---

## 5. Payments and Google sign-in

All three are optional. Each needs its live credentials in `.env` and a restart
(`docker compose up -d backend`). Test each with a real, small payment and refund it from the
admin order page.

**Stripe** (cards, Apple Pay, Google Pay):

1. Dashboard in live mode → Developers → API keys: `STRIPE_SECRET_KEY` (`sk_live_…` or a
   restricted key) and `STRIPE_PUBLISHABLE_KEY` (`pk_live_…`).
2. Developers → Webhooks → Add endpoint `https://shop.example/api/v1/webhooks/stripe` with the
   events `payment_intent.succeeded`, `payment_intent.processing`,
   `payment_intent.payment_failed`, `payment_intent.canceled`, `charge.refunded`,
   `refund.updated`, `refund.failed`. Its signing secret is `STRIPE_WEBHOOK_SECRET`.
3. Settings → Payment methods → Payment method domains: add `shop.example`, so Apple Pay and
   Google Pay are offered, and follow the Dashboard if it asks for a verification step.

**PayPal:** in the PayPal Developer Dashboard, a **live** REST app: client id and secret
(`PAYPAL_CLIENT_ID`, `PAYPAL_CLIENT_SECRET`, `PAYPAL_ENVIRONMENT=live`). Add a webhook for
`https://shop.example/api/v1/webhooks/paypal` with `CHECKOUT.ORDER.APPROVED`,
`PAYMENT.CAPTURE.COMPLETED`, `PAYMENT.CAPTURE.PENDING`, `PAYMENT.CAPTURE.DENIED`,
`PAYMENT.CAPTURE.DECLINED`, `PAYMENT.CAPTURE.REFUNDED` and `PAYMENT.CAPTURE.REVERSED`; its id
is `PAYPAL_WEBHOOK_ID`.

**Google sign-in:** Google Cloud console → APIs & Services → Credentials → OAuth client of type
"Web application" with the redirect URI `https://shop.example/api/v1/auth/google/callback`;
publish the consent screen. `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`.

---

## 6. Backups

### 6.1 What is backed up

Every night at `BACKUP_TIME` (Europe/Berlin, default 03:30) the `backup` service writes a folder
`./backups/<date_time>/` with `database.dump` (the whole database) and `media.tar.gz` (all
photos). A dump is checked after writing; folders older than `BACKUP_KEEP_DAYS` (default 14) are
deleted. A failed backup leaves nothing half-written behind.

Not in the backup: `.env` (keep it in the password manager) and the certificates (Caddy gets
new ones).

The two-step sign-in keys in a backup are encrypted with `APP_ENCRYPTION_KEY`, which is not in
it. Everything else — customers, addresses, orders — is readable to whoever has the files.

### 6.2 A copy away from the server

Backups on the same server do not help if the server is lost. Copy them every night to storage
elsewhere in the EU, **encrypted before they leave the server**, for example with
[restic](https://restic.net) to a storage box or S3-compatible storage:

```bash
restic -r sftp:user@storage.example:pw-aquatics init
```

and a nightly cron job after the backup time (`crontab -e`; the restic password is in a file
readable only by this user, and in the password manager — without it the copies cannot be
opened):

```bash
15 4 * * * cd /home/shop/pw-aquatics && export RESTIC_PASSWORD_FILE=/home/shop/.restic-password && restic -r sftp:user@storage.example:pw-aquatics backup backups && restic -r sftp:user@storage.example:pw-aquatics forget --keep-daily 14 --keep-weekly 8 --keep-monthly 6 --prune
```

### 6.3 Is it working?

Set `BACKUP_PING_URL` to a check at a heartbeat service (e.g. healthchecks.io, period 1 day,
grace 2 hours). Every successful backup calls it; a failed one calls `<url>/fail`; a missing
call means the backup did not run. Either way the service emails you.

By hand:

```bash
ls -lh backups/*/
```

```bash
docker compose logs --tail 20 backup
```

A backup right now (e.g. before an update):

```bash
docker compose exec backup sh /opt/backup/backup.sh
```

### 6.4 Restore a backup

This **replaces** the database and all photos with the backup's. Orders, customers and changes
made after the backup are lost (payments received in between are still in Stripe's and PayPal's
dashboards).

```bash
docker compose run --rm restore
```

lists the backups. Then:

```bash
docker compose stop backend backup
```

```bash
docker compose run --rm restore 2026-11-18_03-30-00 --yes
```

```bash
docker compose up -d
```

Check `https://shop.example/health`, sign in to the admin area and look at the newest orders.
Then sign in again everywhere: sessions are part of the database.

**On a new server:** set it up as in §2–3 with the **same** `.env` (from the password manager),
copy the backup folder into `./backups`, start only the database
(`docker compose up -d postgres`), restore as above, then `docker compose up -d`.

### 6.5 Practise it

Every three months, restore the newest backup on a spare server or a local machine and check
that the shop starts with the latest orders and photos. A backup that was never restored is a
hope, not a backup. (CI restores one on every change to the stack:
`.github/workflows/production.yml`.)

---

## 7. Monitoring

| What | How | Alarm |
|---|---|---|
| Shop reachable | An uptime monitor (e.g. UptimeRobot, Better Stack, healthchecks.io) on `https://shop.example/health`, every 1–5 minutes, expecting `"UP"` | It is down or the database is unreachable (503) |
| Backups | `BACKUP_PING_URL` (§6.3) | A night without a successful backup |
| HTTPS certificate | Caddy renews it 30 days before it expires; the uptime monitor's certificate check, or Let's Encrypt's emails to `ACME_EMAIL` | Renewal keeps failing |
| Disk | `df -h` and `docker system df` monthly, or the hosting provider's disk alert at 80 % | Photos, backups and old images fill the disk |
| Emails | The `FAILED` count (§4) | Emails do not arrive |
| Errors | `docker compose logs --since 24h backend \| grep -E "ERROR\|WARN"` | Look after anything unexpected |

Old Docker images after updates:

```bash
docker image prune -f
```

---

## 8. Updates

**The shop:**

```bash
docker compose exec backup sh /opt/backup/backup.sh
```

```bash
git pull && docker compose up -d --build
```

The API applies new database migrations when it starts. Watch it come up:

```bash
docker compose logs -f backend
```

**Going back:** database migrations only go forward. If an update has to be undone, check out
the previous version **and** restore the backup made just before the update (§6.4):

```bash
git checkout <previous tag or commit> && docker compose up -d --build
```

**Images:** Caddy and PostgreSQL run from published images (`caddy:2-alpine`,
`postgres:18-alpine`) that are rebuilt upstream with security fixes under the same tag, and the
shop's own images start from such images too. Neither `git pull` nor `--build` fetches the new
ones; this does (once a month, and when an advisory concerns one of them):

```bash
docker compose build --pull && docker compose up -d --pull always
```

**Dependencies:** Dependabot opens pull requests for npm, Gradle, the Dockerfiles' base images
and GitHub Actions (`.github/dependabot.yml`); security fixes arrive as soon as an advisory is
published. Merge them when CI is green, then update the server as above.

**Monthly routine (15 minutes):** merge Dependabot's pull requests and update; refresh the images
(above); check the backup heartbeat and run `ls backups`; look at disk space; glance at the error
log; `docker image prune -f`.

---

## 9. When something goes wrong

**The shop is down.** `docker compose ps` (which service is not `running`/`healthy`?), then
`docker compose logs --tail 100 <service>`. Common causes: disk full (`df -h`), the database not
starting, a mistake in `.env` (the API names the setting it does not like). `docker compose up
-d` starts what is missing.

**An administrator lost their phone.** Sign in with a recovery code ("Use a recovery code
instead"), then on the profile page "Move to a new phone" and "New recovery codes".
**Phone and recovery codes both lost:** after checking it really is that person (call them),
reset their second step on the server; they set it up again at the next sign-in:

```bash
docker compose exec postgres psql -U pwaquatics -c "DELETE FROM user_two_factor WHERE user_id = (SELECT id FROM users WHERE email = 'name@shop.example');"
```

**Someone may have broken in** (unknown admin actions in the audit log, a leaked `.env`, a
customer reports their data elsewhere):

1. Write down what you know and when you learned it.
2. Sign everyone out and change the secrets: new `POSTGRES_PASSWORD`, `SMTP_PASSWORD`, Stripe,
   PayPal and Google keys (§3.2), administrators' passwords.

   ```bash
   docker compose exec postgres psql -U pwaquatics -c "DELETE FROM spring_session;"
   ```

3. Look at what happened: `audit_log` (who did what, from which IP, for 90 days) and the logs.
4. **If customers' personal data may be affected, the data-protection authority must be told
   within 72 hours** of learning about it (GDPR Art. 33), and the customers themselves without
   delay if the risk to them is high (Art. 34). Ask the legal adviser.

---

## 10. Before the shop opens

- [ ] DNS points at the server; `https://shop.example` shows the shop with a valid certificate,
      `http://` redirects to `https://`.
- [ ] `.env` complete, `chmod 600`, a copy of it in the password manager.
- [ ] First administrator signed in with two-step sign-in, recovery codes saved; bootstrap lines
      removed from `.env`.
- [ ] Bank account (`BANK_*`) correct; a test order paid by bank transfer marked paid in the
      admin area.
- [ ] Email: SPF, DKIM and DMARC set; mail-tester 9/10 or better; order emails arrive.
- [ ] Stripe and PayPal live (if used): webhooks added, one real payment made and refunded.
- [ ] Legal pages (imprint, privacy policy, terms, right of withdrawal) written with the legal
      adviser; the privacy policy names the mail provider, the hosting provider, Stripe, PayPal
      and Google (docs/DATABASE.md §6).
- [ ] Shipping methods, prices and the shipping pause checked in the admin area.
- [ ] Uptime monitor and backup heartbeat set up; the first night's backup arrived; the off-site
      copy runs; one restore practised.
- [ ] Demo data not loaded (the server runs without the `dev` profile, so it never is).
- [ ] Security, performance and accessibility checks repeated against the server
      (docs/CHECKS.md §2): ZAP baseline without failures, Lighthouse on the main pages with real
      photos, and the open points of docs/CHECKS.md §1.4 settled with the owner.
