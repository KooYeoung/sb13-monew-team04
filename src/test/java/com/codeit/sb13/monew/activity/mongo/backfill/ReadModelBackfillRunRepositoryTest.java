package com.codeit.sb13.monew.activity.mongo.backfill;

import static org.assertj.core.api.Assertions.assertThat;

import com.codeit.sb13.monew.global.config.QueryDslConfig;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@Import(QueryDslConfig.class)
class ReadModelBackfillRunRepositoryTest {

    @Autowired
    private ReadModelBackfillRunRepository repository;

    @Test
    @DisplayName("checkpoint와 lease 기준 시각을 DB에서 조회한다")
    void readsDatabaseTimeAndLocksCheckpoint() {
        LocalDateTime databaseNow = repository.currentTime();
        UUID runId = UUID.randomUUID();
        repository.saveAndFlush(ReadModelBackfillRun.start(runId, databaseNow));

        ReadModelBackfillRun run = repository.findByIdForUpdate(runId).orElseThrow();

        assertThat(databaseNow).isNotNull();
        assertThat(run.getRunId()).isEqualTo(runId);
        assertThat(run.getStage()).isEqualTo(ReadModelBackfillStage.SUBSCRIPTION);
        assertThat(run.getStatus()).isEqualTo(ReadModelBackfillStatus.PENDING);
    }
}
