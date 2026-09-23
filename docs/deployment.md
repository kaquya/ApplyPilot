# Deployment and integrations

## Coolify and Cloudflare

1. Add this repository as a Docker Compose resource in Coolify using `compose.yml`.
2. Set `POSTGRES_PASSWORD` and `APP_ORIGIN` to the final HTTPS origin, without a trailing slash.
3. Set `COOKIE_SECURE=true`. Route the domain to the frontend on port 3000. Keep database and backend private.
4. Use an HTTPS certificate at the origin and Cloudflare Full (strict) if Cloudflare is the DNS/proxy provider.
5. Preserve the `database` and `documents` volumes. Configure off-host encrypted backups and test restoration.
6. Set registration according to the rollout: `REGISTRATION_ENABLED=false` closes new password and Google accounts while allowing existing accounts to sign in.

The frontend's API proxy target is a build argument. The supplied Compose build sets it to `http://backend:8080`; rebuild after changing the backend service address. `APP_ORIGIN` must match the browser origin for OAuth and provider redirects. The backend accepts forwarded headers only behind a trusted reverse proxy; never expose it directly to the Internet.

## OpenAI

Set `OPENAI_API_KEY` on the backend and choose `OPENAI_MODEL` supporting strict structured outputs on the Responses API. The default is `gpt-4.1-mini`. No key is included in the browser bundle.

Requests are bounded by a per-minute limiter, a persistent monthly request allowance, input length limits, an output-token cap and an HTTP timeout. Set a provider project spending limit as an additional control. Failed requests consume an attempt because provider processing may already have occurred. Review quality with real, consented test cases in each language before release.

The implementation follows [structured output documentation](https://developers.openai.com/api/docs/guides/structured-outputs). Evidence validation can reject unsupported quotations; it cannot prove that every generated sentence is correct. Applicants review all drafts.

## Google OAuth

Create a Web OAuth client and set `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET`. Register:

```text
https://your-domain.example/login/oauth2/code/google
```

For local testing register `http://localhost:3000/login/oauth2/code/google`. Only verified Google email claims are accepted. Existing password accounts are not silently linked by email; those users continue to use their password.

## S3-compatible storage

Set `S3_BUCKET`, `S3_REGION`, optionally `S3_ENDPOINT`, and credentials through `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY` (or the AWS default credential provider chain). Use a private bucket and least-privilege credentials for get/put/delete within that bucket. Select the actual storage region intentionally; the application does not create or relocate buckets.

Without a bucket, the application uses the persistent local documents volume. Downloads always pass through account ownership checks. Switching storage backends does not migrate old files; migrate the object keys before changing configuration.

## Stripe

Keep `BILLING_ENABLED=false` until sandbox verification is complete. Configure:

- `STRIPE_SECRET_KEY` and `STRIPE_WEBHOOK_SECRET`.
- CHF monthly Price IDs for Plus and Pro in `STRIPE_PLUS_PRICE` / `STRIPE_PRO_PRICE`.
- A CHF one-time Price ID for Lifetime in `STRIPE_LIFETIME_PRICE`.
- The customer portal, permitting only the supported subscription prices and intended cancellation rules.
- A webhook endpoint at `/api/billing/webhook` for `checkout.session.completed`, `checkout.session.async_payment_succeeded`, `customer.subscription.created`, `customer.subscription.updated`, `customer.subscription.deleted`, `charge.refunded` and `charge.dispute.created`.

The backend verifies the raw-body signature and timestamp, records processed event IDs, retrieves current subscription state and maps only configured prices. A checkout return URL never grants a plan. Lifetime is granted only for the configured paid price; full refunds and disputes revoke it. A won dispute requires an operator review before restoring a lifetime entitlement. Subscription plans are eligible only while active or trialing.

Before enabling billing, test successful and failed checkout, webhook retry/reordering, renewals, cancellation, unpaid subscriptions, refunds/disputes and the billing portal. Also verify configured Stripe prices match the UI and settle tax/invoicing decisions separately.

## Resend

Set `RESEND_API_KEY` and `EMAIL_FROM` using a verified sender domain. Users enable reminders in Settings. Password users receive a single-use email-verification link; verified Google users can enable reminders directly. Delivery stays disabled until consent and verification are recorded.

The scheduler checks hourly from 09:00 through 18:00 Europe/Zurich and sends at most one digest per account per day when a follow-up is due or an interview is today/tomorrow. Failed delivery retries on a later run. Database locking and provider idempotency keys limit duplicate delivery. Digests contain counts and a workspace link, not the user's CV or full application details. Users can turn reminders off in Settings.

## Operations

`/api/health` is a minimal process readiness endpoint. Keep monitoring, error aggregation, backups and restore drills outside the public UI. The in-memory login limiter is per instance; a multi-replica rollout needs a shared edge/rate-limit layer. PDF parsing and background email processing currently run in the API process; large-scale deployment should isolate document parsing and move delivery to a bounded queue.
