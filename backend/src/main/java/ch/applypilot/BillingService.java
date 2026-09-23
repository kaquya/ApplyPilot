package ch.applypilot;

import com.fasterxml.jackson.databind.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
class BillingService {

    private final AccountRepository accounts;
    private final StripeClient stripe;
    private final ObjectMapper json;
    private final JdbcTemplate jdbc;

    @Value("${app.billing}")
    boolean enabled;

    @Value("${app.origin}")
    String origin;

    @Value("${app.stripe-webhook-secret}")
    String secret;

    @Value("${app.stripe-plus-price}")
    String plus;

    @Value("${app.stripe-pro-price}")
    String pro;

    @Value("${app.stripe-lifetime-price}")
    String lifetime;

    BillingService(
        AccountRepository accounts,
        StripeClient stripe,
        ObjectMapper json,
        JdbcTemplate jdbc
    ) {
        this.accounts = accounts;
        this.stripe = stripe;
        this.json = json;
        this.jdbc = jdbc;
    }

    void configured() {
        if (!enabled || secret.isBlank() || stripe.key.isBlank()) throw new ResponseStatusException(
            HttpStatus.SERVICE_UNAVAILABLE,
            "Plans are not available for purchase yet."
        );
    }

    @Transactional
    String checkout(UUID owner, String plan) {
        configured();
        String price = switch (plan) {
            case "PLUS" -> plus;
            case "PRO" -> pro;
            case "LIFETIME" -> lifetime;
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown plan.");
        };
        if (price.isBlank()) throw new ResponseStatusException(
            HttpStatus.SERVICE_UNAVAILABLE,
            "This plan is not configured."
        );
        var account = accounts.lockById(owner).orElseThrow();
        if (!account.plan.equals("FREE")) throw new ResponseStatusException(
            HttpStatus.CONFLICT,
            "Use Manage subscription to change an existing plan."
        );
        if (account.stripeCustomer == null) {
            var customer = stripe.post(
                "customers",
                Map.of("email", account.email, "metadata[accountId]", owner.toString()),
                "customer-" + owner
            );
            account.stripeCustomer = customer.path("id").asText();
        }
        var currentSubscriptions = stripe.get(
            "subscriptions?customer=" + account.stripeCustomer + "&status=all&limit=100"
        );
        for (var subscription : currentSubscriptions.path("data"))
            if (
                Set.of("active", "trialing", "past_due", "unpaid", "incomplete").contains(
                    subscription.path("status").asText()
                )
            ) throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "An existing subscription needs attention. Use Manage subscription."
            );
        var previous = stripe.get(
            "checkout/sessions?customer=" + account.stripeCustomer + "&limit=100"
        );
        for (var session : previous.path("data")) {
            if (
                "paid".equals(session.path("payment_status").asText()) &&
                "LIFETIME".equals(session.path("metadata").path("plan").asText())
            ) throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "A lifetime purchase already exists. Contact support if access has not updated."
            );
            if (
                "complete".equals(session.path("status").asText()) &&
                "unpaid".equals(session.path("payment_status").asText())
            ) throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "A payment is still processing. Wait for confirmation before starting another checkout."
            );
            if ("open".equals(session.path("status").asText())) {
                if (plan.equals(session.path("metadata").path("plan").asText())) return session
                    .path("url")
                    .asText();
                stripe.post(
                    "checkout/sessions/" + session.path("id").asText() + "/expire",
                    Map.of(),
                    null
                );
            }
        }
        var data = new HashMap<String, String>();
        data.put("customer", account.stripeCustomer);
        data.put("mode", plan.equals("LIFETIME") ? "payment" : "subscription");
        data.put("client_reference_id", owner.toString());
        data.put("line_items[0][price]", price);
        data.put("line_items[0][quantity]", "1");
        data.put("success_url", origin + "/?billing=success");
        data.put("cancel_url", origin + "/?billing=cancelled");
        data.put("metadata[accountId]", owner.toString());
        data.put("metadata[plan]", plan);
        if (!plan.equals("LIFETIME")) data.put(
            "subscription_data[metadata][accountId]",
            owner.toString()
        );
        return stripe
            .post("checkout/sessions", data, "checkout-" + owner + "-" + UUID.randomUUID())
            .path("url")
            .asText();
    }

    String portal(UUID owner) {
        configured();
        var account = accounts.findById(owner).orElseThrow();
        if (account.stripeCustomer == null) throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "No billing account exists yet."
        );
        return stripe
            .post(
                "billing_portal/sessions",
                Map.of(
                    "customer",
                    account.stripeCustomer,
                    "return_url",
                    origin + "/?billing=return"
                ),
                null
            )
            .path("url")
            .asText();
    }

    static boolean validSignature(String payload, String header, String secret, long now) {
        if (header == null || secret.isBlank()) return false;
        try {
            String timestamp = null;
            List<String> signatures = new ArrayList<>();
            for (String part : header.split(",")) {
                var pair = part.strip().split("=", 2);
                if (pair.length == 2) {
                    if (pair[0].equals("t")) timestamp = pair[1];
                    if (pair[0].equals("v1")) signatures.add(pair[1]);
                }
            }
            if (timestamp == null || Math.abs(now - Long.parseLong(timestamp)) > 300) return false;
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] expected = mac.doFinal(
                (timestamp + "." + payload).getBytes(StandardCharsets.UTF_8)
            );
            for (String signature : signatures)
                try {
                    if (
                        MessageDigest.isEqual(expected, HexFormat.of().parseHex(signature))
                    ) return true;
                } catch (IllegalArgumentException ignored) {}
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    @Transactional
    void webhook(String payload, String signature) {
        configured();
        if (
            payload.length() > 1_000_000 ||
            !validSignature(payload, signature, secret, Instant.now().getEpochSecond())
        ) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid webhook signature.");
        JsonNode event;
        try {
            event = json.readTree(payload);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid webhook.");
        }
        String eventId = event.path("id").asText();
        String type = event.path("type").asText();
        var object = event.path("data").path("object");
        if (eventId.isBlank() || eventId.length() > 255) throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "Invalid event id."
        );
        if (
            jdbc.queryForObject(
                "select count(*) from billing_events where id=?",
                Integer.class,
                eventId
            ) > 0
        ) return;
        String customer = object.path("customer").asText();
        var account = accounts.findByStripeCustomer(customer).orElse(null);
        if (account == null && type.equals("charge.dispute.created")) account = accounts
            .findByLifetimePayment(object.path("payment_intent").asText())
            .orElse(null);
        if (account != null) {
            account = accounts.lockById(account.id).orElseThrow();
            if (
                jdbc.queryForObject(
                    "select count(*) from billing_events where id=?",
                    Integer.class,
                    eventId
                ) > 0
            ) return;
            if (
                type.startsWith("checkout.session.") &&
                Set.of(
                    "checkout.session.completed",
                    "checkout.session.async_payment_succeeded"
                ).contains(type)
            ) {
                var session = stripe.get(
                    "checkout/sessions/" + object.path("id").asText() + "?expand[]=line_items"
                );
                if (
                    account.stripeCustomer.equals(session.path("customer").asText()) &&
                    "payment".equals(session.path("mode").asText()) &&
                    "paid".equals(session.path("payment_status").asText()) &&
                    lifetime.equals(
                        session
                            .path("line_items")
                            .path("data")
                            .path(0)
                            .path("price")
                            .path("id")
                            .asText()
                    ) &&
                    !lifetime.isBlank()
                ) {
                    String paymentId = session.path("payment_intent").asText();
                    var payment = stripe.get(
                        "payment_intents/" + paymentId + "?expand[]=latest_charge"
                    );
                    var charge = payment.path("latest_charge");
                    if (
                        !charge.path("refunded").asBoolean() && !charge.path("disputed").asBoolean()
                    ) {
                        account.plan = "LIFETIME";
                        account.lifetimePayment = paymentId;
                    }
                }
            }
            if (
                (type.equals("charge.refunded") && object.path("refunded").asBoolean()) ||
                type.equals("charge.dispute.created")
            ) {
                String payment = object.path("payment_intent").asText();
                if (payment.equals(account.lifetimePayment)) {
                    account.lifetimePayment = null;
                    account.plan = "FREE";
                }
            }
            if (account.lifetimePayment == null) {
                // Fetch current provider state rather than trusting event order or a return URL.
                var subscriptions = stripe.get(
                    "subscriptions?customer=" + account.stripeCustomer + "&status=all&limit=100"
                );
                String plan = "FREE";
                for (var subscription : subscriptions.path("data"))
                    if (
                        Set.of("active", "trialing").contains(subscription.path("status").asText())
                    ) for (var item : subscription.path("items").path("data")) {
                        String id = item.path("price").path("id").asText();
                        if (id.equals(pro) && !pro.isBlank()) plan = "PRO";
                        else if (id.equals(plus) && !plus.isBlank() && plan.equals("FREE")) plan =
                            "PLUS";
                    }
                account.plan = plan;
            }
        }
        jdbc.update(
            "insert into billing_events(id,created_at) values (?,?)",
            eventId,
            java.sql.Timestamp.from(Instant.now())
        );
    }
}
