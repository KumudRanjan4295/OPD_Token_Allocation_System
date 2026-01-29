package com.opd.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opd.engine.model.AllocatedToken;
import com.opd.engine.model.Enums.TokenSource;
import com.opd.engine.model.Enums.TokenStatus;
import com.opd.engine.model.TimeSlot;
import com.opd.engine.model.TokenRequest;
import io.javalin.Javalin;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Full web interface for OPD Token Allocation Engine.
 * Provides HTML pages for all operations.
 */
public class ApiServer {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String COMMON_STYLE = "<style>" +
            "* { margin: 0; padding: 0; box-sizing: border-box; }" +
            "body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; background: #f0f2f5; padding: 20px; }" +
            ".container { max-width: 1200px; margin: 0 auto; background: white; padding: 30px; border-radius: 10px; box-shadow: 0 2px 10px rgba(0,0,0,0.1); }" +
            "h1 { color: #2c3e50; margin-bottom: 20px; border-bottom: 3px solid #3498db; padding-bottom: 10px; }" +
            "h2 { color: #34495e; margin: 20px 0 10px 0; }" +
            ".nav { margin-bottom: 20px; }" +
            ".nav a { display: inline-block; margin-right: 15px; padding: 8px 15px; background: #3498db; color: white; text-decoration: none; border-radius: 5px; }" +
            ".nav a:hover { background: #2980b9; }" +
            "table { width: 100%; border-collapse: collapse; margin: 20px 0; }" +
            "th { background: #34495e; color: white; padding: 12px; text-align: left; }" +
            "td { padding: 10px; border-bottom: 1px solid #ddd; }" +
            "tr:hover { background: #f8f9fa; }" +
            ".badge { padding: 4px 8px; border-radius: 4px; font-size: 0.85em; font-weight: bold; }" +
            ".badge-emergency { background: #e74c3c; color: white; }" +
            ".badge-priority { background: #f39c12; color: white; }" +
            ".badge-followup { background: #9b59b6; color: white; }" +
            ".badge-online { background: #3498db; color: white; }" +
            ".badge-walkin { background: #95a5a6; color: white; }" +
            ".badge-confirmed { background: #27ae60; color: white; }" +
            ".badge-pending { background: #f39c12; color: white; }" +
            ".form-group { margin: 15px 0; }" +
            "label { display: block; margin-bottom: 5px; font-weight: bold; color: #2c3e50; }" +
            "input, select { width: 100%; padding: 10px; border: 1px solid #ddd; border-radius: 5px; font-size: 14px; }" +
            "button { padding: 12px 24px; background: #27ae60; color: white; border: none; border-radius: 5px; cursor: pointer; font-size: 16px; }" +
            "button:hover { background: #229954; }" +
            ".btn-danger { background: #e74c3c; }" +
            ".btn-danger:hover { background: #c0392b; }" +
            ".btn-warning { background: #f39c12; }" +
            ".btn-warning:hover { background: #d68910; }" +
            ".success { background: #d4edda; color: #155724; padding: 15px; border-radius: 5px; margin: 15px 0; }" +
            ".error { background: #f8d7da; color: #721c24; padding: 15px; border-radius: 5px; margin: 15px 0; }" +
            ".info-box { background: #e8f4f8; padding: 15px; border-radius: 5px; margin: 15px 0; border-left: 4px solid #3498db; }" +
            "</style>";

