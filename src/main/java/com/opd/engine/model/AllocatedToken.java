package com.opd.engine.model;

import com.opd.engine.model.Enums.TokenStatus;

import java.time.Instant;

public class AllocatedToken {

    private final String tokenId;
    private final TokenRequest request;
    private final String slotId;
    private final int sequence;
    private final Instant allocatedAt;
    private TokenStatus status;

    public AllocatedToken(String tokenId, TokenRequest request, String slotId, int sequence, TokenStatus status, Instant allocatedAt) {
        this.tokenId = tokenId;
        this.request = request;
        this.slotId = slotId;
        this.sequence = sequence;
        this.status = status;
        this.allocatedAt = allocatedAt;
    }

    public String getTokenId() {
        return tokenId;
    }

    public TokenRequest getRequest() {
        return request;
    }

    public String getSlotId() {
        return slotId;
    }

    public int getSequence() {
        return sequence;
    }

    public TokenStatus getStatus() {
        return status;
    }

    public void setStatus(TokenStatus status) {
        this.status = status;
    }

    public Instant getAllocatedAt() {
        return allocatedAt;
    }
}

