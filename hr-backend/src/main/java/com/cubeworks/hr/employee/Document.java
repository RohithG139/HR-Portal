package com.cubeworks.hr.employee;

import com.cubeworks.hr.common.BaseEntity;
import jakarta.persistence.*;
import java.time.OffsetDateTime;

/**
 * A file stored in the document repository.
 *
 * <p>Maps to {@code hr.document}. Documents are uploaded to object storage; only the
 * metadata (filename, MIME type, size, storage key) is stored here. The storage key is
 * unique and is used to retrieve the file from storage.</p>
 *
 * <p>{@link #employee} is nullable: company-wide {@link DocType#POLICY} documents have no
 * employee owner. Uploads made during onboarding before the employee row exists are also
 * initially unlinked and associated later.</p>
 *
 * <p>{@link #uploadedBy} tracks who performed the upload; this is always non-null.
 * {@link #uploadedAt} records when the file was received, separate from the convention
 * {@code created_at} audit timestamp (both fields coexist in the schema).</p>
 */
@Entity
@Table(name = "document")
public class Document extends BaseEntity {

    /**
     * The employee this document belongs to, or {@code null} for company-wide documents
     * (e.g. {@link DocType#POLICY}) or documents uploaded before the employee record exists.
     * Lazy: avoid N+1 when querying a list of documents.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id")
    private Employee employee;

    /** Classification of this document (e.g. CONTRACT, POLICY, POLICY_ACK). */
    @Enumerated(EnumType.STRING)
    @Column(name = "doc_type", length = 30, nullable = false)
    private DocType docType;

    /** Original file name as provided by the uploader. */
    @Column(name = "file_name", length = 255, nullable = false)
    private String fileName;

    /** MIME type of the file (e.g. {@code application/pdf}). */
    @Column(name = "content_type", length = 100, nullable = false)
    private String contentType;

    /**
     * File size in bytes. DB CHECK: must be greater than 0.
     * Using {@code Long} to match the PostgreSQL {@code bigint} column.
     */
    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes;

    /**
     * The object-storage key (path/UUID) used to retrieve the file.
     * Must be unique; attempting to upload a duplicate key is a programming error,
     * not a user-facing validation failure.
     */
    @Column(name = "storage_key", length = 500, nullable = false, unique = true)
    private String storageKey;

    /**
     * The employee who performed the upload.
     * Always non-null; even system-generated documents have a "system actor" employee.
     * Lazy: the uploader object is rarely needed when working with a document.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by", nullable = false)
    private Employee uploadedBy;

    /**
     * When the file was uploaded to object storage.
     * This is a business-event timestamp, distinct from the convention {@code created_at}
     * (which records when this metadata row was inserted into the DB).
     */
    @Column(name = "uploaded_at", nullable = false)
    private OffsetDateTime uploadedAt;

    /** Required no-arg constructor for JPA. */
    protected Document() {}

    // ── Accessors ────────────────────────────────────────────────────────────

    public Employee getEmployee() { return employee; }
    public void setEmployee(final Employee employee) { this.employee = employee; }

    public DocType getDocType() { return docType; }
    public void setDocType(final DocType docType) { this.docType = docType; }

    public String getFileName() { return fileName; }
    public void setFileName(final String fileName) { this.fileName = fileName; }

    public String getContentType() { return contentType; }
    public void setContentType(final String contentType) { this.contentType = contentType; }

    public Long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(final Long sizeBytes) { this.sizeBytes = sizeBytes; }

    public String getStorageKey() { return storageKey; }
    public void setStorageKey(final String storageKey) { this.storageKey = storageKey; }

    public Employee getUploadedBy() { return uploadedBy; }
    public void setUploadedBy(final Employee uploadedBy) { this.uploadedBy = uploadedBy; }

    public OffsetDateTime getUploadedAt() { return uploadedAt; }
    public void setUploadedAt(final OffsetDateTime uploadedAt) { this.uploadedAt = uploadedAt; }
}
