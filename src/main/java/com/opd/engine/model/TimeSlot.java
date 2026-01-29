package com.opd.engine.model;

import java.time.LocalTime;

public class TimeSlot {

    private final String id;       // e.g. "drA-09:00"
    private final String doctorId;
    private final LocalTime start;
    private final LocalTime end;
    private final int capacity;

    public TimeSlot(String id, String doctorId, LocalTime start, LocalTime end, int capacity) {
        this.id = id;
        this.doctorId = doctorId;
        this.start = start;
        this.end = end;
        this.capacity = capacity;
    }

    public String getId() {
        return id;
    }

    public String getDoctorId() {
        return doctorId;
    }

    public LocalTime getStart() {
        return start;
    }

    public LocalTime getEnd() {
        return end;
    }

    public int getCapacity() {
        return capacity;
    }
}

