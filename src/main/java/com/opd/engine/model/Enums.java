package com.opd.engine.model;

public final class Enums {

    private Enums() {
    }

    public enum TokenSource {
        ONLINE,
        WALK_IN,
        PRIORITY,   // paid priority patients
        FOLLOW_UP,
        EMERGENCY
    }

    public enum TokenStatus {
        PENDING,
        CONFIRMED,
        CANCELLED,
        NO_SHOW
    }
}

