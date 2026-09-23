package ch.applypilot;

import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
class ApiError {

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<?> status(ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode()).body(
            Map.of("message", ex.getReason() == null ? "Request failed" : ex.getReason())
        );
    }

    @ExceptionHandler({
        MethodArgumentNotValidException.class,
        IllegalArgumentException.class,
        org.springframework.http.converter.HttpMessageNotReadableException.class,
    })
    ResponseEntity<?> invalid(Exception ex) {
        return ResponseEntity.badRequest().body(
            Map.of("message", "Please check the submitted fields and their lengths.")
        );
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<?> conflict() {
        return ResponseEntity.status(409).body(
            Map.of("message", "This change conflicts with an existing record.")
        );
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ResponseEntity<?> stale() {
        return ResponseEntity.status(409).body(
            Map.of(
                "message",
                "This record changed in another window. Refresh before editing again."
            )
        );
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<?> large() {
        return ResponseEntity.status(413).body(Map.of("message", "Files must be 5 MB or smaller."));
    }
}
