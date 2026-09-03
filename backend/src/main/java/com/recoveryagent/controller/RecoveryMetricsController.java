package com.recoveryagent.controller;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@Profile("mysql")
@RequestMapping("/api/recovery")
public class RecoveryMetricsController {

    private final JdbcTemplate jdbcTemplate;

    public RecoveryMetricsController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/metrics")
    public Map<String, Object> metrics() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("summary", jdbcTemplate.queryForMap("""
                SELECT
                    COUNT(*) AS total_attempts,
                    COALESCE(SUM(recovery_success = TRUE), 0) AS successful_recoveries,
                    COALESCE(SUM(recovered_amount), 0) AS recovered_amount,
                    COALESCE(ROUND(AVG(NULLIF(time_to_recovery, 0))), 0) AS average_recovery_seconds
                FROM recovery_outcomes
                """));
        List<Map<String, Object>> daily = jdbcTemplate.queryForList("""
                SELECT recovery_date, total_recovery_attempts, successful_recoveries,
                       failed_recoveries, success_rate_pct, total_recovered_amount,
                       avg_recovery_time_seconds
                FROM v_recovery_metrics
                ORDER BY recovery_date DESC
                LIMIT 30
                """);
        response.put("daily", daily);
        return response;
    }
}
