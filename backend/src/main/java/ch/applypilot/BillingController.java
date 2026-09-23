package ch.applypilot;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/billing")
class BillingController {

    private final BillingService billing;
    private final RateLimit limits;

    BillingController(BillingService billing, RateLimit limits) {
        this.billing = billing;
        this.limits = limits;
    }

    record Checkout(@NotNull @Pattern(regexp = "PLUS|PRO|LIFETIME") String plan) {}

    @PostMapping("/checkout")
    Object checkout(Authentication auth, @Valid @RequestBody Checkout input) {
        limits.check("billing:" + auth.getName(), 5);
        return Map.of("url", billing.checkout(AuthController.owner(auth), input.plan()));
    }

    @PostMapping("/portal")
    Object portal(Authentication auth) {
        limits.check("billing:" + auth.getName(), 5);
        return Map.of("url", billing.portal(AuthController.owner(auth)));
    }

    @PostMapping("/webhook")
    void webhook(
        @RequestBody String payload,
        @RequestHeader(value = "Stripe-Signature", required = false) String signature
    ) {
        billing.webhook(payload, signature);
    }
}
