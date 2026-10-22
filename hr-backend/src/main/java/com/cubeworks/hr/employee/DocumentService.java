package com.cubeworks.hr.employee;

import com.cubeworks.hr.common.audit.AuditLogService;
import com.cubeworks.hr.common.error.ApiException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Upload logic for A07 {@code POST /api/documents} (M3 hardening, gate-5 follow-up).
 *
 * <ul>
 *   <li>{@code docType} must be one HR may upload here: {@link DocType#CONTRACT} (step-1
 *       contract file, FRD-02 §4.1) or {@link DocType#POLICY} (policy PDF linked to a
 *       POLICY_ACK template item, D-07). Anything else → {@code 400 VALIDATION_FAILED}.</li>
 *   <li>The stored content type comes from the file's own signature (magic bytes), never from
 *       the client's {@code Content-Type}: PDF ({@code %PDF}) for both types, plus PNG/JPEG
 *       scans for a contract. Any other content → {@code 400 VALIDATION_FAILED}.</li>
 *   <li>The upload is audited (R-API-5).</li>
 * </ul>
 */
@Service
public class DocumentService {

    /** Document types HR may upload through A07. */
    private static final Set<DocType> UPLOADABLE = Set.of(DocType.CONTRACT, DocType.POLICY);

    private static final String PDF = "application/pdf";
    private static final String PNG = "image/png";
    private static final String JPEG = "image/jpeg";

    private final DocumentStorageService documentStorageService;
    private final DocumentRepository documentRepository;
    private final AuditLogService auditLogService;
    private final Clock clock;

    /** Constructor injection (R-CODE-1). */
    public DocumentService(final DocumentStorageService documentStorageService,
                           final DocumentRepository documentRepository,
                           final AuditLogService auditLogService,
                           final Clock clock) {
        this.documentStorageService = documentStorageService;
        this.documentRepository = documentRepository;
        this.auditLogService = auditLogService;
        this.clock = clock;
    }

    /**
     * Validates, stores and records an uploaded document, unlinked from any employee
     * (step 1 runs before the employee row exists; A10 links it via {@code contractDocumentId}).
     *
     * @param file    the uploaded file
     * @param docType the requested document type (string from the multipart form)
     * @param caller  the HR user uploading
     * @return the new document's ID
     * @throws IOException if reading or storing the file fails
     */
    @Transactional
    @PreAuthorize("hasRole('HR')")
    public Long upload(final MultipartFile file, final String docType, final Employee caller)
            throws IOException {
        final DocType type = parseDocType(docType);
        if (file == null || file.isEmpty()) {
            throw invalid("file", "This field is required.");
        }
        final String detected = detectContentType(file);
        final boolean allowed = PDF.equals(detected)
                || (type == DocType.CONTRACT && (PNG.equals(detected) || JPEG.equals(detected)));
        if (!allowed) {
            throw invalid("file", "This file type is not allowed.");
        }

        final String storageKey = documentStorageService.store(file);
        final OffsetDateTime now = OffsetDateTime.now(clock);
        final Document document = new Document();
        document.setDocType(type);
        document.setFileName(safeFileName(file.getOriginalFilename()));
        document.setContentType(detected);
        document.setSizeBytes(file.getSize());
        document.setStorageKey(storageKey);
        document.setUploadedBy(caller);
        document.setUploadedAt(now);
        document.setCreatedAt(now);
        document.setCreatedBy(caller.getId());
        document.setUpdatedAt(now);
        document.setUpdatedBy(caller.getId());
        final Document saved = documentRepository.save(document);

        auditLogService.record(null, "DOCUMENT", saved.getId(), "UPLOAD_DOCUMENT", caller, null,
                "{\"docType\":\"" + type.name() + "\",\"contentType\":\"" + detected + "\"}");
        return saved.getId();
    }

    /**
     * Stores a document the system generates itself (not an upload), linked to an employee —
     * e.g. the POLICY_ACK receipt written when an employee acknowledges a policy (FRD-02 §4.5).
     * The caller records its own audit entry for the business action.
     *
     * @param employee    the employee the document belongs to
     * @param docType     the document type
     * @param fileName    file name to show
     * @param contentType MIME type of {@code content}
     * @param content     the bytes (must not be empty — DB CHECK size_bytes &gt; 0)
     * @param caller      the user whose action produced it
     * @return the saved document
     */
    @Transactional
    public Document storeGenerated(final Employee employee, final DocType docType, final String fileName,
                                   final String contentType, final byte[] content, final Employee caller) {
        final String storageKey;
        try {
            storageKey = documentStorageService.store(content);
        } catch (final IOException ex) {
            throw new IllegalStateException("Could not store generated document", ex);
        }
        final OffsetDateTime now = OffsetDateTime.now(clock);
        final Document document = new Document();
        document.setEmployee(employee);
        document.setDocType(docType);
        document.setFileName(fileName);
        document.setContentType(contentType);
        document.setSizeBytes((long) content.length);
        document.setStorageKey(storageKey);
        document.setUploadedBy(caller);
        document.setUploadedAt(now);
        document.setCreatedAt(now);
        document.setCreatedBy(caller.getId());
        document.setUpdatedAt(now);
        document.setUpdatedBy(caller.getId());
        return documentRepository.save(document);
    }

    private static DocType parseDocType(final String value) {
        try {
            final DocType type = DocType.valueOf(value);
            if (UPLOADABLE.contains(type)) return type;
        } catch (final IllegalArgumentException | NullPointerException ignored) {
            // falls through to the validation error below
        }
        throw invalid("docType", "This field is required.");
    }

    /** Content type from the file's first bytes; {@code null} when not a recognised type. */
    private static String detectContentType(final MultipartFile file) throws IOException {
        final byte[] head = new byte[8];
        final int n;
        try (InputStream in = file.getInputStream()) {
            n = in.readNBytes(head, 0, head.length);
        }
        final byte[] h = Arrays.copyOf(head, n);
        if (startsWith(h, new byte[]{'%', 'P', 'D', 'F'})) return PDF;
        if (startsWith(h, new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A})) return PNG;
        if (startsWith(h, new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF})) return JPEG;
        return null;
    }

    private static boolean startsWith(final byte[] data, final byte[] prefix) {
        if (data.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) return false;
        }
        return true;
    }

    /** Keeps only the last path segment of the client-supplied name, without control characters. */
    private static String safeFileName(final String original) {
        if (original == null || original.isBlank()) return "document";
        String name = original.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("\\p{Cntrl}", "");
        if (name.isBlank()) return "document";
        return name.length() > 255 ? name.substring(name.length() - 255) : name;
    }

    private static ApiException invalid(final String field, final String message) {
        return ApiException.fieldValidation(List.of(Map.of("field", field, "message", message)));
    }
}