    public static void main(String[] args) {
        TokenAllocationEngine engine = createDefaultEngine();

        int port = resolvePort();
        Javalin app = Javalin.create(config -> {
            config.http.defaultContentType = "text/html";
        }).start(port);

        System.out.println("OPD Token Allocation API running on http://localhost:" + port);

        // Home page
        app.get("/", ctx -> ctx.html(homePage(engine)));

        // Health check
        app.get("/health", ctx -> ctx.html(healthPage()));

        // Slots listing
        app.get("/slots", ctx -> ctx.html(slotsPage(engine)));

        // View tokens for a specific slot
        app.get("/slots/{slotId}/tokens", ctx -> {
            String slotId = ctx.pathParam("slotId");
            ctx.html(slotTokensPage(engine, slotId));
        });

        // Request token form
        app.get("/tokens/request", ctx -> ctx.html(requestTokenForm(engine)));

        // Handle token request (form submission)
        app.post("/tokens/request", ctx -> {
            try {
                String contentType = ctx.contentType();
                String patientId;
                TokenSource source;
                String preferredSlotId;
                boolean followUp;

                if (contentType != null && contentType.contains("application/json")) {
                    // JSON API
                    Map<String, Object> body = MAPPER.readValue(ctx.body(), Map.class);
                    patientId = (String) body.get("patientId");
                    source = TokenSource.valueOf(((String) body.get("source")).toUpperCase());
                    preferredSlotId = (String) body.getOrDefault("preferredSlotId", null);
                    followUp = body.getOrDefault("followUp", Boolean.FALSE) instanceof Boolean b && b;
                } else {
                    // Form submission
                    patientId = ctx.formParam("patientId");
                    source = TokenSource.valueOf(ctx.formParam("source").toUpperCase());
                    preferredSlotId = ctx.formParam("preferredSlotId");
                    if (preferredSlotId != null && preferredSlotId.isEmpty()) preferredSlotId = null;
                    followUp = "true".equals(ctx.formParam("followUp"));
                }

                TokenRequest request = new TokenRequest(patientId, source, preferredSlotId, followUp);
                engine.addRequest(request);

                if (contentType != null && contentType.contains("application/json")) {
                    Map<String, Object> response = new HashMap<>();
                    response.put("requestId", request.getId());
                    response.put("allocations", engine.getCurrentAllocations());
                    ctx.json(response);
                } else {
                    ctx.html(requestTokenSuccess(engine, request));
                }
            } catch (Exception e) {
                ctx.html(requestTokenError(e.getMessage()));
            }
        });

        // Cancel token
        app.post("/tokens/{requestId}/cancel", ctx -> {
            String requestId = ctx.pathParam("requestId");
            try {
                engine.cancelRequest(requestId);
                String contentType = ctx.contentType();
                if (contentType != null && contentType.contains("application/json")) {
                    ctx.json(Map.of("status", "cancelled", "requestId", requestId));
                } else {
                    ctx.html(cancelTokenSuccess(engine, requestId));
                }
            } catch (Exception e) {
                ctx.html(cancelTokenError(e.getMessage()));
            }
        });

        // Mark no-show
        app.post("/tokens/{requestId}/no-show", ctx -> {
            String requestId = ctx.pathParam("requestId");
            try {
                engine.markNoShow(requestId);
                String contentType = ctx.contentType();
                if (contentType != null && contentType.contains("application/json")) {
                    ctx.json(Map.of("status", "no_show_recorded", "requestId", requestId));
                } else {
                    ctx.html(noShowSuccess(engine, requestId));
                }
            } catch (Exception e) {
                ctx.html(noShowError(e.getMessage()));
            }
        });
    }

    private static String homePage(TokenAllocationEngine engine) {
        long totalSlots = engine.getSlots().size();
        long totalAllocations = engine.getCurrentAllocations().size();
        
        return "<!DOCTYPE html><html><head><title>OPD Token Allocation Engine</title>" + COMMON_STYLE + "</head><body>" +
                "<div class=\"container\">" +
                "<h1><span style='font-size: 1.2em; color: #3498db; font-weight: bold;'>&#10010;</span> OPD Token Allocation Engine</h1>" +
                "<div class=\"nav\">" +
                "<a href=\"/\">Home</a>" +
                "<a href=\"/health\">Health</a>" +
                "<a href=\"/slots\">View Slots</a>" +
                "<a href=\"/tokens/request\">Request Token</a>" +
                "</div>" +
                "<div class=\"info-box\">" +
                "<h2>System Status</h2>" +
                "<p><strong>Total Slots:</strong> " + totalSlots + "</p>" +
                "<p><strong>Active Allocations:</strong> " + totalAllocations + "</p>" +
                "</div>" +
                "<h2>Quick Actions</h2>" +
                "<div class=\"nav\">" +
                "<a href=\"/slots\">View All Time Slots</a>" +
                "<a href=\"/tokens/request\">Request New Token</a>" +
                "</div>" +
                "<h2>Available Endpoints</h2>" +
                "<table>" +
                "<tr><th>Method</th><th>Endpoint</th><th>Description</th></tr>" +
                "<tr><td>GET</td><td><a href=\"/health\">/health</a></td><td>System health check</td></tr>" +
                "<tr><td>GET</td><td><a href=\"/slots\">/slots</a></td><td>List all time slots</td></tr>" +
                "<tr><td>GET</td><td>/slots/{slotId}/tokens</td><td>View tokens for a slot</td></tr>" +
                "<tr><td>GET</td><td><a href=\"/tokens/request\">/tokens/request</a></td><td>Request token form</td></tr>" +
                "<tr><td>POST</td><td>/tokens/request</td><td>Create token request</td></tr>" +
                "<tr><td>POST</td><td>/tokens/{requestId}/cancel</td><td>Cancel a token</td></tr>" +
                "<tr><td>POST</td><td>/tokens/{requestId}/no-show</td><td>Mark no-show</td></tr>" +
                "</table>" +
                "</div></body></html>";
    }

