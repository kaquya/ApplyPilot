# Release readiness

The core workflow can be evaluated locally without external credentials. Complete the following before accepting real users or payments:

- Verify OpenAI outputs with realistic vacancies and CVs in all four languages, including prompt injection, missing evidence, refusals and provider timeouts.
- Verify Google sign-in and the existing-email-account behavior on the final HTTPS domain.
- Run the Stripe sandbox lifecycle described in `deployment.md`; reconcile displayed prices and quotas with the actual Stripe configuration.
- Verify Resend domain configuration, single-use verification, reminder opt-out and failed-delivery retry.
- Verify the selected private object storage region, permissions and backup/restore behavior.
- Add password recovery and self-service account erasure; define retention for application documents and provider metadata.
- Add operator identity, support contact, appropriate privacy/terms content and the final cancellation/refund policy.
- Validate accessibility with assistive technology and native-language review of interface translations.
- Configure monitoring, incident response, edge rate limits and a recovery drill. Confirm HTTPS cookies on the final domain.

The repository has no live credentials, configured hosting account or registered production domain. Deployment configuration alone does not complete these checks.
