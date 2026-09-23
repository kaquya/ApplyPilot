package ch.applypilot;

import com.fasterxml.jackson.databind.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
class StripeClient {

    @Value("${app.stripe-key}")
    String key;

    private final ObjectMapper json;
    private final HttpClient http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();

    StripeClient(ObjectMapper json) {
        this.json = json;
    }

    JsonNode get(String path) {
        return request(path, null, null);
    }

    JsonNode post(String path, Map<String, String> data, String idempotency) {
        return request(path, data, idempotency);
    }

    private JsonNode request(String path, Map<String, String> data, String idempotency) {
        if (key.isBlank()) throw new ResponseStatusException(
            HttpStatus.SERVICE_UNAVAILABLE,
            "Payments are not configured."
        );
        try {
            var builder = HttpRequest.newBuilder(URI.create("https://api.stripe.com/v1/" + path))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + key);
            if (data != null) {
                builder.header("Content-Type", "application/x-www-form-urlencoded");
                builder.POST(
                    HttpRequest.BodyPublishers.ofString(
                        data
                            .entrySet()
                            .stream()
                            .map(
                                e ->
                                    URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) +
                                    "=" +
                                    URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8)
                            )
                            .collect(Collectors.joining("&"))
                    )
                );
            }
            if (idempotency != null) builder.header("Idempotency-Key", idempotency);
            var response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (
                response.statusCode() < 200 || response.statusCode() >= 300
            ) throw new ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                "The payment provider could not complete this request."
            );
            return json.readTree(response.body());
        } catch (ResponseStatusException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Payment request interrupted."
            );
        } catch (Exception e) {
            throw new ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                "The payment provider is unavailable."
            );
        }
    }
}