    private static String healthPage() {
        String timestamp = java.time.LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        return "<!DOCTYPE html><html><head><title>Health Check - OPD Token Allocation</title>" + COMMON_STYLE + "</head><body>" +
                "<div class=\"container\">" +
                "<h1><span style='font-size: 1.2em; color: #3498db; font-weight: bold;'>&#10010;</span> System Health</h1>" +
                "<div class=\"nav\">" +
                "<a href=\"/\">Home</a>" +
                "<a href=\"/health\">Health</a>" +
                "<a href=\"/slots\">View Slots</a>" +
                "<a href=\"/tokens/request\">Request Token</a>" +
                "</div>" +
                "<div class=\"success\">" +
                "<h2>✓ System Operational</h2>" +
                "<p>All services are running normally.</p>" +
                "<p><strong>Status:</strong> OK</p>" +
                "<p><strong>Timestamp:</strong> " + timestamp + "</p>" +
                "</div>" +
                "</div></body></html>";
    }

    private static String slotsPage(TokenAllocationEngine engine) {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html><head><title>Time Slots - OPD Token Allocation</title>");
        html.append(COMMON_STYLE);
        html.append("</head><body><div class=\"container\">");
        html.append("<h1>Time Slots</h1>");
        html.append("<div class=\"nav\">");
        html.append("<a href=\"/\">Home</a>");
        html.append("<a href=\"/health\">Health</a>");
        html.append("<a href=\"/slots\">View Slots</a>");
        html.append("<a href=\"/tokens/request\">Request Token</a>");
        html.append("</div>");
        html.append("<table>");
        html.append("<tr><th>Slot ID</th><th>Doctor</th><th>Time</th><th>Capacity</th><th>Allocated</th><th>Available</th><th>Actions</th></tr>");

        for (TimeSlot slot : engine.getSlots()) {
            List<AllocatedToken> tokens = engine.getAllocationsForSlot(slot.getId());
            int allocated = tokens.size();
            int available = slot.getCapacity() - allocated;
            String timeRange = slot.getStart() + " - " + slot.getEnd();
            
            html.append("<tr>");
            html.append("<td><strong>").append(slot.getId()).append("</strong></td>");
            html.append("<td>").append(slot.getDoctorId()).append("</td>");
            html.append("<td>").append(timeRange).append("</td>");
            html.append("<td>").append(slot.getCapacity()).append("</td>");
            html.append("<td>").append(allocated).append("</td>");
            html.append("<td>").append(available).append("</td>");
            html.append("<td><a href=\"/slots/").append(slot.getId()).append("/tokens\">View Tokens</a></td>");
            html.append("</tr>");
        }

        html.append("</table></div></body></html>");
        return html.toString();
    }

