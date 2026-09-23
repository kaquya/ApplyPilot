package ch.applypilot;

import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.jpa.repository.*;

interface AccountRepository extends JpaRepository<Account, UUID> {
    Optional<Account> findByEmail(String email);
    Optional<Account> findByGoogleSubject(String subject);
    Optional<Account> findByStripeCustomer(String customer);
    Optional<Account> findByLifetimePayment(String payment);
    Optional<Account> findByVerificationHash(String hash);
    List<Account> findByEmailVerifiedTrueAndEmailRemindersTrue();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.id = :id")
    Optional<Account> lockById(UUID id);
}
