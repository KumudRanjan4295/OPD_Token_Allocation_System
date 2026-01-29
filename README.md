## OPD Token Allocation Engine (Java)

This project implements a **token allocation system for a hospital OPD** that supports **elastic capacity management**.

It is written in **Java 17** with:

- **Core engine** in `com.opd.engine.TokenAllocationEngine`
- **HTTP API service** using Javalin in `com.opd.engine.ApiServer`
- **One-day simulation** in `com.opd.engine.SimulationRunner`

### Concepts

- **Time slots**: fixed OPD windows per doctor (e.g. 9–10, 10–11) with a **hard capacity**.
- **Token sources / priorities**:
  - EMERGENCY (highest)
  - PRIORITY (paid)  
  - FOLLOW_UP  
  - ONLINE  
  - WALK_IN (lowest)
- **Engine behaviour**:
  - Enforces **per-slot hard limits**
  - Maintains a set of active requests and **recomputes allocations** whenever something changes
  - Higher-priority sources are always allocated before lower-priority ones
  - **Cancellations** and **no-shows** free capacity, which is then re-used by waiting lower-priority requests
  - **Emergency / priority** requests can be inserted at any time and will be given the earliest possible slot

### API Design

- **POST `/tokens/request`**
  - Body:
    - `patientId` (string, required)
    - `source` (one of: `ONLINE`, `WALK_IN`, `PRIORITY`, `FOLLOW_UP`, `EMERGENCY`)
    - `preferredSlotId` (optional, e.g. `drA-09`)
    - `followUp` (boolean, optional)
  - Response: JSON containing `requestId` and the current full allocation view.

- **POST `/tokens/{requestId}/cancel`**
  - Marks a request as cancelled and triggers reallocation.

- **POST `/tokens/{requestId}/no-show`**
  - Marks a request as no-show and triggers reallocation.

- **GET `/slots`**
  - Lists all configured slots (for a typical day, 3 doctors × 2 slots).

- **GET `/slots/{slotId}/tokens`**
  - Returns ordered tokens for a specific slot.

### How dynamic reallocation works

1. All **active** requests (not cancelled / no-show) are collected.
2. They are **sorted by priority** (EMERGENCY → PRIORITY → FOLLOW_UP → ONLINE → WALK_IN) and then by **arrival time**.
3. The engine walks this ordered list and tries to place each request into:
   - Its **preferred slot**, if specified and there is capacity; otherwise
   - The **earliest available slot** (across all doctors).
4. If a slot fills up, lower-priority patients are naturally pushed to later slots or left unallocated.
5. When:
   - a **cancellation** happens, or
   - a **no-show** is recorded, or
   - a new **emergency/priority** patient is added,
   the engine recomputes from scratch to produce the best schedule given the new situation.

This gives **elastic capacity management** while respecting strict per-slot limits.

### Running the API service

1. Make sure you have **Java 17+** and **Maven** installed.
2. From the project root (`OPD Token Allocation Engine`), run:

```bash
mvn clean package
java -jar target/opd-token-allocation-engine-1.0.0-SNAPSHOT.jar
```

3. The API listens on `http://localhost:8080`.

Example request (online booking with preferred slot):

PowerShell:

```powershell
Invoke-RestMethod -Method Post -Uri "http://localhost:8080/tokens/request" -ContentType "application/json" -Body '{"patientId":"P123","source":"ONLINE","preferredSlotId":"drA-09"}'
```

Git Bash / CMD (curl):

```bash
curl -X POST "http://localhost:8080/tokens/request" -H "Content-Type: application/json" -d "{\"patientId\":\"P123\",\"source\":\"ONLINE\",\"preferredSlotId\":\"drA-09\"}"
```

### Running the one-day simulation

The `SimulationRunner` class simulates **one OPD morning** with at least **3 doctors**, showing:

- Initial online bookings
- Walk-in arrivals
- Insertion of a paid priority patient
- Insertion of an emergency case
- One cancellation and one no-show

To run:

```bash
mvn -q -DskipTests exec:java -Dexec.mainClass=com.opd.engine.SimulationRunner
```

The simulation prints slot-wise schedules after each event so you can see how tokens are **reallocated dynamically** while respecting capacities and priorities.