    private static String slotTokensPage(TokenAllocationEngine engine, String slotId) {
        List<AllocatedToken> tokens = engine.getAllocationsForSlot(slotId);
        TimeSlot slot = engine.getSlots().stream()
                .filter(s -> s.getId().equals(slotId))
                .findFirst()
                .orElse(null);

        if (slot == null) {
            return errorPage("Slot not found: " + slotId);
        }

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html><head><title>Tokens for ").append(slotId).append(" - OPD Token Allocation</title>");
        html.append(COMMON_STYLE);
        html.append("</head><body><div class=\"container\">");
        html.append("<h1>Tokens for ").append(slotId).append("</h1>");
        html.append("<div class=\"nav\">");
        html.append("<a href=\"/\">Home</a>");
        html.append("<a href=\"/health\">Health</a>");
        html.append("<a href=\"/slots\">View Slots</a>");
        html.append("<a href=\"/tokens/request\">Request Token</a>");
        html.append("</div>");
        html.append("<div class=\"info-box\">");
        html.append("<p><strong>Doctor:</strong> ").append(slot.getDoctorId()).append("</p>");
        html.append("<p><strong>Time:</strong> ").append(slot.getStart()).append(" - ").append(slot.getEnd()).append("</p>");
        html.append("<p><strong>Capacity:</strong> ").append(slot.getCapacity()).append("</p>");
        html.append("<p><strong>Allocated:</strong> ").append(tokens.size()).append("</p>");
        html.append("</div>");
        html.append("<table>");
        html.append("<tr><th>Sequence</th><th>Token ID</th><th>Patient ID</th><th>Source</th><th>Status</th><th>Actions</th></tr>");

        if (tokens.isEmpty()) {
            html.append("<tr><td colspan='6' style='text-align: center; padding: 20px;'>No tokens allocated for this slot</td></tr>");
        } else {
            for (AllocatedToken token : tokens) {
                String sourceBadge = getSourceBadge(token.getRequest().getSource());
                String statusBadge = getStatusBadge(token.getStatus());
                
                html.append("<tr>");
                html.append("<td><strong>#").append(token.getSequence()).append("</strong></td>");
                html.append("<td>").append(token.getTokenId()).append("</td>");
                html.append("<td>").append(token.getRequest().getPatientId()).append("</td>");
                html.append("<td>").append(sourceBadge).append("</td>");
                html.append("<td>").append(statusBadge).append("</td>");
                html.append("<td>");
                html.append("<form method=\"post\" action=\"/tokens/").append(token.getRequest().getId()).append("/cancel\" style=\"display: inline;\">");
                html.append("<button type=\"submit\" class=\"btn-danger\" style=\"padding: 5px 10px; font-size: 12px;\">Cancel</button>");
                html.append("</form> ");
                html.append("<form method=\"post\" action=\"/tokens/").append(token.getRequest().getId()).append("/no-show\" style=\"display: inline;\">");
                html.append("<button type=\"submit\" class=\"btn-warning\" style=\"padding: 5px 10px; font-size: 12px;\">No Show</button>");
                html.append("</form>");
                html.append("</td>");
                html.append("</tr>");
            }
        }

        html.append("</table></div></body></html>");
        return html.toString();
    }

