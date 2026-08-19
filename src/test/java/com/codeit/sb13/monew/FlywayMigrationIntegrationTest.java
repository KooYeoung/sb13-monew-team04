package com.codeit.sb13.monew;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * GitHub Actions workflow에서 PostgreSQL service와 함께 실행하는 Flyway 검증 테스트입니다.
 * 로컬에서 별도로 실행하려면 PostgreSQL 임시 컨테이너를 띄우고 MONEW_MIGRATION_DB_* 값을 지정해야 합니다.
 */
@Tag("migration")
@SpringBootTest(properties = {
        "spring.datasource.url=${MONEW_MIGRATION_DB_URL:jdbc:postgresql://localhost:5432/monew}",
        "spring.datasource.username=${MONEW_MIGRATION_DB_USERNAME:monew}",
        "spring.datasource.password=${MONEW_MIGRATION_DB_PASSWORD:change-me}",
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.docker.compose.enabled=false"
})
class FlywayMigrationIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void flywayMigrationsAreApplied() {
        Long appliedCount = jdbcTemplate.queryForObject(
                "select count(*) from flyway_schema_history where success = true",
                Long.class
        );
        Long failedCount = jdbcTemplate.queryForObject(
                "select count(*) from flyway_schema_history where success = false",
                Long.class
        );

        assertThat(appliedCount).isNotNull().isPositive();
        assertThat(failedCount).isNotNull().isZero();
    }
}
