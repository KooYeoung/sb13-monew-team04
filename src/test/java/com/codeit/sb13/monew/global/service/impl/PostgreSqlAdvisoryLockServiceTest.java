package com.codeit.sb13.monew.global.service.impl;

import com.codeit.sb13.monew.global.exception.ApiErrorCode;
import com.codeit.sb13.monew.global.exception.AdvisoryLockException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("PostgreSQL advisory lock 서비스 단위 테스트")
@ExtendWith(MockitoExtension.class)
class PostgreSqlAdvisoryLockServiceTest {

    private static final String LOCK_KEY = "article-backup:2026-08-23";
    private static final String TRY_LOCK_SQL = "SELECT pg_try_advisory_lock(?)";
    private static final String UNLOCK_SQL = "SELECT pg_advisory_unlock(?)";

    @Mock
    private DataSource dataSource;

    @Mock
    private Connection connection;

    @Mock
    private PreparedStatement tryLockStatement;

    @Mock
    private PreparedStatement unlockStatement;

    @Mock
    private ResultSet tryLockResultSet;

    private PostgreSqlAdvisoryLockService service;

    @BeforeEach
    void setUp() {
        service = new PostgreSqlAdvisoryLockService(dataSource);
    }

    @Test
    @DisplayName("락을 획득하면 작업을 실행하고 락을 해제한다")
    void executesTaskAndUnlocksWhenLockAcquired() throws Exception {
        givenTryLockResult(true);
        givenUnlockStatement();
        AtomicBoolean executed = new AtomicBoolean(false);

        boolean result = service.executeWithLock(LOCK_KEY, () -> executed.set(true));

        assertThat(result).isTrue();
        assertThat(executed).isTrue();
        verify(tryLockStatement).setLong(eq(1), anyLong());
        verify(unlockStatement).setLong(eq(1), anyLong());
        verify(unlockStatement).executeQuery();
    }

    @Test
    @DisplayName("락을 획득하지 못하면 작업을 실행하지 않고 false를 반환한다")
    void returnsFalseWithoutExecutingTaskWhenLockNotAcquired() throws Exception {
        givenTryLockResult(false);
        AtomicBoolean executed = new AtomicBoolean(false);

        boolean result = service.executeWithLock(LOCK_KEY, () -> executed.set(true));

        assertThat(result).isFalse();
        assertThat(executed).isFalse();
        verify(connection, never()).prepareStatement(UNLOCK_SQL);
    }

    @Test
    @DisplayName("작업 중 예외가 발생해도 락을 해제하고 원래 예외를 전파한다")
    void unlocksAndPropagatesOriginalExceptionWhenTaskFails() throws Exception {
        givenTryLockResult(true);
        givenUnlockStatement();
        RuntimeException cause = new RuntimeException("task failure");

        assertThatThrownBy(() -> service.executeWithLock(LOCK_KEY, () -> {
            throw cause;
        })).isSameAs(cause);

        verify(unlockStatement).executeQuery();
    }

    @Test
    @DisplayName("커넥션 획득 실패는 락 커스텀 예외로 감싼다")
    void wrapsConnectionFailure() throws Exception {
        SQLException cause = new SQLException("connection failure");
        when(dataSource.getConnection()).thenThrow(cause);

        assertThatThrownBy(() -> service.executeWithLock(LOCK_KEY, () -> {
        })).isInstanceOfSatisfying(AdvisoryLockException.class, e -> {
            assertThat(e.getApiErrorCode()).isEqualTo(ApiErrorCode.ADVISORY_LOCK_FAILED);
            assertThat(e.getCause()).isSameAs(cause);
            assertThat(e.getDetails())
                    .containsEntry("lockKey", LOCK_KEY)
                    .containsEntry("operation", "connection");
        });
    }

    @Test
    @DisplayName("락 획득 SQL 실패는 락 커스텀 예외로 감싼다")
    void wrapsTryLockFailure() throws Exception {
        SQLException cause = new SQLException("try lock failure");
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(TRY_LOCK_SQL)).thenReturn(tryLockStatement);
        when(tryLockStatement.executeQuery()).thenThrow(cause);