    private static String requestTokenForm(TokenAllocationEngine engine) {
        StringBuilder slotOptions = new StringBuilder("<option value=''>Any available slot</option>");
        for (TimeSlot slot : engine.getSlots()) {
            slotOptions.append("<option value='").append(slot.getId()).append("'>")
                    .append(slot.getId()).append(" (").append(slot.getDoctorId())
                    .append(" ").append(slot.getStart()).append("-").append(slot.getEnd())
                    .append(")</option>");
        }

        return "<!DOCTYPE html><html><head><title>Request Token - OPD Token Allocation</title>" + COMMON_STYLE + "</head><body>" +
                "<div class=\"container\">" +
                "<h1>Request New Token</h1>" +
                "<div class=\"nav\">" +
                "<a href=\"/\">Home</a>" +
                "<a href=\"/health\">Health</a>" +
                "<a href=\"/slots\">View Slots</a>" +
                "<a href=\"/tokens/request\">Request Token</a>" +
                "</div>" +
                "<form method=\"post\" action=\"/tokens/request\">" +
                "<div class=\"form-group\">" +
                "<label for=\"patientId\">Patient ID *</label>" +
                "<input type=\"text\" id=\"patientId\" name=\"patientId\" required placeholder=\"e.g., P123\">" +
                "</div>" +
                "<div class=\"form-group\">" +
                "<label for=\"source\">Token Source *</label>" +
                "<select id=\"source\" name=\"source\" required>" +
                "<option value=\"ONLINE\">Online Booking</option>" +
                "<option value=\"WALK_IN\">Walk-In</option>" +
                "<option value=\"PRIORITY\">Paid Priority</option>" +
                "<option value=\"FOLLOW_UP\">Follow-Up</option>" +
                "<option value=\"EMERGENCY\">Emergency</option>" +
                "</select>" +
                "</div>" +
                "<div class=\"form-group\">" +
                "<label for=\"preferredSlotId\">Preferred Slot (Optional)</label>" +
                "<select id=\"preferredSlotId\" name=\"preferredSlotId\">" +
                slotOptions.toString() +
                "</select>" +
                "</div>" +
                "<div class=\"form-group\">" +
                "<label><input type=\"checkbox\" name=\"followUp\" value=\"true\"> Follow-up patient</label>" +
                "</div>" +
                "<button type=\"submit\">Request Token</button>" +
                "</form>" +
                "</div></body></html>";
    }

    private static String requestTokenSuccess(TokenAllocationEngine engine, TokenRequest request) {
        return "<!DOCTYPE html><html><head><title>Token Requested - OPD Token Allocation</title>" + COMMON_STYLE + "</head><body>" +
                "<div class=\"container\">" +
                "<h1><span style='color: #27ae60; font-size: 1.2em; font-weight: bold;'>&#10003;</span> Token Request Successful</h1>" +
                "<div class=\"nav\">" +
                "<a href=\"/\">Home</a>" +
                "<a href=\"/health\">Health</a>" +
                "<a href=\"/slots\">View Slots</a>" +
                "<a href=\"/tokens/request\">Request Token</a>" +
                "</div>" +
                "<div class=\"success\">" +
                "<p><strong>Request ID:</strong> " + request.getId() + "</p>" +
                "<p><strong>Patient ID:</strong> " + request.getPatientId() + "</p>" +
                "<p><strong>Source:</strong> " + request.getSource() + "</p>" +
                "<p>Your token request has been processed. The system will allocate you to the best available slot.</p>" +
                "</div>" +
                "<p><a href=\"/slots\">View All Slots</a> | <a href=\"/tokens/request\">Request Another Token</a></p>" +
                "</div></body></html>";
    }

    private static String requestTokenError(String message) {
        return errorPage("Error requesting token: " + message);
    }

    private static String cancelTokenSuccess(TokenAllocationEngine engine, String requestId) {
        return "<!DOCTYPE html><html><head><title>Token Cancelled - OPD Token Allocation</title>" + COMMON_STYLE + "</head><body>" +
                "<div class=\"container\">" +
                "<h1><span style='color: #27ae60; font-size: 1.2em; font-weight: bold;'>&#10003;</span> Token Cancelled</h1>" +
                "<div class=\"nav\">" +
                "<a href=\"/\">Home</a>" +
                "<a href=\"/health\">Health</a>" +
                "<a href=\"/slots\">View Slots</a>" +
                "<a href=\"/tokens/request\">Request Token</a>" +
                "</div>" +
                "<div class=\"success\">" +
                "<p>Token request <strong>" + requestId + "</strong> has been cancelled.</p>" +
                "<p>The slot capacity has been freed and reallocated.</p>" +
                "</div>" +
                "<p><a href=\"/slots\">View All Slots</a></p>" +
                "</div></body></html>";
    }

    private static String cancelTokenError(String message) {
        return errorPage("Error cancelling token: " + message);
    }

