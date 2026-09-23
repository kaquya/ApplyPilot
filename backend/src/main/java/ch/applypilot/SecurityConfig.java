package ch.applypilot;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.registration.*;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.*;

@Configuration
class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    SecurityFilterChain security(
        HttpSecurity http,
        OAuthAccounts oauth,
        @Value("${app.google-id}") String clientId,
        @Value("${app.google-secret}") String clientSecret
    ) throws Exception {
        http.authorizeHttpRequests(a ->
            a
                .requestMatchers(
                    "/api/auth/csrf",
                    "/api/auth/login",
                    "/api/auth/register",
                    "/api/config",
                    "/api/health",
                    "/api/billing/webhook",
                    "/api/email/verify",
                    "/oauth2/**",
                    "/login/oauth2/**",
                    "/error"
                )
                .permitAll()
                .anyRequest()
                .authenticated()
        )
            .csrf(c ->
                c
                    .csrfTokenRepository(new HttpSessionCsrfTokenRepository())
                    .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                    .ignoringRequestMatchers("/api/billing/webhook")
            )
            .exceptionHandling(e ->
                e.authenticationEntryPoint((req, res, ex) -> {
                    res.setStatus(401);
                    res.setContentType("application/json");
                    res.getWriter().write("{\"message\":\"Please sign in to continue.\"}");
                })
            )
            .logout(l ->
                l
                    .logoutUrl("/api/auth/logout")
                    .logoutSuccessHandler((req, res, auth) -> res.setStatus(204))
                    .deleteCookies("SESSION")
            );
        if (!clientId.isBlank() && !clientSecret.isBlank()) {
            var google = ClientRegistration.withRegistrationId("google")
                .clientId(clientId)
                .clientSecret(clientSecret)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope(List.of("openid", "profile", "email"))
                .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
                .tokenUri("https://oauth2.googleapis.com/token")
                .jwkSetUri("https://www.googleapis.com/oauth2/v3/certs")
                .userInfoUri("https://openidconnect.googleapis.com/v1/userinfo")
                .userNameAttributeName("sub")
                .clientName("Google")
                .build();
            http.oauth2Login(o ->
                o
                    .clientRegistrationRepository(new InMemoryClientRegistrationRepository(google))
                    .successHandler(oauth)
                    .failureUrl("/?authError=google")
            );
        }
        return http.build();
    }
}
