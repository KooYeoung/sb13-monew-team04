package com.codeit.sb13.monew.global.exception.readmodel;

import com.codeit.sb13.monew.global.exception.ApiErrorCode;
import java.util.Map;

/** MongoDB Read Model의 필수 필드를 기존 활동내역 DTO로 변환할 수 없을 때 발생한다. */
public class ReadModelDocumentMappingException extends ReadModelQueryException {

    public ReadModelDocumentMappingException(
            String documentId,
            String field,
            Object value,
            Throwable cause
    ) {
        super(ApiErrorCode.INTERNAL_SERVER_ERROR, Map.of(
                "documentId", String.valueOf(documentId),
                "field", field,
                "value", String.valueOf(value)
        ), cause);
    }
}
