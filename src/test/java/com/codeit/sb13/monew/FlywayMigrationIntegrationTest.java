package com.codeit.sb13.monew;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
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

    @Test
    void activityVisibilityStatusColumnsAreCreated() {
        List<String> tableNames = List.of("subscriptions", "article_views", "comments", "comment_likes");

        for (String tableName : tableNames) {
            var column = jdbcTemplate.queryForMap("""
                            select is_nullable, column_default
                            from information_schema.columns
                            where table_schema = current_schema()
                              and table_name = ?
                              and column_name = 'visibility_status'
                            """,
                    tableName
            );

            assertThat(column.get("is_nullable")).isEqualTo("NO");
            assertThat((String) column.get("column_default")).contains("ACTIVE");
        }
    }
}
