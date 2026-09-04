package com.codeit.sb13.monew.activity.service.impl;

import com.codeit.sb13.monew.activity.mongo.config.MongoReadModelProperties;
import com.codeit.sb13.monew.activity.mongo.query.MongoUserActivityReadSource;
import com.codeit.sb13.monew.activity.service.UserActivityReadSource;
import com.codeit.sb13.monew.activity.service.UserActivityReadSourceType;
import com.codeit.sb13.monew.activity.service.UserActivitySections;
import com.codeit.sb13.monew.activity.service.config.UserActivityReadProperties;
import com.codeit.sb13.monew.global.exception.readmodel.ReadModelSourceConfigurationException;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/** 설정에 따라 RDB 또는 MongoDB 활동내역 source를 선택하고 MongoDB 실패를 RDB로 우회한다. */
@Slf4j
@Primary
@Component
public class UserActivityReadSourceRouter implements UserActivityReadSource {

    private final RdbUserActivityReadSource rdbSource;
    private final MongoUserActivityReadSource mongoSource;
    private final UserActivityReadProperties readProperties;

    public UserActivityReadSourceRouter(
            RdbUserActivityReadSource rdbSource,
            MongoUserActivityReadSource mongoSource,
            UserActivityReadProperties readProperties,
            MongoReadModelProperties mongoProperties
    ) {
        validateConfiguration(readProperties, mongoProperties);
        this.rdbSource = rdbSource;
        this.mongoSource = mongoSource;
        this.readProperties = readProperties;
    }

    @Override
    public UserActivitySections read(UUID userId) {
        if (readProperties.readSource() == UserActivityReadSourceType.RDB) {
            return rdbSource.read(userId);
        }

        try {
            return mongoSource.read(userId);
        } catch (RuntimeException mongoFailure) {
            log.warn(
                    "MongoDB 활동내역 조회에 실패해 RDB로 fallback합니다. "
                            + "userId={}, exceptionType={}",
                    userId,
                    mongoFailure.getClass().getSimpleName(),
                    mongoFailure
            );
            return readFromRdb(userId, mongoFailure);
        }
    }

    private UserActivitySections readFromRdb(UUID userId, RuntimeException mongoFailure) {
        try {
            return rdbSource.read(userId);
        } catch (RuntimeException rdbFailure) {
            if (rdbFailure != mongoFailure) {
                rdbFailure.addSuppressed(mongoFailure);
            }
            throw rdbFailure;
        }
    }

    private void validateConfiguration(
            UserActivityReadProperties readProperties,
            MongoReadModelProperties mongoProperties
    ) {
        if (readProperties.readSource() == UserActivityReadSourceType.MONGODB
                && !mongoProperties.enabled()) {
            throw new ReadModelSourceConfigurationException(
                    readProperties.readSource(),
                    mongoProperties.enabled(),
                    "MongoDB 조회를 선택하려면 monew.mongodb.enabled=true가 필요합니다."
            );
        }
    }
}
