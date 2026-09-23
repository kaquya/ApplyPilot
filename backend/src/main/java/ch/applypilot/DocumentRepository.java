package ch.applypilot;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

interface DocumentRepository extends JpaRepository<Document, UUID> {
    Optional<Document> findByIdAndOwnerId(UUID id, UUID owner);
    List<Document> findByOwnerIdOrderByCreatedAtDesc(UUID owner);
    List<Document> findByOwnerIdAndEntryId(UUID owner, UUID entry);
    long countByOwnerId(UUID owner);
}
