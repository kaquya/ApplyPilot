package ch.applypilot;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "accounts")
class Account {

    @Id
    UUID id = UUID.randomUUID();

    @Column(nullable = false, unique = true, length = 254)
    String email;

    @Column(nullable = false, length = 100)
    String name;

    @Column(name = "password_hash", length = 100)
    String passwordHash;

    @Column(name = "google_subject", unique = true)
    String googleSubject;

    @Column(nullable = false, length = 20)
    String plan = "FREE";

    @Column(name = "stripe_customer", length = 100)
    String stripeCustomer;

    @Column(name = "lifetime_payment")
    String lifetimePayment;

    @Column(name = "email_verified", nullable = false)
    boolean emailVerified;

    @Column(name = "email_reminders", nullable = false)
    boolean emailReminders;

    @Column(name = "verification_hash", length = 64)
    String verificationHash;

    @Column(name = "verification_expires")
    Instant verificationExpires;

    @Column(name = "last_digest")
    java.time.LocalDate lastDigest;

    @Column(nullable = false, length = 2)
    String language = "en";

    @Column(name = "created_at", nullable = false)
    Instant createdAt = Instant.now();
}
