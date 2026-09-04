package com.codeit.sb13.monew.activity.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.codeit.sb13.monew.activity.mongo.config.MongoReadModelProperties;
import com.codeit.sb13.monew.activity.mongo.query.MongoUserActivityReadSource;
import com.codeit.sb13.monew.activity.service.UserActivityReadSourceType;
import com.codeit.sb13.monew.activity.service.UserActivitySections;
import com.codeit.sb13.monew.activity.service.config.UserActivityReadProperties;
import com.codeit.sb13.monew.global.exception.readmodel.ReadModelQueryProgressException;
import com.codeit.sb13.monew.global.exception.readmodel.ReadModelSourceConfigurationException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class UserActivityReadSourceRouterTest {

    private final RdbUserActivityReadSource rdbSource = mock(RdbUserActivityReadSource.class);
    private final MongoUserActivityReadSource mongoSource =
            mock(MongoUserActivityReadSource.class);

    @Test
    void rdbModeUsesOnlyRdbSource() {
        UUID userId = UUID.randomUUID();
        UserActivitySections expected = sections();
        when(rdbSource.read(userId)).thenReturn(expected);
        UserActivityReadSourceRouter router = router(UserActivityReadSourceType.RDB, false);

        UserActivitySections actual = router.read(userId);

        assertThat(actual).isSameAs(expected);
        verify(rdbSource).read(userId);
        verifyNoInteractions(mongoSource);
    }

    @Test
    void mongoDbModeUsesMongoSourceWithoutRdbRead() {
        UUID userId = UUID.randomUUID();
        UserActivitySections expected = sections();
        when(mongoSource.read(userId)).thenReturn(expected);
        UserActivityReadSourceRouter router = router(UserActivityReadSourceType.MONGODB, true);

        UserActivitySections actual = router.read(userId);

        assertThat(actual).isSameAs(expected);
        verify(mongoSource).read(userId);
        verifyNoInteractions(rdbSource);
    }

    @Test
    void emptyMongoDbResultIsReturnedWithoutFallback() {
        UUID userId = UUID.randomUUID();
        UserActivitySections empty = sections();
        when(mongoSource.read(userId)).thenReturn(empty);
        UserActivityReadSourceRouter router = router(UserActivityReadSourceType.MONGODB, true);

        assertThat(router.read(userId)).isSameAs(empty);

        verifyNoInteractions(rdbSource);
    }

    @Test
    void mongoDbRuntimeFailureIsLoggedAndFallsBackToRdb(CapturedOutput output) {
        UUID userId = UUID.randomUUID();
        IllegalStateException mongoFailure = new IllegalStateException("mongo unavailable");
        UserActivitySections fallback = sections();
        when(mongoSource.read(userId)).thenThrow(mongoFailure);
        when(rdbSource.read(userId)).thenReturn(fallback);
        UserActivityReadSourceRouter router = router(UserActivityReadSourceType.MONGODB, true);

        UserActivitySections actual = router.read(userId);

        assertThat(actual).isSameAs(fallback);
        assertThat(output)
                .contains("MongoDB 활동내역 조회에 실패해 RDB로 fallback합니다.")
                .contains(userId.toString())
                .contains("IllegalStateException")
                .contains("mongo unavailable");
        verify(mongoSource).read(userId);
        verify(rdbSource).read(userId);
    }

    @Test
    void readModelFailureAlsoFallsBackToRdb() {
        UUID userId = UUID.randomUUID();
        ReadModelQueryProgressException mongoFailure = new ReadModelQueryProgressException(
                userId, null, null, "NEXT_CURSOR_MISSING"
        );
        UserActivitySections fallback = sections();
        when(mongoSource.read(userId)).thenThrow(mongoFailure);
        when(rdbSource.read(userId)).thenReturn(fallback);
        UserActivityReadSourceRouter router = router(UserActivityReadSourceType.MONGODB, true);

        assertThat(router.read(userId)).isSameAs(fallback);

        verify(rdbSource).read(userId);
    }

    @Test
    void rdbFailurePreservesMongoDbFailureAsSuppressed() {
        UUID userId = UUID.randomUUID();
        IllegalStateException mongoFailure = new IllegalStateException("mongo unavailable");
        IllegalArgumentException rdbFailure = new IllegalArgumentException("rdb unavailable");
        when(mongoSource.read(userId)).thenThrow(mongoFailure);
        when(rdbSource.read(userId)).thenThrow(rdbFailure);
        UserActivityReadSourceRouter router = router(UserActivityReadSourceType.MONGODB, true);

        assertThatThrownBy(() -> router.read(userId))
                .isSameAs(rdbFailure)
                .satisfies(failure -> assertThat(failure.getSuppressed())
                        .containsExactly(mongoFailure));
    }

    @Test
    void mongoDbSourceRequiresMongoDbFoundationToBeEnabled() {
        assertThatThrownBy(() -> router(UserActivityReadSourceType.MONGODB, false))
                .isInstanceOf(ReadModelSourceConfigurationException.class)
                .satisfies(failure -> {
                    ReadModelSourceConfigurationException configurationFailure =
                            (ReadModelSourceConfigurationException) failure;
                    assertThat(configurationFailure.getDetails())
                            .containsEntry("readSource", UserActivityReadSourceType.MONGODB)
                            .containsEntry("mongodbEnabled", false);
                });
    }

    private UserActivityReadSourceRouter router(
            UserActivityReadSourceType readSource,
            boolean mongoEnabled
    ) {
        return new UserActivityReadSourceRouter(
                rdbSource,
                mongoSource,
                new UserActivityReadProperties(readSource),
                new MongoReadModelProperties(mongoEnabled, false)
        );
    }

    private UserActivitySections sections() {
        return new UserActivitySections(List.of(), List.of(), List.of(), List.of());
    }
}
