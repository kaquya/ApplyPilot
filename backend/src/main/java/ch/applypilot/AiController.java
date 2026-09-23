package ch.applypilot;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
class AiController {

    private final AiService ai;

    AiController(AiService ai) {
        this.ai = ai;
    }

    record Generate(
        @NotNull @Pattern(regexp = "analysis|coverLetter|interviewPrep|tailoring") String kind
    ) {}

    @PostMapping("/api/jobs/{id}/ai")
    Object generate(
        Authentication auth,
        @PathVariable UUID id,
        @Valid @RequestBody Generate request
    ) {
        return ai.generate(AuthController.owner(auth), id, request.kind());
    }
}
