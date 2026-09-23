package ch.applypilot;

import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Service
class DocumentService {

    private final DocumentRepository documents;
    private final WorkspaceService workspace;
    private final Path root;
    private final String bucket;
    private final S3Client s3;

    DocumentService(
        DocumentRepository documents,
        WorkspaceService workspace,
        @Value("${app.storage-path}") String path,
        @Value("${app.s3-bucket}") String bucket,
        @Value("${app.s3-endpoint}") String endpoint,
        @Value("${app.s3-region}") String region
    ) throws IOException {
        this.documents = documents;
        this.workspace = workspace;
        this.root = Path.of(path).toAbsolutePath().normalize();
        this.bucket = bucket;
        if (bucket.isBlank()) {
            Files.createDirectories(root);
            s3 = null;
        } else {
            var builder = S3Client.builder().region(Region.of(region)).forcePathStyle(true);
            if (!endpoint.isBlank()) builder.endpointOverride(URI.create(endpoint));
            s3 = builder.build();
        }
    }

    Document owned(UUID owner, UUID id) {
        return documents
            .findByIdAndOwnerId(id, owner)
            .orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found.")
            );
    }

    Map<String, Object> view(Document d) {
        return Map.of(
            "id",
            d.id,
            "entryId",
            d.entryId == null ? "" : d.entryId,
            "filename",
            d.filename,
            "size",
            d.sizeBytes,
            "createdAt",
            d.createdAt,
            "text",
            d.extractedText
        );
    }

    List<Map<String, Object>> list(UUID owner) {
        return documents.findByOwnerIdOrderByCreatedAtDesc(owner).stream().map(this::view).toList();
    }

    synchronized Map<String, Object> upload(UUID owner, UUID entryId, MultipartFile file)
        throws IOException {
        if (entryId != null) workspace.owned(owner, entryId, "job");
        if (documents.countByOwnerId(owner) >= 200) throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "Document limit reached (200 files)."
        );
        if (file.isEmpty() || file.getSize() > 5 * 1024 * 1024) throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "Choose a PDF or text file up to 5 MB."
        );
        byte[] bytes = file.getBytes();
        String name = Objects.requireNonNullElse(file.getOriginalFilename(), "document").replaceAll(
            "[\\\\/\\r\\n\\p{Cntrl}]",
            "_"
        );
        if (name.length() > 200) name = name.substring(name.length() - 200);
        String text;
        String type;
        if (
            name.toLowerCase(Locale.ROOT).endsWith(".pdf") &&
            bytes.length > 5 &&
            new String(bytes, 0, 5, StandardCharsets.US_ASCII).equals("%PDF-")
        ) {
            try (var pdf = Loader.loadPDF(bytes)) {
                if (
                    pdf.isEncrypted() || pdf.getNumberOfPages() > 40
                ) throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Use an unencrypted PDF with at most 40 pages."
                );
                text = new PDFTextStripper().getText(pdf);
            } catch (ResponseStatusException e) {
                throw e;
            } catch (IOException e) {
                throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "This PDF could not be read."
                );
            }
            type = "application/pdf";
        } else if (name.toLowerCase(Locale.ROOT).endsWith(".txt")) {
            text = new String(bytes, StandardCharsets.UTF_8);
            if (text.indexOf('\0') >= 0) throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Use a UTF-8 text file."
            );
            type = "text/plain";
        } else throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "Supported documents: PDF and UTF-8 text."
        );
        if (text.length() > 40000) throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "Document text exceeds 40,000 characters."
        );
        var d = new Document();
        d.ownerId = owner;
        d.entryId = entryId;
        d.filename = name;
        d.contentType = type;
        d.sizeBytes = bytes.length;
        d.extractedText = text;
        d.storageKey = owner + "/" + d.id;
        if (s3 == null) {
            var target = resolve(d.storageKey);
            Files.createDirectories(target.getParent());
            Files.write(target, bytes, StandardOpenOption.CREATE_NEW);
        } else s3.putObject(
            b -> b.bucket(bucket).key(d.storageKey).contentType(type),
            RequestBody.fromBytes(bytes)
        );
        try {
            documents.saveAndFlush(d);
        } catch (RuntimeException e) {
            removeBytes(d);
            throw e;
        }
        return view(d);
    }

    byte[] read(Document d) throws IOException {
        return s3 == null
            ? Files.readAllBytes(resolve(d.storageKey))
            : s3.getObjectAsBytes(b -> b.bucket(bucket).key(d.storageKey)).asByteArray();
    }

    Path resolve(String key) {
        var target = root.resolve(key).normalize();
        if (!target.startsWith(root)) throw new IllegalArgumentException("Invalid storage key");
        return target;
    }

    void removeBytes(Document d) throws IOException {
        if (s3 == null) Files.deleteIfExists(resolve(d.storageKey));
        else s3.deleteObject(b -> b.bucket(bucket).key(d.storageKey));
    }

    void delete(UUID owner, UUID id) throws IOException {
        var d = owned(owner, id);
        for (var p : workspace.list(owner, "profile"))
            if (
                id.toString().equals(p.path("documentId").asText())
            ) throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Remove this document from its CV profile first."
            );
        removeBytes(d);
        documents.delete(d);
    }

    void deleteForEntry(UUID owner, UUID id, String kind) {
        workspace.owned(owner, id, kind);
        for (var d : documents.findByOwnerIdAndEntryId(owner, id))
            try {
                delete(owner, d.id);
            } catch (IOException e) {
                throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Could not remove document storage. Please retry."
                );
            }
    }
}
