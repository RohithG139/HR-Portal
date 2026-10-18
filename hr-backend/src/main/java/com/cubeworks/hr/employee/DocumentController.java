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
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Map;

/**
 * REST controller for document uploads.
 *
 * <h2>Covered endpoints (spec §6)</h2>
 * <ul>
 *   <li>A07 {@code POST /api/documents} (multipart: {@code file}, {@code docType=CONTRACT}) —
 *       uploads a file and creates its metadata row, unlinked from any employee until
 *       {@link com.cubeworks.hr.onboarding.OnboardingService#createOnboarding} links it via
 *       {@code contractDocumentId}.</li>
 * </ul>
 *
 * <p>Controllers stay thin (R-CODE-1): the only logic here is wiring the multipart upload
 * to {@link DocumentStorageService} and persisting the {@link Document} metadata row.</p>
 */
@RestController
public class DocumentController {

    private final DocumentStorageService documentStorageService;
    private final DocumentRepository documentRepository;
    private final CurrentUser currentUser;
    private final Clock clock;

    /**
     * Constructor injection (R-CODE-1).
     *
     * @param documentStorageService stores the uploaded file bytes
     * @param documentRepository     persists the document metadata row
     * @param currentUser            resolves the authenticated employee from the JWT
     * @param clock                  application-wide clock bean (never call Instant.now() directly)
     */
    public DocumentController(final DocumentStorageService documentStorageService,
                               final DocumentRepository documentRepository,
                               final CurrentUser currentUser,
                               final Clock clock) {
        this.documentStorageService = documentStorageService;
        this.documentRepository = documentRepository;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    /**
     * A07 — Uploads a file (e.g. the signed contract) and records its metadata.
     *
     * <p>The document is created without an employee link (step 1 of onboarding happens
     * before the employee row exists); {@code OnboardingService.createOnboarding} links it
     * once the employee is created, via {@code contractDocumentId}.</p>
     *
     * @param file    the uploaded file
     * @param docType the document classification, e.g. {@code CONTRACT}
     * @return 201 with {@code documentId}
     */
    @PostMapping(value = "/api/documents", consumes = "multipart/form-data")
    @PreAuthorize("hasRole('HR')")
    public ResponseEntity<Map<String, Object>> uploadDocument(
            @RequestParam("file") final MultipartFile file,
            @RequestParam("docType") final String docType) throws IOException {
        final Employee caller = currentUser.require();
        final String storageKey = documentStorageService.store(file);
        final OffsetDateTime now = OffsetDateTime.now(clock);

        final Document document = new Document();
        document.setDocType(DocType.valueOf(docType));
        document.setFileName(file.getOriginalFilename() != null ? file.getOriginalFilename() : "document");
        document.setContentType(file.getContentType() != null ? file.getContentType() : "application/octet-stream");
        document.setSizeBytes(file.getSize());
        document.setStorageKey(storageKey);
        document.setUploadedBy(caller);
        document.setUploadedAt(now);
        document.setCreatedAt(now);
        document.setCreatedBy(caller.getId());
        document.setUpdatedAt(now);
        document.setUpdatedBy(caller.getId());

        final Document saved = documentRepository.save(document);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("documentId", saved.getId()));
    }
}
