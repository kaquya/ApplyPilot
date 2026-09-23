# Deploy ApplyPilot on Render

The repository includes a [Render Blueprint](../render.yaml). It creates a public Next.js service, a private Spring Boot service, PostgreSQL 17 and a 5 GB document disk, all in Frankfurt. Frankfurt is in Germany; this is not Swiss data residency. [Render regions](https://render.com/docs/regions)

## 1. Deploy the tracker

1. Sign in to [Render](https://dashboard.render.com/), connect GitHub and grant access to `kaquya/ApplyPilot`.
2. Choose **New > Blueprint**, select that repository and branch `main`, and use `render.yaml`.
3. Render prompts for **APP_ORIGIN**. If your custom HTTPS domain is ready, enter its origin without a trailing slash. Otherwise enter `https://setup.invalid` temporarily. Do not configure Google or Stripe until step 6.
4. Review the paid resources and Render's current total before deploying. The frontend uses `0.5c-512mb`, the backend `1c-2g`, and PostgreSQL `0.1c-256mb` with 5 GB storage. The backend gets 2 GB RAM for Spring Boot and PDF processing. Database capacity can be increased as usage grows. These are deployment defaults, not a load-tested capacity promise.
5. Apply the Blueprint and wait for `applypilot-db`, `applypilot-api` and `applypilot-web` to become available. The backend runs its tests during the image build and applies database migrations at startup. The frontend health check goes through the API proxy.
6. Copy the actual HTTPS address shown on **applypilot-web**. In **applypilot-api > Environment**, replace **APP_ORIGIN** with that exact origin, without a trailing slash. Save and redeploy the API. Never leave `https://setup.invalid` configured when enabling integrations.
7. Open the web address, create an account, add a CV profile and application, then upload a small PDF or text document. Restart the API and confirm the application and downloadable document remain available.

The Blueprint wires database credentials and the private backend address automatically. Do not create a public backend or manually expose PostgreSQL. No provider credentials are necessary to deploy the tracker.

All optional credentials below belong in **applypilot-api > Environment**, never in the frontend, GitHub, or `render.yaml`. Save and redeploy after changes. Optional values are intentionally omitted from the Blueprint so later Blueprint syncs preserve your dashboard settings. `APP_ORIGIN` uses `sync: false` for the same reason. [Blueprint environment settings](https://render.com/docs/blueprint-spec#setting-environment-variables)

Automatic deployment is configured for successful GitHub checks. Enable GitHub Actions for the repository; if a later commit is not deploying, inspect the checks and Render Events. The initial Blueprint still builds its resources.

## 2. Set up AI

**ChatGPT Plus does not include API usage for this application.** You can use the same OpenAI login, but API billing is separate. [OpenAI billing explanation](https://help.openai.com/en/articles/9039756)

1. Open the [OpenAI Platform](https://platform.openai.com/), create/select a project for ApplyPilot and activate API billing under the organization billing settings.
2. Create a project API key. Add it to the backend as `OPENAI_API_KEY`.
3. Add `OPENAI_MODEL=gpt-4.1-mini` (also the application's default). The integration uses the Responses API and structured outputs. Keep this model initially so you can evaluate the existing behavior before making model changes. [API quickstart](https://developers.openai.com/api/docs/quickstart) and [model capabilities](https://developers.openai.com/api/docs/models/gpt-4.1-mini)
4. Configure usage alerts and review usage in the API dashboard. Do not assume a project budget alert is a hard spending stop.
5. Save and redeploy the API. Sign in, add real CV text to a profile, paste a vacancy into an application, select that profile, and run its analysis. Review the quotations and suggested changes before using any generated text.

The application never uses your ChatGPT browser session. With billing disabled, registered accounts can use all AI tools subject to the application's monthly allowance; the API charges go to your OpenAI project. For a private trial, create your account first, then set `REGISTRATION_ENABLED=false` to close further sign-ups. Existing accounts can still sign in.

## 3. Enable Google login

1. Open the [Google Cloud Console](https://console.cloud.google.com/), create/select a project and open **Google Auth Platform** (also accessible through APIs & Services OAuth settings).
2. Configure the app branding, support email and audience. For applicants outside your own Google Workspace, use an **External** audience. For initial testing, add the Google accounts you will use as test users.
3. Create an OAuth client with application type **Web application**.
4. Add this exact **Authorized redirect URI**, replacing the origin with your actual frontend address:

   ```text
   https://YOUR-APP-DOMAIN/login/oauth2/code/google
   ```

5. Add `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET` to the backend and redeploy. This server-side flow does not require a browser API key or a Google service-account JSON file. The app requests only `openid`, `profile` and `email`. [Google OpenID Connect setup](https://developers.google.com/identity/openid-connect/openid-connect)
6. Test **Continue with Google** using an account that has not already registered with a password. Existing password accounts are deliberately not linked automatically by matching email; continue using their passwords.
7. Before opening access broadly, move the OAuth app to production and complete the domain/branding verification steps Google requests. Keep the registered redirect URI and `APP_ORIGIN` current when changing domains. [Google production policies](https://developers.google.com/identity/protocols/oauth2/production-readiness/policy-compliance)

Google login does not require Resend. For creation of a new Google account in ApplyPilot, `REGISTRATION_ENABLED` must be true.

## 4. Configure Stripe payments in a sandbox

Use a test-only deployment/database for Stripe sandbox work. ApplyPilot stores Stripe customer IDs on accounts, and sandbox IDs do not work with live API keys. Do not switch an existing sandbox database to live keys. For production, provision a separate clean stack with distinct resource names and update all matching `fromService` / `fromDatabase` references in its Blueprint. Preserve any existing real-user data.

1. Create/activate your [Stripe account](https://dashboard.stripe.com/) and open a sandbox or test mode.
2. Create the following products and prices. Copy each **Price ID** beginning with `price_`, not the Product ID.

   | Product | Price | Environment variable |
   | --- | --- | --- |
   | ApplyPilot Plus | CHF 7.90, recurring monthly | `STRIPE_PLUS_PRICE` |
   | ApplyPilot Pro | CHF 12.90, recurring monthly | `STRIPE_PRO_PRICE` |
   | ApplyPilot Lifetime | CHF 99, one-time | `STRIPE_LIFETIME_PRICE` |

   These match the current UI. If you choose different amounts, update the displayed prices too. [Stripe subscription setup](https://docs.stripe.com/billing/quickstart)

3. Copy the sandbox secret API key into `STRIPE_SECRET_KEY` (normally `sk_test_...`). This integration redirects to Stripe-hosted Checkout and does not need a frontend publishable key.
4. In Stripe Workbench/Developers, create a webhook event destination for **your account**, using snapshot events and this URL:

   ```text
   https://YOUR-APP-DOMAIN/api/billing/webhook
   ```

   Subscribe to:

   ```text
   checkout.session.completed
   checkout.session.async_payment_succeeded
   customer.subscription.created
   customer.subscription.updated
   customer.subscription.deleted
   charge.refunded
   charge.dispute.created
   ```

5. Reveal that destination's signing secret (`whsec_...`) and set `STRIPE_WEBHOOK_SECRET`. Use the deployed destination's secret, not one from a local Stripe CLI listener. [Webhook configuration](https://docs.stripe.com/webhooks)
6. Configure and save the sandbox **Customer portal**. Enable payment-method updates, invoices, cancellation, and plan changes between only the supported Plus and Pro monthly prices. Leave subscription quantity changes disabled. Decide the cancellation timing and proration behavior. Lifetime is a one-time purchase, not a subscription option. [Portal configuration](https://docs.stripe.com/customer-management/configure-portal)
7. Once the key, three price IDs, signing secret and portal are configured, set `BILLING_ENABLED=true` on the backend and redeploy. This enables Checkout and paid-plan restrictions. Free accounts will now need a paid plan for AI.
8. In ApplyPilot, purchase a plan using Stripe's test card `4242 4242 4242 4242`, a future expiry and any valid CVC. Verify that the webhook succeeds and the account's plan changes. Test the portal, cancellation, failed payments and Lifetime refunds as described in [deployment.md](deployment.md#stripe). Never use a real card in a sandbox. [Stripe test payments](https://docs.stripe.com/testing)

For live payments, finish Stripe's business verification, recreate products/prices and the webhook in live mode, and configure the live portal. On the clean production stack, set its live secret key, live Price IDs and live webhook signing secret. Enable billing only after the integration and [release readiness](release-readiness.md) checks are complete. The application does not yet configure Stripe Tax automatically.

## 5. Custom domain, optional email and operations

- Add a custom domain under **applypilot-web > Settings > Custom Domains**, then apply the DNS records Render displays. Once HTTPS is ready, update backend `APP_ORIGIN`, Google's redirect URI and Stripe's webhook URL to match. Cloudflare is optional.
- For email reminders, verify a sender domain in Resend, add its DNS records, and set `RESEND_API_KEY` and `EMAIL_FROM` on the backend. Users opt in through Settings. This does not add password recovery, which remains unfinished.
- The disk is sufficient for initial document storage; no AWS/S3 credentials are required. Render disks allow one backend instance and involve downtime during redeployment. Before scaling to multiple API instances, migrate existing document objects to private S3 storage and review shared rate limiting. [Disk limitations](https://render.com/docs/disks)
- Plan database and document backups together and test a restore. A working deployment does not replace the remaining [release readiness](release-readiness.md) work, including password recovery, account erasure and operator/privacy details.

## Troubleshooting

| Symptom | Check |
| --- | --- |
| Frontend health check fails / API returns 502 | API is healthy, both services are in Frankfurt, and `API_INTERNAL_HOSTPORT` is the Blueprint reference. Rebuild the frontend after changing that value: Next.js bakes rewrites into its build. |
| Database connection fails | Keep `SPRING_PROFILES_ACTIVE=render` and the generated `DATABASE_*` references. Render's `postgresql://...` connection string is not a JDBC URL; this profile assembles the JDBC URL with TLS. |
| Upload fails with permission denied | Use the supplied image entrypoint and mount path `/app/uploads`. The entrypoint prepares a new disk's directory ownership before starting Java as the app user. |
| OAuth redirect mismatch | Compare Google's registered callback with the actual HTTPS browser origin, including `/login/oauth2/code/google`. |
| AI unavailable | Verify API billing, project key/model access, the saved key and API redeployment. ChatGPT Plus alone does not fund the API. |
| Checkout unavailable | Check `BILLING_ENABLED=true`, matching sandbox/live credentials, all Price IDs and the webhook signing secret. |
| Checkout succeeds but plan stays Free | Inspect the Stripe webhook deliveries and backend logs. The return page alone never grants a plan. |

The Compose/local-development configuration remains supported. The `render` Spring profile is only enabled by the Blueprint. Docker Compose continues to use `DATABASE_URL` and its own persistent volumes.