        assertThatThrownBy(() -> service.executeWithLock(LOCK_KEY, () -> {
        })).isInstanceOfSatisfying(AdvisoryLockException.class, e -> {
            assertThat(e.getApiErrorCode()).isEqualTo(ApiErrorCode.ADVISORY_LOCK_FAILED);
            assertThat(e.getCause()).isSameAs(cause);
            assertThat(e.getDetails())
                    .containsEntry("lockKey", LOCK_KEY)
                    .containsEntry("operation", "tryLock");
        });
    }

    @Test
    @DisplayName("락 획득 결과가 비어 있으면 락 커스텀 예외로 처리한다")
    void wrapsEmptyTryLockResult() throws Exception {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(TRY_LOCK_SQL)).thenReturn(tryLockStatement);
        when(tryLockStatement.executeQuery()).thenReturn(tryLockResultSet);
        when(tryLockResultSet.next()).thenReturn(false);

        assertThatThrownBy(() -> service.executeWithLock(LOCK_KEY, () -> {
        })).isInstanceOfSatisfying(AdvisoryLockException.class, e -> {
            assertThat(e.getApiErrorCode()).isEqualTo(ApiErrorCode.ADVISORY_LOCK_FAILED);
            assertThat(e.getCause()).isNull();
            assertThat(e.getDetails())
                    .containsEntry("lockKey", LOCK_KEY)
                    .containsEntry("operation", "tryLockEmptyResult");
        });
    }

    @Test
    @DisplayName("락 해제 SQL 실패는 락 커스텀 예외로 감싼다")
    void wrapsUnlockFailure() throws Exception {
        SQLException cause = new SQLException("unlock failure");
        givenTryLockResult(true);
        when(connection.prepareStatement(UNLOCK_SQL)).thenReturn(unlockStatement);
        when(unlockStatement.executeQuery()).thenThrow(cause);

        assertThatThrownBy(() -> service.executeWithLock(LOCK_KEY, () -> {
        })).isInstanceOfSatisfying(AdvisoryLockException.class, e -> {
            assertThat(e.getApiErrorCode()).isEqualTo(ApiErrorCode.ADVISORY_LOCK_FAILED);
            assertThat(e.getCause()).isSameAs(cause);
            assertThat(e.getDetails())
                    .containsEntry("lockKey", LOCK_KEY)
                    .containsEntry("operation", "unlock");
        });
    }

    @Test
    @DisplayName("작업 예외와 락 해제가 모두 실패하면 원래 작업 예외에 락 해제 예외를 보관한다")
    void addsUnlockFailureAsSuppressedWhenTaskFails() throws Exception {
        SQLException unlockFailure = new SQLException("unlock failure");
        RuntimeException taskFailure = new RuntimeException("task failure");
        givenTryLockResult(true);
        givenUnlockFailure(unlockFailure);

        assertThatThrownBy(() -> service.executeWithLock(LOCK_KEY, () -> {
            throw taskFailure;
        })).isSameAs(taskFailure)
                .satisfies(e -> assertThat(e.getSuppressed())
                        .singleElement()
                        .isInstanceOfSatisfying(AdvisoryLockException.class, suppressed -> {
                            assertThat(suppressed.getCause()).isSameAs(unlockFailure);
                            assertThat(suppressed.getDetails())
                                    .containsEntry("lockKey", LOCK_KEY)
                                    .containsEntry("operation", "unlock");
                        }));
    }

    @Test
    @DisplayName("작업 Error와 락 해제가 모두 실패하면 원래 Error에 락 해제 예외를 보관한다")
    void addsUnlockFailureAsSuppressedWhenTaskThrowsError() throws Exception {
        SQLException unlockFailure = new SQLException("unlock failure");
        AssertionError taskFailure = new AssertionError("task error");
        givenTryLockResult(true);
        givenUnlockFailure(unlockFailure);

        assertThatThrownBy(() -> service.executeWithLock(LOCK_KEY, () -> {
            throw taskFailure;
        })).isSameAs(taskFailure)
                .satisfies(e -> assertThat(e.getSuppressed())
                        .singleElement()
                        .isInstanceOfSatisfying(AdvisoryLockException.class, suppressed -> {
                            assertThat(suppressed.getCause()).isSameAs(unlockFailure);
                            assertThat(suppressed.getDetails())
                                    .containsEntry("lockKey", LOCK_KEY)
                                    .containsEntry("operation", "unlock");
                        }));
    }

    private void givenTryLockResult(boolean locked) throws Exception {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(TRY_LOCK_SQL)).thenReturn(tryLockStatement);
        when(tryLockStatement.executeQuery()).thenReturn(tryLockResultSet);
        when(tryLockResultSet.next()).thenReturn(true);
        when(tryLockResultSet.getBoolean(1)).thenReturn(locked);
    }

    private void givenUnlockStatement() throws Exception {
        when(connection.prepareStatement(UNLOCK_SQL)).thenReturn(unlockStatement);
    }

    private void givenUnlockFailure(SQLException cause) throws Exception {
        when(connection.prepareStatement(UNLOCK_SQL)).thenReturn(unlockStatement);
        when(unlockStatement.executeQuery()).thenThrow(cause);
    }

}
