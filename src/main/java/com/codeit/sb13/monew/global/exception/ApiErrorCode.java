package com.codeit.sb13.monew.global.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ApiErrorCode {

    // USER USR
    USER_NOT_FOUND("USR_001", HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
    DUPLICATE_EMAIL("USR_002", HttpStatus.CONFLICT,"이미 사용 중인 이메일입니다."),

    // ARTICLE ART
    ARTICLE_NOT_FOUND("ART_001", HttpStatus.NOT_FOUND, "뉴스를 찾을 수 없습니다."),

    // INTEREST INT
    INTEREST_NOT_FOUND("INT_001", HttpStatus.NOT_FOUND, "관심사를 찾을 수 없습니다."),
    INTEREST_NAME_INVALID("INT_002", HttpStatus.BAD_REQUEST, "이름은 비어있을 수 없고 50자를 넘을 수 없습니다."),
    INTEREST_KEYWORD_REQUIRED("INT_003", HttpStatus.BAD_REQUEST, "관심사에는 최소 1개의 키워드가 있어야 합니다."),
    INTEREST_KEYWORD_INVALID("INT_004", HttpStatus.BAD_REQUEST, "키워드는 비어있을 수 없고 50자를 넘을 수 없습니다."),

    // NOTIFICATION NTF
    NOTIFICATION_NOT_FOUND("NTF_001", HttpStatus.NOT_FOUND, "알림을 찾을 수 없습니다."),

    // COMMENT CMT
    COMMENT_NOT_FOUND("CMT_001", HttpStatus.NOT_FOUND, "댓글을 찾을 수 없습니다."),

    // GLOBAL/COMMON GLB
    INVALID_REQUEST("GLB_001", HttpStatus.BAD_REQUEST, "요청 데이터가 올바르지 않습니다."),
    INTERNAL_SERVER_ERROR("GLB_999", HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류");

    private final String code;
    private final HttpStatus status;
    private final String message;

    ApiErrorCode(String code, HttpStatus status, String message) {
        this.code = code;
        this.status = status;
        this.message = message;
    }
}
