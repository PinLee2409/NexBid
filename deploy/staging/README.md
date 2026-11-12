# Staging

One Linux server running the whole NexBid stack behind HTTPS on your own domain. This is the place to try VNPay's IPN and real Brevo sends: both need a public HTTPS address, which `localhost` cannot give.

## What you need

- A server with Docker and the Compose plugin; 2 vCPU and 4 GB RAM is comfortable.
- Ports 80 and 443 open.
- A domain or subdomain whose DNS A record points at the server, for example `staging.your-domain.com`.
- A VNPay sandbox merchant: [sandbox.vnpayment.vn/devreg](https://sandbox.vnpayment.vn/devreg/).
- A Brevo account with an API key and a verified sender. Verify the whole domain (SPF and DKIM) so mail does not land in spam.

## Steps

1. Clone the repository on the server, then create the settings file:

   ```bash
   cp deploy/staging/.env.example deploy/staging/.env
   ```

2. Fill in `deploy/staging/.env`. Use `openssl rand -hex 32` for `NEXBID_DB_PASSWORD` and `NEXBID_JWT_SECRET`. The file is ignored by git.

3. Start everything:

   ```bash
   docker compose -f deploy/staging/docker-compose.yml --env-file deploy/staging/.env up -d --build
   ```

   Caddy gets the certificate within a minute of the first request. The backend runs with the `prod` profile, so it refuses to start on development secrets or VNPay placeholder keys. Its logs say which one:

   ```bash
   docker compose -f deploy/staging/docker-compose.yml logs backend
   ```

4. In the VNPay merchant portal, set the IPN URL to `https://<your-domain>/api/payments/vnpay/ipn`.

5. Make yourself admin: register on the site, add your email to `NEXBID_ADMIN_EMAILS`, then run `up -d` again.

## Updating

```bash
git pull
docker compose -f deploy/staging/docker-compose.yml --env-file deploy/staging/.env up -d --build
```

Data lives in named volumes (`postgres-data`, `product-images`, `kafka-data`, `caddy-data`), so it survives updates. `down -v` deletes it.

## How requests flow

| Path | Goes to |
| --- | --- |
| `/api/*`, `/media/*`, `/ws` | backend (VNPay's IPN, product photos, the realtime socket over `wss://`) |
| everything else | frontend |

Postgres, Redis, Kafka and the backend publish no ports; only Caddy is reachable from outside.
