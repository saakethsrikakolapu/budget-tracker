package com.saaketh.budget.health;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lets the frontend, CI, and hosting platforms check that the backend is running.
 */
@RestController
public class HealthController {

    /** Response body for GET /api/health; Spring turns it into {"status":"UP"}. */
    public record HealthResponse(String status) {
    }

    @GetMapping("/api/health")
    public HealthResponse health() {
        return new HealthResponse("UP");
    }
}
