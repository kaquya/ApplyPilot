package ch.applypilot;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;

class BillingLifecycleTest {

    final ObjectMapper json = new ObjectMapper();
    AccountRepository accounts;
    StripeClient stripe;
    JdbcTemplate jdbc;
    BillingService billing;
    Account account;

    @BeforeEach
    void setup() {
        accounts = mock(AccountRepository.class);
        stripe = mock(StripeClient.class);
        jdbc = mock(JdbcTemplate.class);
        stripe.key = "sk_test_placeholder";
        billing = new BillingService(accounts, stripe, json, jdbc);
        billing.enabled = true;
        billing.secret = "whsec_placeholder";
        billing.plus = "price_plus";
        billing.pro = "price_pro";
        billing.lifetime = "price_lifetime";
        account = new Account();
        account.stripeCustomer = "cus_test";
        when(jdbc.queryForObject(anyString(), eq(Integer.class), anyString())).thenReturn(0);
        when(accounts.findByStripeCustomer("cus_test")).thenReturn(Optional.of(account));
        when(accounts.findByStripeCustomer("")).thenReturn(Optional.empty());
        when(accounts.lockById(account.id)).thenReturn(Optional.of(account));
    }

    JsonNode node(String value) throws Exception {
        return json.readTree(value);
    }

    void event(String type, String object) throws Exception {
        String payload =
            "{\"id\":\"evt_" +
            UUID.randomUUID() +
            "\",\"type\":\"" +
            type +
            "\",\"data\":{\"object\":" +
            object +
            "}}";
        long now = Instant.now().getEpochSecond();
        var mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(billing.secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String signature =
            "t=" +
            now +
            ",v1=" +
            HexFormat.of().formatHex(
                mac.doFinal((now + "." + payload).getBytes(StandardCharsets.UTF_8))
            );
        billing.webhook(payload, signature);
    }

    @Test
    void lateDeletionEventCannotRevokeCurrentActiveSubscription() throws Exception {
        when(stripe.get("subscriptions?customer=cus_test&status=all&limit=100")).thenReturn(
            node(
                "{\"data\":[{\"status\":\"active\",\"items\":{\"data\":[{\"price\":{\"id\":\"price_pro\"}}]}}]}"
            )
        );
        event("customer.subscription.deleted", "{\"customer\":\"cus_test\"}");
        assertThat(account.plan).isEqualTo("PRO");
    }

    @Test
    void unpaidCheckoutCannotGrantLifetime() throws Exception {
        when(stripe.get("checkout/sessions/cs_test?expand[]=line_items")).thenReturn(
            node(
                "{\"customer\":\"cus_test\",\"mode\":\"payment\",\"payment_status\":\"unpaid\",\"line_items\":{\"data\":[{\"price\":{\"id\":\"price_lifetime\"}}]}}"
            )
        );
        when(stripe.get("subscriptions?customer=cus_test&status=all&limit=100")).thenReturn(
            node("{\"data\":[]}")
        );
        event("checkout.session.completed", "{\"id\":\"cs_test\",\"customer\":\"cus_test\"}");
        assertThat(account.plan).isEqualTo("FREE");
        assertThat(account.lifetimePayment).isNull();
    }

    @Test
    void verifiedPaidLifetimeGrantsEntitlement() throws Exception {
        when(stripe.get("checkout/sessions/cs_test?expand[]=line_items")).thenReturn(
            node(
                "{\"customer\":\"cus_test\",\"mode\":\"payment\",\"payment_status\":\"paid\",\"payment_intent\":\"pi_test\",\"line_items\":{\"data\":[{\"price\":{\"id\":\"price_lifetime\"}}]}}"
            )
        );
        when(stripe.get("payment_intents/pi_test?expand[]=latest_charge")).thenReturn(
            node("{\"latest_charge\":{\"refunded\":false,\"disputed\":false}}")
        );
        event("checkout.session.completed", "{\"id\":\"cs_test\",\"customer\":\"cus_test\"}");
        assertThat(account.plan).isEqualTo("LIFETIME");
        assertThat(account.lifetimePayment).isEqualTo("pi_test");
    }

    @Test
    void disputeWithoutCustomerFieldRevokesLifetime() throws Exception {
        account.plan = "LIFETIME";
        account.lifetimePayment = "pi_test";
        when(accounts.findByLifetimePayment("pi_test")).thenReturn(Optional.of(account));
        when(stripe.get("subscriptions?customer=cus_test&status=all&limit=100")).thenReturn(
            node("{\"data\":[]}")
        );
        event("charge.dispute.created", "{\"payment_intent\":\"pi_test\"}");
        assertThat(account.plan).isEqualTo("FREE");
        assertThat(account.lifetimePayment).isNull();
    }
}
