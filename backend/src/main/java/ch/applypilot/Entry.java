package ch.applypilot;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "workspace_entries")
class Entry {

    @Id
    UUID id = UUID.randomUUID();

    @Column(name = "owner_id", nullable = false)
    UUID ownerId;

    @Column(nullable = false, length = 20)
    String kind;

    @Column(nullable = false, columnDefinition = "text")
    String payload;

    @Column(name = "created_at", nullable = false)
    Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    Instant updatedAt = Instant.now();

    @Version
    long version;
}