    private static String noShowSuccess(TokenAllocationEngine engine, String requestId) {
        return "<!DOCTYPE html><html><head><title>No-Show Recorded - OPD Token Allocation</title>" + COMMON_STYLE + "</head><body>" +
                "<div class=\"container\">" +
                "<h1><span style='color: #27ae60; font-size: 1.2em; font-weight: bold;'>&#10003;</span> No-Show Recorded</h1>" +
                "<div class=\"nav\">" +
                "<a href=\"/\">Home</a>" +
                "<a href=\"/health\">Health</a>" +
                "<a href=\"/slots\">View Slots</a>" +
                "<a href=\"/tokens/request\">Request Token</a>" +
                "</div>" +
                "<div class=\"success\">" +
                "<p>Token request <strong>" + requestId + "</strong> has been marked as no-show.</p>" +
                "<p>The slot capacity has been freed and reallocated.</p>" +
                "</div>" +
                "<p><a href=\"/slots\">View All Slots</a></p>" +
                "</div></body></html>";
    }

    private static String noShowError(String message) {
        return errorPage("Error recording no-show: " + message);
    }

    private static String errorPage(String message) {
        return "<!DOCTYPE html><html><head><title>Error - OPD Token Allocation</title>" + COMMON_STYLE + "</head><body>" +
                "<div class=\"container\">" +
                "<h1><span style='color: #e74c3c; font-size: 1.2em; font-weight: bold;'>&#10007;</span> Error</h1>" +
                "<div class=\"nav\">" +
                "<a href=\"/\">Home</a>" +
                "<a href=\"/health\">Health</a>" +
                "<a href=\"/slots\">View Slots</a>" +
                "<a href=\"/tokens/request\">Request Token</a>" +
                "</div>" +
                "<div class=\"error\">" +
                "<p>" + message + "</p>" +
                "</div>" +
                "</div></body></html>";
    }

    private static String getSourceBadge(TokenSource source) {
        return switch (source) {
            case EMERGENCY -> "<span class='badge badge-emergency'>EMERGENCY</span>";
            case PRIORITY -> "<span class='badge badge-priority'>PRIORITY</span>";
            case FOLLOW_UP -> "<span class='badge badge-followup'>FOLLOW-UP</span>";
            case ONLINE -> "<span class='badge badge-online'>ONLINE</span>";
            case WALK_IN -> "<span class='badge badge-walkin'>WALK-IN</span>";
        };
    }

    private static String getStatusBadge(TokenStatus status) {
        return switch (status) {
            case CONFIRMED -> "<span class='badge badge-confirmed'>CONFIRMED</span>";
            case PENDING -> "<span class='badge badge-pending'>PENDING</span>";
            case CANCELLED -> "<span class='badge badge-danger'>CANCELLED</span>";
            case NO_SHOW -> "<span class='badge badge-warning'>NO-SHOW</span>";
        };
    }

    private static int resolvePort() {
        String fromEnv = System.getenv("PORT");
        if (fromEnv == null || fromEnv.isBlank()) {
            return 8080;
        }
        try {
            int p = Integer.parseInt(fromEnv.trim());
            if (p < 1 || p > 65535) {
                return 8080;
            }
            return p;
        } catch (NumberFormatException ignored) {
            return 8080;
        }
    }

    /**
     * Builds a simple default day with three doctors and fixed slots.
     */
    private static TokenAllocationEngine createDefaultEngine() {
        TimeSlot drA1 = new TimeSlot("drA-09", "DrA", LocalTime.of(9, 0), LocalTime.of(10, 0), 10);
        TimeSlot drA2 = new TimeSlot("drA-10", "DrA", LocalTime.of(10, 0), LocalTime.of(11, 0), 10);

        TimeSlot drB1 = new TimeSlot("drB-09", "DrB", LocalTime.of(9, 0), LocalTime.of(10, 0), 8);
        TimeSlot drB2 = new TimeSlot("drB-10", "DrB", LocalTime.of(10, 0), LocalTime.of(11, 0), 8);

        TimeSlot drC1 = new TimeSlot("drC-09", "DrC", LocalTime.of(9, 0), LocalTime.of(10, 0), 6);
        TimeSlot drC2 = new TimeSlot("drC-10", "DrC", LocalTime.of(10, 0), LocalTime.of(11, 0), 6);

        return new TokenAllocationEngine(List.of(drA1, drA2, drB1, drB2, drC1, drC2));
    }
}
