package ch.applypilot;

import jakarta.validation.Valid;
import java.util.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
class WorkspaceController {

    private final WorkspaceService workspace;
    private final DocumentService documents;

    WorkspaceController(WorkspaceService workspace, DocumentService documents) {
        this.workspace = workspace;
        this.documents = documents;
    }

    @GetMapping("/jobs")
    Object jobs(Authentication a) {
        return workspace.list(AuthController.owner(a), "job");
    }

    @PostMapping("/jobs")
    Object createJob(Authentication a, @Valid @RequestBody WorkspaceService.JobInput body) {
        return workspace.saveJob(AuthController.owner(a), null, 0, body);
    }

    @PutMapping("/jobs/{id}")
    Object editJob(
        Authentication a,
        @PathVariable UUID id,
        @Valid @RequestBody WorkspaceService.JobEdit body
    ) {
        return workspace.saveJob(AuthController.owner(a), id, body.version(), body.data());
    }

    @DeleteMapping("/jobs/{id}")
    void deleteJob(Authentication a, @PathVariable UUID id) {
        documents.deleteForEntry(AuthController.owner(a), id, "job");
        workspace.delete(AuthController.owner(a), id, "job");
    }

    @GetMapping("/profiles")
    Object profiles(Authentication a) {
        return workspace.list(AuthController.owner(a), "profile");
    }

    @PostMapping("/profiles")
    Object createProfile(Authentication a, @Valid @RequestBody WorkspaceService.ProfileInput body) {
        if (body.documentId() != null) documents.owned(AuthController.owner(a), body.documentId());
        return workspace.saveProfile(AuthController.owner(a), null, 0, body);
    }

    @PutMapping("/profiles/{id}")
    Object editProfile(
        Authentication a,
        @PathVariable UUID id,
        @Valid @RequestBody WorkspaceService.ProfileEdit body
    ) {
        if (body.data().documentId() != null) documents.owned(
            AuthController.owner(a),
            body.data().documentId()
        );
        return workspace.saveProfile(AuthController.owner(a), id, body.version(), body.data());
    }

    @DeleteMapping("/profiles/{id}")
    void deleteProfile(Authentication a, @PathVariable UUID id) {
        workspace.delete(AuthController.owner(a), id, "profile");
    }

    @GetMapping("/export")
    Object export(Authentication a) {
        UUID owner = AuthController.owner(a);
        return Map.of(
            "applications",
            workspace.list(owner, "job"),
            "profiles",
            workspace.list(owner, "profile"),
            "documents",
            documents.list(owner)
        );
    }
}
