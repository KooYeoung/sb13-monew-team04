package com.codeit.sb13.monew.global.exception.readmodel;

import com.codeit.sb13.monew.activity.service.UserActivityReadSourceType;
import com.codeit.sb13.monew.global.exception.ApiErrorCode;
import java.util.Map;

/** 활동내역 조회 source 설정과 MongoDB 기반 활성화 설정이 서로 호환되지 않을 때 발생한다. */
public class ReadModelSourceConfigurationException extends ReadModelQueryException {

    public ReadModelSourceConfigurationException(
            UserActivityReadSourceType readSource,
            boolean mongodbEnabled,
            String reason
    ) {
        super(ApiErrorCode.INTERNAL_SERVER_ERROR, Map.of(
                "readSource", readSource,
                "mongodbEnabled", mongodbEnabled,
                "reason", reason
        ));
    }
}
