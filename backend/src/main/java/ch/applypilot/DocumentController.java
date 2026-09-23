package ch.applypilot;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/documents")
class DocumentController {

    private final DocumentService documents;

    DocumentController(DocumentService documents) {
        this.documents = documents;
    }

    @GetMapping
    Object list(Authentication a) {
        return documents.list(AuthController.owner(a));
    }

    @PostMapping
    Object upload(
        Authentication a,
        @RequestParam MultipartFile file,
        @RequestParam(required = false) UUID entryId
    ) throws IOException {
        return documents.upload(AuthController.owner(a), entryId, file);
    }

    @GetMapping("/{id}")
    ResponseEntity<byte[]> download(Authentication a, @PathVariable UUID id) throws IOException {
        var d = documents.owned(AuthController.owner(a), id);
        return ResponseEntity.ok()
            .header(
                HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment()
                    .filename(d.filename, StandardCharsets.UTF_8)
                    .build()
                    .toString()
            )
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .contentType(MediaType.parseMediaType(d.contentType))
            .body(documents.read(d));
    }

    @DeleteMapping("/{id}")
    void delete(Authentication a, @PathVariable UUID id) throws IOException {
        documents.delete(AuthController.owner(a), id);
    }
}
