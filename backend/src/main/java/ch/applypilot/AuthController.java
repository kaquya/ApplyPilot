package ch.applypilot;

import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
class AuthController {

    private final AccountRepository accounts;
    private final PasswordEncoder passwords;
    private final RateLimit limits;

    @Value("${app.registration}")
    boolean registration;

    @Value("${app.openai-key}")
    String aiKey;

    @Value("${app.google-id}")
    String googleId;

    @Value("${app.google-secret}")
    String googleSecret;

    @Value("${app.billing}")
    boolean billing;

    @Value("${app.resend-key}")
    String resendKey;

    @Value("${app.email-from}")
    String emailFrom;

    AuthController(AccountRepository accounts, PasswordEncoder passwords, RateLimit limits) {
        this.accounts = accounts;
        this.passwords = passwords;
        this.limits = limits;
    }

    record Credentials(
        @Email @NotBlank @Size(max = 254) String email,
        @NotBlank @Size(min = 12, max = 72) String password,
        @Size(max = 100) String name
    ) {}

    @GetMapping("/health")
    Map<String, String> health() {
        return Map.of("status", "ok");
    }

    @GetMapping("/config")
    Map<String, Object> config() {
        return Map.of(
            "ai",
            !aiKey.isBlank(),
            "google",
            !googleId.isBlank() && !googleSecret.isBlank(),
            "billing",
            billing,
            "registration",
            registration,
            "email",
            !resendKey.isBlank() && !emailFrom.isBlank()
        );
    }

    @GetMapping("/auth/csrf")
    Map<String, String> csrf(CsrfToken token) {
        return Map.of("token", token.getToken());
    }

    @PostMapping("/auth/register")
    Map<String, Object> register(
        @Valid @RequestBody Credentials body,
        HttpServletRequest req,
        HttpServletResponse res
    ) {
        limits.check("auth:" + req.getRemoteAddr(), 20);
        if (!registration) throw new ResponseStatusException(
            HttpStatus.FORBIDDEN,
            "Registration is currently closed."
        );
        if (
            body.password().getBytes(StandardCharsets.UTF_8).length > 72
        ) throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "Password must be at most 72 UTF-8 bytes."
        );
        if (body.name() == null || body.name().isBlank()) throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "Enter your name."
        );
        var account = new Account();
        account.email = body.email().strip().toLowerCase(Locale.ROOT);
        account.name = body.name().strip();
        account.passwordHash = passwords.encode(body.password());
        accounts.saveAndFlush(account);
        signIn(account, req, res);
        return view(account);
    }

    @PostMapping("/auth/login")
    Map<String, Object> login(
        @Valid @RequestBody Credentials body,
        HttpServletRequest req,
        HttpServletResponse res
    ) {
        limits.check("auth:" + req.getRemoteAddr(), 20);
        var account = accounts.findByEmail(body.email().strip().toLowerCase(Locale.ROOT));
        // Always run BCrypt, including for unknown email addresses.
        String hash = account
            .map(a -> a.passwordHash)
            .filter(Objects::nonNull)
            .orElse("$2a$12$R9h/cIPz0gi.URNNX3kh2OPST9/PgBkqquzi.Ss7KIUgO2t0jWMUW");
        boolean valid = passwords.matches(body.password(), hash);
        if (
            !valid || account.isEmpty() || account.get().passwordHash == null
        ) throw new ResponseStatusException(
            HttpStatus.UNAUTHORIZED,
            "Email or password is incorrect."
        );
        signIn(account.get(), req, res);
        return view(account.get());
    }

    @GetMapping("/auth/me")
    Map<String, Object> me(Authentication auth) {
        return view(accounts.findById(owner(auth)).orElseThrow());
    }

    static UUID owner(Authentication auth) {
        return UUID.fromString(auth.getName());
    }

    static Map<String, Object> view(Account a) {
        return Map.of(
            "id",
            a.id,
            "name",
            a.name,
            "email",
            a.email,
            "plan",
            a.plan,
            "emailVerified",
            a.emailVerified,
            "emailReminders",
            a.emailReminders
        );
    }

    static void signIn(Account account, HttpServletRequest req, HttpServletResponse res) {
        if (req.getSession(false) != null) req.changeSessionId();
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(
            new UsernamePasswordAuthenticationToken(account.id.toString(), null, List.of())
        );
        SecurityContextHolder.setContext(context);
        new HttpSessionSecurityContextRepository().saveContext(context, req, res);
        req.getSession().removeAttribute(HttpSessionCsrfTokenRepositoryName.VALUE);
    }

    private static class HttpSessionCsrfTokenRepositoryName {

        static final String VALUE =
            "org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository.CSRF_TOKEN";
    }
}
