package com.codeit.sb13.monew.global.service.impl;

import com.codeit.sb13.monew.global.exception.article.ArticleAdvisoryLockException;
import com.codeit.sb13.monew.global.service.AdvisoryLockService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

@Service
@RequiredArgsConstructor
public class PostgreSqlAdvisoryLockService implements AdvisoryLockService {

    private static final String TRY_LOCK_SQL = "SELECT pg_try_advisory_lock(?)";
    private static final String UNLOCK_SQL = "SELECT pg_advisory_unlock(?)";
    private static final String HASH_ALGORITHM = "SHA-256";

    private final DataSource dataSource;

    @Override
    public boolean executeWithLock(String lockKey, Runnable task) {
        long lockId = toLockId(lockKey);

        try (Connection connection = dataSource.getConnection()) {
            if (!tryLock(connection, lockId, lockKey)) {
                return false;
            }

            return executeTaskAndUnlock(connection, lockId, lockKey, task);
        } catch (SQLException e) {
            throw new ArticleAdvisoryLockException(lockKey, "connection", e);
        }
    }

    private boolean executeTaskAndUnlock(Connection connection, long lockId, String lockKey, Runnable task) {
        RuntimeException runtimeFailure = null;
        Error errorFailure = null;

        try {
            task.run();
            return true;
        } catch (RuntimeException e) {
            runtimeFailure = e;
            throw e;
        } catch (Error e) {
            errorFailure = e;
            throw e;
        } finally {
            unlockAfterTask(connection, lockId, lockKey, runtimeFailure, errorFailure);
        }
    }

    private void unlockAfterTask(
            Connection connection,
            long lockId,
            String lockKey,
            RuntimeException runtimeFailure,
            Error errorFailure
    ) {
        try {
            unlock(connection, lockId, lockKey);
        } catch (ArticleAdvisoryLockException e) {
            if (runtimeFailure != null) {
                runtimeFailure.addSuppressed(e);
                return;
            }
            if (errorFailure != null) {
                errorFailure.addSuppressed(e);
                return;
            }
            throw e;
        }
    }

    private boolean tryLock(Connection connection, long lockId, String lockKey) {
        try (PreparedStatement statement = connection.prepareStatement(TRY_LOCK_SQL)) {
            statement.setLong(1, lockId);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getBoolean(1);
                }
                throw new ArticleAdvisoryLockException(lockKey, "tryLockEmptyResult");
            }
        } catch (SQLException e) {
            throw new ArticleAdvisoryLockException(lockKey, "tryLock", e);
        }
    }

    private void unlock(Connection connection, long lockId, String lockKey) {
        try (PreparedStatement statement = connection.prepareStatement(UNLOCK_SQL)) {
            statement.setLong(1, lockId);
            statement.executeQuery();
        } catch (SQLException e) {
            throw new ArticleAdvisoryLockException(lockKey, "unlock", e);
        }
    }

    private long toLockId(String lockKey) {
        byte[] digest = sha256(lockKey);
        return ByteBuffer.wrap(digest, 0, Long.BYTES).getLong();
    }

    private byte[] sha256(String lockKey) {
        try {
            return MessageDigest.getInstance(HASH_ALGORITHM)
                    .digest(lockKey.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new ArticleAdvisoryLockException(lockKey, "hash", e);
        }
    }
}
