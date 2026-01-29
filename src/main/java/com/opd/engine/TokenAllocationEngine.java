package com.opd.engine;

import com.opd.engine.model.AllocatedToken;
import com.opd.engine.model.Enums.TokenSource;
import com.opd.engine.model.Enums.TokenStatus;
import com.opd.engine.model.TimeSlot;
import com.opd.engine.model.TokenRequest;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Core in-memory engine that:
 * - Enforces per-slot hard limits
 * - Dynamically reallocates tokens whenever requests or statuses change
 * - Prioritises between token sources
 * - Handles cancellations, no-shows and emergency additions
 */
public class TokenAllocationEngine {

    private final Map<String, TimeSlot> slotsById = new LinkedHashMap<>();
    private final Map<String, TokenRequest> requestsById = new ConcurrentHashMap<>();
    private final Map<String, AllocatedToken> allocationsByRequestId = new ConcurrentHashMap<>();

    private final Map<String, Integer> slotSequenceCounters = new ConcurrentHashMap<>();

    public TokenAllocationEngine(List<TimeSlot> slots) {
        for (TimeSlot slot : slots) {
            slotsById.put(slot.getId(), slot);
            slotSequenceCounters.put(slot.getId(), 0);
        }
    }

    public synchronized TokenRequest addRequest(TokenRequest request) {
        requestsById.put(request.getId(), request);
        rebalance();
        return request;
    }

    public synchronized void cancelRequest(String requestId) {
        AllocatedToken token = allocationsByRequestId.remove(requestId);
        if (token != null) {
            token.setStatus(TokenStatus.CANCELLED);
        }
        requestsById.remove(requestId);
        rebalance();
    }

    public synchronized void markNoShow(String requestId) {
        AllocatedToken token = allocationsByRequestId.get(requestId);
        if (token != null) {
            token.setStatus(TokenStatus.NO_SHOW);
            allocationsByRequestId.remove(requestId);
            requestsById.remove(requestId);
            rebalance();
        }
    }

    public synchronized List<AllocatedToken> getCurrentAllocations() {
        return new ArrayList<>(allocationsByRequestId.values());
    }

    public synchronized List<AllocatedToken> getAllocationsForSlot(String slotId) {
        return allocationsByRequestId.values().stream()
                .filter(t -> t.getSlotId().equals(slotId))
                .sorted(Comparator.comparingInt(AllocatedToken::getSequence))
                .collect(Collectors.toList());
    }

    public Collection<TimeSlot> getSlots() {
        return slotsById.values();
    }

    private int priorityRank(TokenSource source) {
        return switch (source) {
            case EMERGENCY -> 5;
            case PRIORITY -> 4;
            case FOLLOW_UP -> 3;
            case ONLINE -> 2;
            case WALK_IN -> 1;
        };
    }

    /**
     * Rebuild allocation from scratch based on all active requests.
     * High priority sources are allocated first. Within the same priority,
     * earlier requests win.
     */
    private void rebalance() {
        allocationsByRequestId.clear();
        slotSequenceCounters.replaceAll((slotId, ignored) -> 0);

        List<TokenRequest> sortedRequests = requestsById.values().stream()
                .sorted(Comparator
                        .comparingInt((TokenRequest r) -> -priorityRank(r.getSource()))
                        .thenComparing(TokenRequest::getCreatedAt))
                .toList();

        for (TokenRequest request : sortedRequests) {
            List<TimeSlot> candidateSlots = determineCandidateSlots(request);

            for (TimeSlot slot : candidateSlots) {
                int used = (int) allocationsByRequestId.values().stream()
                        .filter(t -> t.getSlotId().equals(slot.getId()))
                        .count();

                if (used < slot.getCapacity()) {
                    int nextSeq = slotSequenceCounters.compute(slot.getId(), (id, current) -> current == null ? 1 : current + 1);
                    AllocatedToken token = new AllocatedToken(
                            UUID.randomUUID().toString(),
                            request,
                            slot.getId(),
                            nextSeq,
                            TokenStatus.CONFIRMED,
                            Instant.now()
                    );
                    allocationsByRequestId.put(request.getId(), token);
                    break;
                }
            }
        }
    }

    private List<TimeSlot> determineCandidateSlots(TokenRequest request) {
        if (request.getPreferredSlotId() != null) {
            TimeSlot preferred = slotsById.get(request.getPreferredSlotId());
            if (preferred != null) {
                return List.of(preferred);
            }
        }

        // No explicit slot: assign to earliest available slot of any doctor.
        return slotsById.values().stream()
                .sorted(Comparator.comparing(TimeSlot::getStart))
                .collect(Collectors.toList());
    }
}

