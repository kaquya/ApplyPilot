package ch.applypilot;

import org.slf4j.*;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.*;

@Configuration
@EnableScheduling
class ReminderScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReminderScheduler.class);
    private final AccountRepository accounts;
    private final EmailService email;

    ReminderScheduler(AccountRepository accounts, EmailService email) {
        this.accounts = accounts;
        this.email = email;
    }

    @Scheduled(cron = "0 0 9-18 * * *", zone = "Europe/Zurich")
    void reminders() {
        if (!email.configured()) return;
        for (var account : accounts.findByEmailVerifiedTrueAndEmailRemindersTrue())
            try {
                email.digest(account.id);
            } catch (Exception e) {
                log.warn("Reminder delivery failed; the next scheduled run will retry.");
            }
    }
}
