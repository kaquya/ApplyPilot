package ch.applypilot;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class TrustBoundaryTest {

    @Test
    void evidenceMustActuallyOccurInTheCv() throws Exception {
        var json = new ObjectMapper();
        var result = (ObjectNode) json.readTree(
            "{\"requirements\":[{\"status\":\"matched\",\"evidence\":\"Professional AWS experience\"},{\"status\":\"matched\",\"evidence\":\"Java APIs\"},{\"status\":\"partial\",\"evidence\":\"\"}]}"
        );
        AiService.validateEvidence(result, "Built Java APIs using Spring Boot.");
        assertThat(result.at("/requirements/0/status").asText()).isEqualTo("missing");
        assertThat(result.at("/requirements/0/evidence").asText()).isEmpty();
        assertThat(result.at("/requirements/1/status").asText()).isEqualTo("matched");
        assertThat(result.at("/requirements/2/status").asText()).isEqualTo("missing");
    }

    @Test
    void stripeSignatureRequiresAuthenticFreshPayload() throws Exception {
        String body = "{\"id\":\"evt_test\"}";
        String secret = "whsec_test";
        long now = 1_800_000_000L;
        var mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String signature =
            "t=" +
            now +
            ",v1=" +
            HexFormat.of().formatHex(
                mac.doFinal((now + "." + body).getBytes(StandardCharsets.UTF_8))
            );
        assertThat(BillingService.validSignature(body, signature, secret, now)).isTrue();
        assertThat(BillingService.validSignature(body + " ", signature, secret, now)).isFalse();
        assertThat(BillingService.validSignature(body, signature, secret, now + 301)).isFalse();
        assertThat(BillingService.validSignature(body, signature, "", now)).isFalse();
        assertThat(BillingService.validSignature(body, null, secret, now)).isFalse();
    }
}
