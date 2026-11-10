package com.citydisruptors.incidentservice.photo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;

import java.sql.Types;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "incident_photos")
public class IncidentPhoto {

    @Id
    @JdbcTypeCode(Types.VARCHAR)
    @Column(name = "incident_id")
    private UUID incidentId;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(nullable = false)
    private byte[] data;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected IncidentPhoto() {
    }

    public IncidentPhoto(UUID incidentId, PhotoDataUrl photo) {
        this.incidentId = incidentId;
        this.contentType = photo.contentType();
        this.data = photo.data();
        this.createdAt = Instant.now();
    }

    public String getContentType() {
        return contentType;
    }

    public byte[] getData() {
        return data;
    }
}
