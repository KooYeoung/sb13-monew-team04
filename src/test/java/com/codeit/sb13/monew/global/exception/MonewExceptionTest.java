package com.codeit.sb13.monew.global.exception;

import com.codeit.sb13.monew.global.exception.article.ArticleException;
import com.codeit.sb13.monew.global.exception.comment.CommentException;
import com.codeit.sb13.monew.global.exception.interest.InterestException;
import com.codeit.sb13.monew.global.exception.notification.NotificationException;
import com.codeit.sb13.monew.global.exception.user.UserException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MonewException 단위 테스트")
class MonewExceptionTest {

    @Test
    @DisplayName("원인 예외를 전달하면 getCause에 보존한다")
    void preservesCauseWhenCauseIsProvided() {
        // given
        Throwable cause = new IllegalStateException("original cause");

        // when
        MonewException exception = new TestMonewException(
                ApiErrorCode.INVALID_REQUEST,
                Map.of("field", "name"),
                cause
        );

        // then
        assertThat(exception.getCause()).isSameAs(cause);
        assertThat(exception.getMessage()).isEqualTo(ApiErrorCode.INVALID_REQUEST.getMessage());
        assertThat(exception.getDetails()).containsEntry("field", "name");
    }

    @Test
    @DisplayName("기존 생성자 경로는 원인 예외 없이 기존 응답 정보를 유지한다")
    void preservesExistingConstructorBehavior() {
        // when
        MonewException exception = new TestMonewException(ApiErrorCode.INVALID_REQUEST, null);

        // then
        assertThat(exception.getCause()).isNull();
        assertThat(exception.getMessage()).isEqualTo(ApiErrorCode.INVALID_REQUEST.getMessage());
        assertThat(exception.getApiErrorCode()).isEqualTo(ApiErrorCode.INVALID_REQUEST);
        assertThat(exception.getDetails()).isEmpty();
    }

    @Test
    @DisplayName("기존 생성자 경로는 initCause 우회 방식과 호환된다")
    void remainsCompatibleWithInitCause() {
        // given
        MonewException exception = new TestMonewException(ApiErrorCode.INVALID_REQUEST, null);
        Throwable cause = new IllegalStateException("legacy cause");

        // when
        exception.initCause(cause);

        // then
        assertThat(exception.getCause()).isSameAs(cause);
    }

    @Test
    @DisplayName("도메인 추상 예외 생성자 체인에서도 원인 예외를 보존한다")
    void preservesCauseThroughDomainAbstractExceptions() {
        // given
        Throwable cause = new IllegalArgumentException("domain cause");

        // when
        List<MonewException> exceptions = List.of(
                new TestArticleException(cause),
                new TestInterestException(cause),
                new TestUserException(cause),
                new TestNotificationException(cause),
                new TestCommentException(cause)
        );

        // then
        assertThat(exceptions)
                .allSatisfy(exception -> assertThat(exception.getCause()).isSameAs(cause));
    }

    private static class TestMonewException extends MonewException {

        private TestMonewException(ApiErrorCode apiErrorCode, Map<String, Object> details) {
            super(apiErrorCode, details);
        }

        private TestMonewException(ApiErrorCode apiErrorCode, Map<String, Object> details, Throwable cause) {
            super(apiErrorCode, details, cause);
        }
    }

    private static class TestArticleException extends ArticleException {

        private TestArticleException(Throwable cause) {
            super(ApiErrorCode.ARTICLE_DUPLICATE, Map.of("domain", "article"), cause);
        }
    }

    private static class TestInterestException extends InterestException {

        private TestInterestException(Throwable cause) {
            super(ApiErrorCode.INTEREST_NAME_DUPLICATED, Map.of("domain", "interest"), cause);
        }
    }

    private static class TestUserException extends UserException {

        private TestUserException(Throwable cause) {
            super(ApiErrorCode.USER_ALREADY_DELETED, Map.of("domain", "user"), cause);
        }
    }

    private static class TestNotificationException extends NotificationException {

        private TestNotificationException(Throwable cause) {
            super(ApiErrorCode.NOTIFICATION_NOT_FOUND, Map.of("domain", "notification"), cause);
        }
    }

    private static class TestCommentException extends CommentException {

        private TestCommentException(Throwable cause) {
            super(ApiErrorCode.COMMENT_NOT_FOUND, Map.of("domain", "comment"), cause);
        }
    }
}
