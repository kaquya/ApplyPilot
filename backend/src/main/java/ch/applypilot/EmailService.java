package ch.applypilot;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
class EmailService {

    private final AccountRepository accounts;
    private final WorkspaceService workspace;
    private final ObjectMapper json;
    private final HttpClient http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();

    @Value("${app.resend-key}")
    String key;

    @Value("${app.email-from}")
    String from;

    @Value("${app.origin}")
    String origin;

    EmailService(AccountRepository accounts, WorkspaceService workspace, ObjectMapper json) {
        this.accounts = accounts;
        this.workspace = workspace;
        this.json = json;
    }

    boolean configured() {
        return !key.isBlank() && !from.isBlank();
    }

    static String hash(String value) {
        try {
            return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))
            );
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Transactional
    Map<String, Object> preference(UUID owner, boolean enabled, String language) {
        var account = accounts.lockById(owner).orElseThrow();
        account.language = language;
        if (!enabled) {
            account.emailReminders = false;
            account.verificationHash = null;
            account.verificationExpires = null;
            return AuthController.view(account);
        }
        if (!configured()) throw new ResponseStatusException(
            HttpStatus.SERVICE_UNAVAILABLE,
            "Email reminders are not connected yet."
        );
        if (account.emailVerified) {
            account.emailReminders = true;
            return AuthController.view(account);
        }
        if (
            account.verificationExpires != null &&
            account.verificationExpires.isAfter(Instant.now().plusSeconds(84600))
        ) throw new ResponseStatusException(
            HttpStatus.TOO_MANY_REQUESTS,
            "A verification email was already sent. Check your inbox or wait 30 minutes."
        );
        byte[] random = new byte[32];
        new SecureRandom().nextBytes(random);
        String token = HexFormat.of().formatHex(random);
        account.verificationHash = hash(token);
        account.verificationExpires = Instant.now().plusSeconds(86400);
        send(
            account.email,
            "Confirm your ApplyPilot reminders",
            "You requested job-search reminders from ApplyPilot. Confirm your email address using this link (valid for 24 hours):\n\n" +
                origin +
                "/?verify=" +
                token +
                "\n\nIf you did not request this, ignore this email. No reminders will be sent without verification.",
            "verify-" + account.id + "-" + account.verificationHash
        );
        return Map.of("verificationSent", true);
    }

    @Transactional
    void verify(String token) {
        if (token == null || !token.matches("[0-9a-f]{64}")) throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "Invalid verification link."
        );
        var match = accounts
            .findByVerificationHash(hash(token))
            .orElseThrow(() ->
                new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "This link has expired or was already used."
                )
            );
        var account = accounts.lockById(match.id).orElseThrow();
        if (
            !hash(token).equals(account.verificationHash) ||
            account.verificationExpires == null ||
            account.verificationExpires.isBefore(Instant.now())
        ) throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "This link has expired or was already used."
        );
        account.emailVerified = true;
        account.emailReminders = true;
        account.verificationHash = null;
        account.verificationExpires = null;
    }

    @Transactional
    void digest(UUID owner) {
        if (!configured()) return;
        var account = accounts.lockById(owner).orElseThrow();
        var today = LocalDate.now(ZoneId.of("Europe/Zurich"));
        if (
            !account.emailVerified || !account.emailReminders || today.equals(account.lastDigest)
        ) return;
        int followUps = 0,
            interviews = 0;
        for (var job : workspace.list(owner, "job")) {
            String status = job.path("status").asText();
            if (!Set.of("APPLIED", "INTERVIEW").contains(status)) continue;
            String follow = job.path("followUpDate").asText("");
            String applied = job.path("appliedDate").asText("");
            if (follow.isBlank() && status.equals("APPLIED") && !applied.isBlank()) follow =
                LocalDate.parse(applied).plusDays(8).toString();
            if (!follow.isBlank() && !LocalDate.parse(follow).isAfter(today)) followUps++;
            String interview = job.path("interviewDate").asText("");
            if (status.equals("INTERVIEW") && !interview.isBlank()) {
                var day = OffsetDateTime.parse(interview)
                    .atZoneSameInstant(ZoneId.of("Europe/Zurich"))
                    .toLocalDate();
                if (!day.isBefore(today) && !day.isAfter(today.plusDays(1))) interviews++;
            }
        }
        if (followUps == 0 && interviews == 0) return;
        String subject = switch (account.language) {
            case "de" -> "Deine nächsten Bewerbungsschritte";
            case "fr" -> "Vos prochaines étapes de candidature";
            case "pl" -> "Twoje kolejne kroki w rekrutacji";
            default -> "Your next application steps";
        };
        String summary = switch (account.language) {
            case "de" -> "Fällige Rückmeldungen: " +
                followUps +
                ". Gespräche heute oder morgen: " +
                interviews +
                ".";
            case "fr" -> "Relances à effectuer : " +
                followUps +
                ". Entretiens aujourd’hui ou demain : " +
                interviews +
                ".";
            case "pl" -> "Aplikacje wymagające przypomnienia: " +
                followUps +
                ". Rozmowy dzisiaj lub jutro: " +
                interviews +
                ".";
            default -> "Follow-ups to consider: " +
                followUps +
                ". Interviews today or tomorrow: " +
                interviews +
                ".";
        };
        send(
            account.email,
            subject,
            summary +
                "\n\nApplyPilot: " +
                origin +
                "\n\nManage or turn off email reminders in Settings.",
            "digest-" + account.id + "-" + today
        );
        account.lastDigest = today;
    }

    private void send(String to, String subject, String text, String idempotency) {
        try {
            var body = json.writeValueAsString(
                Map.of("from", from, "to", List.of(to), "subject", subject, "text", text)
            );
            var response = http.send(
                HttpRequest.newBuilder(URI.create("https://api.resend.com/emails"))
                    .timeout(Duration.ofSeconds(20))
                    .header("Authorization", "Bearer " + key)
                    .header("Content-Type", "application/json")
                    .header("Idempotency-Key", idempotency)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build(),
                HttpResponse.BodyHandlers.discarding()
            );
            if (
                response.statusCode() < 200 || response.statusCode() >= 300
            ) throw new ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                "The email could not be sent. Please try again later."
            );
        } catch (ResponseStatusException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Email request interrupted."
            );
        } catch (Exception e) {
            throw new ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                "Email delivery is unavailable."
            );
        }
    }
}
