package ch.applypilot;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

interface EntryRepository extends JpaRepository<Entry, UUID> {
    List<Entry> findByOwnerIdAndKindOrderByCreatedAtDesc(UUID ownerId, String kind);
    Optional<Entry> findByIdAndOwnerId(UUID id, UUID ownerId);
    long countByOwnerIdAndKind(UUID ownerId, String kind);
}
