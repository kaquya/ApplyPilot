package ch.applypilot;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
class WorkspaceService {

    final EntryRepository entries;
    final AccountRepository accounts;
    final ObjectMapper json;

    @Value("${app.billing}")
    boolean billing;

    WorkspaceService(EntryRepository entries, AccountRepository accounts, ObjectMapper json) {
        this.entries = entries;
        this.accounts = accounts;
        this.json = json;
    }

    enum Status {
        INTERESTED,
        APPLIED,
        INTERVIEW,
        OFFER,
        REJECTED,
    }

    record JobInput(
        @NotBlank @Size(max = 150) String title,
        @NotBlank @Size(max = 150) String company,
        @NotNull Status status,
        @Size(max = 100) String location,
        @Size(max = 30) String canton,
        @Size(max = 2000) String url,
        @Size(max = 80) String source,
        @Size(max = 40000) String description,
        @Min(0) @Max(10000000) Integer salaryMin,
        @Min(0) @Max(10000000) Integer salaryMax,
        @Min(1) @Max(100) Integer workload,
        @Size(max = 100) String contactName,
        @Email @Size(max = 254) String contactEmail,
        LocalDate appliedDate,
        OffsetDateTime interviewDate,
        LocalDate followUpDate,
        @Size(max = 10000) String notes,
        UUID profileId,
        @Pattern(regexp = "en|de|fr|pl") String language
    ) {}

    record ProfileInput(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Pattern(regexp = "en|de|fr|pl") String language,
        @NotBlank @Size(max = 40000) String text,
        UUID documentId
    ) {}

    record JobEdit(@Min(0) long version, @NotNull @Valid JobInput data) {}

    record ProfileEdit(@Min(0) long version, @NotNull @Valid ProfileInput data) {}

    @Transactional(readOnly = true)
    List<ObjectNode> list(UUID owner, String kind) {
        return entries
            .findByOwnerIdAndKindOrderByCreatedAtDesc(owner, kind)
            .stream()
            .map(this::view)
            .toList();
    }

    Entry owned(UUID owner, UUID id, String kind) {
        var entry = entries
            .findByIdAndOwnerId(id, owner)
            .orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Record not found.")
            );
        if (!entry.kind.equals(kind)) throw new ResponseStatusException(
            HttpStatus.NOT_FOUND,
            "Record not found."
        );
        return entry;
    }

    ObjectNode data(Entry e) {
        try {
            return (ObjectNode) json.readTree(e.payload);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    ObjectNode view(Entry e) {
        var node = data(e);
        node.put("id", e.id.toString());
        node.put("version", e.version);
        node.put("createdAt", e.createdAt.toString());
        return node;
    }

    @Transactional
    ObjectNode saveJob(UUID owner, UUID id, long version, JobInput input) {
        accounts.lockById(owner).orElseThrow();
        if (
            input.salaryMin() != null &&
            input.salaryMax() != null &&
            input.salaryMin() > input.salaryMax()
        ) throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "Salary minimum must not exceed maximum."
        );
        if (input.url() != null && !input.url().isBlank()) {
            var uri = java.net.URI.create(input.url());
            if (
                !Set.of("http", "https").contains(uri.getScheme()) || uri.getHost() == null
            ) throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Use a valid http or https job URL."
            );
        }
        if (input.profileId() != null) owned(owner, input.profileId(), "profile");
        var entry = id == null ? new Entry() : owned(owner, id, "job");
        if (id != null && entry.version != version) throw new ResponseStatusException(
            HttpStatus.CONFLICT,
            "This application changed. Refresh and try again."
        );
        if (
            id == null &&
            billing &&
            accounts.findById(owner).orElseThrow().plan.equals("FREE") &&
            entries.countByOwnerIdAndKind(owner, "job") >= 10
        ) throw new ResponseStatusException(
            HttpStatus.PAYMENT_REQUIRED,
            "The Free plan includes 10 applications."
        );
        ObjectNode node = json.valueToTree(input);
        for (String field : List.of(
            "location",
            "canton",
            "url",
            "source",
            "description",
            "contactName",
            "contactEmail",
            "notes"
        ))
            if (node.path(field).isNull()) node.put(field, "");
        if (node.path("language").isNull()) node.put("language", "en");
        if (entry.payload != null) {
            var old = data(entry);
            boolean sameSource =
                Objects.equals(old.get("description"), node.get("description")) &&
                Objects.equals(old.get("profileId"), node.get("profileId")) &&
                Objects.equals(old.get("language"), node.get("language")) &&
                Objects.equals(old.get("title"), node.get("title")) &&
                Objects.equals(old.get("company"), node.get("company"));
            if (sameSource) for (String key : List.of(
                "analysis",
                "coverLetter",
                "interviewPrep",
                "tailoring"
            ))
                if (old.has(key)) node.set(key, old.get(key));
        }
        if (input.status() != Status.INTERESTED && input.appliedDate() == null) node.put(
            "appliedDate",
            LocalDate.now(ZoneId.of("Europe/Zurich")).toString()
        );
        entry.ownerId = owner;
        entry.kind = "job";
        entry.payload = node.toString();
        entry.updatedAt = Instant.now();
        return view(entries.saveAndFlush(entry));
    }

    @Transactional
    ObjectNode saveProfile(UUID owner, UUID id, long version, ProfileInput input) {
        accounts.lockById(owner).orElseThrow();
        if (
            id == null && entries.countByOwnerIdAndKind(owner, "profile") >= 25
        ) throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "You can keep up to 25 CV profiles."
        );
        var entry = id == null ? new Entry() : owned(owner, id, "profile");
        if (id != null && entry.version != version) throw new ResponseStatusException(
            HttpStatus.CONFLICT,
            "This profile changed. Refresh and try again."
        );
        entry.ownerId = owner;
        entry.kind = "profile";
        entry.payload = json.valueToTree(input).toString();
        entry.updatedAt = Instant.now();
        if (id != null) for (var job : entries.findByOwnerIdAndKindOrderByCreatedAtDesc(
            owner,
            "job"
        )) {
            var node = data(job);
            if (id.toString().equals(node.path("profileId").asText())) {
                node.remove(List.of("analysis", "coverLetter", "interviewPrep", "tailoring"));
                job.payload = node.toString();
            }
        }
        return view(entries.saveAndFlush(entry));
    }

    @Transactional
    void delete(UUID owner, UUID id, String kind) {
        var entry = owned(owner, id, kind);
        if (kind.equals("profile")) for (var job : entries.findByOwnerIdAndKindOrderByCreatedAtDesc(
            owner,
            "job"
        ))
            if (
                id.toString().equals(data(job).path("profileId").asText())
            ) throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Remove this profile from linked applications first."
            );
        entries.delete(entry);
    }

    @Transactional
    ObjectNode storeResult(
        UUID owner,
        UUID jobId,
        long version,
        UUID profileId,
        long profileVersion,
        String key,
        JsonNode result
    ) {
        accounts.lockById(owner).orElseThrow();
        if (
            owned(owner, profileId, "profile").version != profileVersion
        ) throw new ResponseStatusException(
            HttpStatus.CONFLICT,
            "Your CV changed during generation. Please try again."
        );
        var entry = owned(owner, jobId, "job");
        if (entry.version != version) throw new ResponseStatusException(
            HttpStatus.CONFLICT,
            "The application changed during generation. Please try again."
        );
        var data = data(entry);
        data.set(key, result);
        entry.payload = data.toString();
        entry.updatedAt = Instant.now();
        return view(entries.saveAndFlush(entry));
    }
}
