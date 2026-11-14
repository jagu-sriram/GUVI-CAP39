
package com.sriram.cap39.controller;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/health")
public class SnowflakeHealthController {

    private final JdbcTemplate jdbcTemplate;

    public SnowflakeHealthController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/snowflake")
    public Map<String, Object> checkSnowflakeConnection() {

        return jdbcTemplate.queryForMap("""
            SELECT
                CURRENT_ACCOUNT() AS ACCOUNT,
                CURRENT_DATABASE() AS DATABASE_NAME,
                CURRENT_SCHEMA() AS SCHEMA_NAME,
                CURRENT_WAREHOUSE() AS WAREHOUSE_NAME,
                CURRENT_VERSION() AS SNOWFLAKE_VERSION
        """);
    }
}
