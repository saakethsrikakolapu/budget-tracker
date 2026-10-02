package com.saaketh.budget.health;

import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lets the frontend, CI, and hosting platforms check that the backend and its database are running.
 */
@RestController
public class HealthController {

    /** Response body for GET /api/health, e.g. {"status":"UP","database":"UP"}. */
    public record HealthResponse(String status, String database) {
    }

    private final JdbcTemplate jdbcTemplate;

    public HealthController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/api/health")
    public ResponseEntity<HealthResponse> health() {
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return ResponseEntity.ok(new HealthResponse("UP", "UP"));
        } catch (DataAccessException e) {
            // 503 tells hosting platforms the app is running but can't serve requests yet.
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new HealthResponse("DOWN", "DOWN"));
        }
    }
}
