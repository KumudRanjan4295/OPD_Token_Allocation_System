package com.opd.engine;

import com.opd.engine.model.AllocatedToken;
import com.opd.engine.model.Enums.TokenSource;
import com.opd.engine.model.TimeSlot;
import com.opd.engine.model.TokenRequest;

import java.time.LocalTime;
import java.util.List;

/**
 * Simple console-based simulation of one OPD day with 3 doctors.
 * Run with: mvn -q -DskipTests exec:java -Dexec.mainClass=com.opd.engine.SimulationRunner
 */
public class SimulationRunner {

    public static void main(String[] args) {
        TimeSlot drA1 = new TimeSlot("drA-09", "DrA", LocalTime.of(9, 0), LocalTime.of(10, 0), 4);
        TimeSlot drA2 = new TimeSlot("drA-10", "DrA", LocalTime.of(10, 0), LocalTime.of(11, 0), 4);

        TimeSlot drB1 = new TimeSlot("drB-09", "DrB", LocalTime.of(9, 0), LocalTime.of(10, 0), 3);
        TimeSlot drB2 = new TimeSlot("drB-10", "DrB", LocalTime.of(10, 0), LocalTime.of(11, 0), 3);

        TimeSlot drC1 = new TimeSlot("drC-09", "DrC", LocalTime.of(9, 0), LocalTime.of(10, 0), 2);
        TimeSlot drC2 = new TimeSlot("drC-10", "DrC", LocalTime.of(10, 0), LocalTime.of(11, 0), 2);

        TokenAllocationEngine engine = new TokenAllocationEngine(List.of(drA1, drA2, drB1, drB2, drC1, drC2));

        System.out.println("=== Morning online bookings ===");
        for (int i = 1; i <= 8; i++) {
            engine.addRequest(new TokenRequest("P-online-" + i, TokenSource.ONLINE, "drA-09", false));
        }
        printSnapshot(engine);

        System.out.println("\n=== Walk-in patients arrive ===");
        for (int i = 1; i <= 5; i++) {
            engine.addRequest(new TokenRequest("P-walkin-" + i, TokenSource.WALK_IN, null, false));
        }
        printSnapshot(engine);

        System.out.println("\n=== Paid priority patient added (should jump ahead) ===");
        TokenRequest priority = new TokenRequest("P-priority-1", TokenSource.PRIORITY, "drA-09", false);
        engine.addRequest(priority);
        printSnapshot(engine);

        System.out.println("\n=== Emergency case inserted (highest priority) ===");
        TokenRequest emergency = new TokenRequest("P-emergency-1", TokenSource.EMERGENCY, "drA-09", false);
        engine.addRequest(emergency);
        printSnapshot(engine);

        System.out.println("\n=== One patient cancels, one no-show ===");
        engine.cancelRequest(priority.getId());
        engine.markNoShow(emergency.getId());
        printSnapshot(engine);
    }

    private static void printSnapshot(TokenAllocationEngine engine) {
        System.out.println("--- Current allocations by slot ---");
        engine.getSlots().forEach(slot -> {
            System.out.printf("%s (%s %s-%s)%n", slot.getId(), slot.getDoctorId(), slot.getStart(), slot.getEnd());
            List<AllocatedToken> tokens = engine.getAllocationsForSlot(slot.getId());
            for (AllocatedToken token : tokens) {
                System.out.printf("  #%d %s [%s] from %s%n",
                        token.getSequence(),
                        token.getRequest().getPatientId(),
                        token.getRequest().getSource(),
                        token.getRequest().getPreferredSlotId());
            }
            if (tokens.isEmpty()) {
                System.out.println("  <empty>");
            }
        });
    }
}

