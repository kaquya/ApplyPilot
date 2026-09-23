package ch.applypilot;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "documents")
class Document {

    @Id
    UUID id = UUID.randomUUID();

    @Column(name = "owner_id", nullable = false)
    UUID ownerId;

    @Column(name = "entry_id")
    UUID entryId;

    @Column(nullable = false)
    String filename;

    @Column(name = "content_type", nullable = false, length = 100)
    String contentType;

    @Column(name = "size_bytes", nullable = false)
    long sizeBytes;

    @Column(name = "storage_key", nullable = false, length = 300)
    String storageKey;

    @Column(name = "extracted_text", nullable = false, columnDefinition = "text")
    String extractedText;

    @Column(name = "created_at", nullable = false)
    Instant createdAt = Instant.now();
}
