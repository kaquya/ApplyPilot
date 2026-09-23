package ch.applypilot;

import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

@Component
class OAuthAccounts implements AuthenticationSuccessHandler {

    private final AccountRepository accounts;

    @Value("${app.registration}")
    boolean registration;

    OAuthAccounts(AccountRepository accounts) {
        this.accounts = accounts;
    }

    @Override
    public void onAuthenticationSuccess(
        HttpServletRequest req,
        HttpServletResponse res,
        Authentication authentication
    ) throws IOException {
        OidcUser user = (OidcUser) authentication.getPrincipal();
        if (!Boolean.TRUE.equals(user.getEmailVerified())) {
            req.getSession().invalidate();
            res.sendRedirect("/?authError=unverified");
            return;
        }
        var existing = accounts.findByGoogleSubject(user.getSubject());
        Account account;
        if (existing.isPresent()) account = existing.get();
        else {
            // Never silently link an existing password account by email.
            if (
                !registration ||
                accounts.findByEmail(user.getEmail().toLowerCase(Locale.ROOT)).isPresent()
            ) {
                req.getSession().invalidate();
                res.sendRedirect("/?authError=existing");
                return;
            }
            account = new Account();
            account.email = user.getEmail().toLowerCase(Locale.ROOT);
            account.name = user.getFullName() == null ? "Applicant" : user.getFullName();
            account.googleSubject = user.getSubject();
            account.emailVerified = true;
            accounts.saveAndFlush(account);
        }
        AuthController.signIn(account, req, res);
        res.sendRedirect("/");
    }
}
