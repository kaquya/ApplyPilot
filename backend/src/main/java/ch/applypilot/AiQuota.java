package ch.applypilot;

import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
class AiQuota {

    private final AccountRepository accounts;
    private final JdbcTemplate jdbc;

    @Value("${app.billing}")
    boolean billing;

    AiQuota(AccountRepository accounts, JdbcTemplate jdbc) {
        this.accounts = accounts;
        this.jdbc = jdbc;
    }

    @Transactional
    void reserve(UUID owner, String kind) {
        var account = accounts.lockById(owner).orElseThrow();
        if (
            billing &&
            (account.plan.equals("FREE") ||
                (account.plan.equals("PLUS") && !kind.equals("analysis")))
        ) throw new ResponseStatusException(
            HttpStatus.PAYMENT_REQUIRED,
            "This feature requires a higher plan."
        );
        int limit = account.plan.equals("PRO") ? 200 : account.plan.equals("LIFETIME") ? 50 : 100;
        var month = LocalDate.now(ZoneOffset.UTC)
            .withDayOfMonth(1)
            .atStartOfDay()
            .toInstant(ZoneOffset.UTC);
        Integer count = jdbc.queryForObject(
            "select count(*) from ai_usage where owner_id = ? and created_at >= ?",
            Integer.class,
            owner,
            java.sql.Timestamp.from(month)
        );
        if (count != null && count >= limit) throw new ResponseStatusException(
            HttpStatus.TOO_MANY_REQUESTS,
            "Your monthly AI request allowance has been reached. It resets on the first day of the month (UTC)."
        );
        jdbc.update(
            "insert into ai_usage(id,owner_id,created_at) values (?,?,?)",
            UUID.randomUUID(),
            owner,
            java.sql.Timestamp.from(Instant.now())
        );
    }
}
