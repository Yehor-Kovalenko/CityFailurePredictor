package com.citydisruptors.data_ingestion_service.entity;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "dataset_import_state")
public class DatasetImportState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "file_name", nullable = false, unique = true)
    private String fileName;

    @Column(name = "next_record_number", nullable = false)
    private long nextRecordNumber = 1;

    @Column(nullable = false)
    private boolean completed = false;

    @Column(name = "imported_rows", nullable = false)
    private long importedRows = 0;

    @Column(name = "skipped_rows", nullable = false)
    private long skippedRows = 0;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected DatasetImportState() {
    }

    public DatasetImportState(String fileName) {
        this.fileName = fileName;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public long getSkippedRows() {
        return skippedRows;
    }

    public void setSkippedRows(long skippedRows) {
        this.skippedRows = skippedRows;
    }

    public long getImportedRows() {
        return importedRows;
    }

    public void setImportedRows(long importedRows) {
        this.importedRows = importedRows;
    }

    public boolean isCompleted() {
        return completed;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }

    public long getNextRecordNumber() {
        return nextRecordNumber;
    }

    public void setNextRecordNumber(long nextRecordNumber) {
        this.nextRecordNumber = nextRecordNumber;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }
}