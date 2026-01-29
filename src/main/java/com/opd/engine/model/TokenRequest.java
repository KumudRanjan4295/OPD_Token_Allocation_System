package com.opd.engine.model;

import com.opd.engine.model.Enums.TokenSource;

import java.time.Instant;
import java.util.UUID;

public class TokenRequest {

    private final String id;
    private final String patientId;
    private final TokenSource source;
    private final String preferredSlotId; // nullable
    private final boolean followUp;
    private final Instant createdAt;

    public TokenRequest(String patientId, TokenSource source, String preferredSlotId, boolean followUp) {
        this(UUID.randomUUID().toString(), patientId, source, preferredSlotId, followUp, Instant.now());
    }

    public TokenRequest(String id,
                         String patientId,
                         TokenSource source,
                         String preferredSlotId,
                         boolean followUp,
                         Instant createdAt) {
        this.id = id;
        this.patientId = patientId;
        this.source = source;
        this.preferredSlotId = preferredSlotId;
        this.followUp = followUp;
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public String getPatientId() {
        return patientId;
    }

    public TokenSource getSource() {
        return source;
    }

    public String getPreferredSlotId() {
        return preferredSlotId;
    }

    public boolean isFollowUp() {
        return followUp;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

