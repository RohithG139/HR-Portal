package com.cubeworks.hr.employee;

import com.cubeworks.hr.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

/**
 * REST controller for document uploads.
 *
 * <h2>Covered endpoints (spec §6)</h2>
 * <ul>
 *   <li>A07 {@code POST /api/documents} (multipart: {@code file}, {@code docType=CONTRACT|POLICY}) —
 *       uploads a file and creates its metadata row, unlinked from any employee until
 *       {@link com.cubeworks.hr.onboarding.OnboardingService#createOnboarding} links it via
 *       {@code contractDocumentId}.</li>
 * </ul>
 *
 * <p>Controllers stay thin (R-CODE-1): validation (allowed doc type, file signature), storage
 * and the audit entry live in {@link DocumentService}.</p>
 */
@RestController
public class DocumentController {

    private final DocumentService documentService;
    private final CurrentUser currentUser;

    /**
     * Constructor injection (R-CODE-1).
     *
     * @param documentService validates, stores and records the upload
     * @param currentUser     resolves the authenticated employee from the JWT
     */
    public DocumentController(final DocumentService documentService, final CurrentUser currentUser) {
        this.documentService = documentService;
        this.currentUser = currentUser;
    }

    /**
     * A07 — Uploads a file (the signed contract, or a policy PDF) and records its metadata.
     *
     * @param file    the uploaded file
     * @param docType the document classification: {@code CONTRACT} or {@code POLICY}
     * @return 201 with {@code documentId} / 400 VALIDATION_FAILED
     */
    @PostMapping(value = "/api/documents", consumes = "multipart/form-data")
    @PreAuthorize("hasRole('HR')")
    public ResponseEntity<Map<String, Object>> uploadDocument(
            @RequestParam("file") final MultipartFile file,
            @RequestParam("docType") final String docType) throws IOException {
        final Long id = documentService.upload(file, docType, currentUser.require());
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("documentId", id));
    }
}
