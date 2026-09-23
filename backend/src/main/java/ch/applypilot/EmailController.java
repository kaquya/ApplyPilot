package ch.applypilot;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/email")
class EmailController {

    private final EmailService email;
    private final RateLimit limits;

    EmailController(EmailService email, RateLimit limits) {
        this.email = email;
        this.limits = limits;
    }

    record Preference(boolean enabled, @NotNull @Pattern(regexp = "en|de|fr|pl") String language) {}

    record Verify(@NotNull @Pattern(regexp = "[0-9a-f]{64}") String token) {}

    @PostMapping("/reminders")
    Object preference(Authentication auth, @Valid @RequestBody Preference input) {
        limits.check("email:" + auth.getName(), 3);
        return email.preference(AuthController.owner(auth), input.enabled(), input.language());
    }

    @PostMapping("/verify")
    Object verify(@Valid @RequestBody Verify input) {
        email.verify(input.token());
        return Map.of("verified", true);
    }
}
