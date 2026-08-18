package com.codeit.sb13.monew.interest.controller.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InterestCreateRequestTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        validatorFactory.close();
    }

    @Nested
    @DisplayName("검증")
    class ValidationTest {

        @Test
        @DisplayName("이름과 키워드가 모두 유효하면 위반 사항이 없다")
        void validRequest_hasNoViolations() {
            // given
            InterestCreateRequest request = new InterestCreateRequest("스포츠", List.of("축구"));

            // when
            Set<ConstraintViolation<InterestCreateRequest>> violations = validator.validate(request);

            // then
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("이름이 비어 있으면 위반이 발생한다")
        void blankName_violatesConstraint() {
            // given
            InterestCreateRequest request = new InterestCreateRequest("", List.of("축구"));

            // when
            Set<ConstraintViolation<InterestCreateRequest>> violations = validator.validate(request);

            // then
            assertThat(violations)
                    .extracting(ConstraintViolation::getMessage)
                    .contains("관심사 이름은 필수입니다.");
        }

        @Test
        @DisplayName("이름이 50자를 넘으면 위반이 발생한다")
        void tooLongName_violatesConstraint() {
            // given
            String tooLongName = "가".repeat(51);
            InterestCreateRequest request = new InterestCreateRequest(tooLongName, List.of("축구"));

            // when
            Set<ConstraintViolation<InterestCreateRequest>> violations = validator.validate(request);

            // then
            assertThat(violations)
                    .extracting(ConstraintViolation::getMessage)
                    .contains("관심사 이름은 50자를 넘을 수 없습니다.");
        }

        @Test
        @DisplayName("키워드가 비어 있으면 위반이 발생한다")
        void emptyKeywords_violatesConstraint() {
            // given
            InterestCreateRequest request = new InterestCreateRequest("스포츠", List.of());

            // when
            Set<ConstraintViolation<InterestCreateRequest>> violations = validator.validate(request);

            // then
            assertThat(violations)
                    .extracting(ConstraintViolation::getMessage)
                    .contains("키워드는 최소 1개 이상 등록해야 합니다.");
        }

        @Test
        @DisplayName("키워드 목록에 빈 문자열이 섞여 있으면 위반이 발생한다")
        void blankKeywordInList_violatesConstraint() {
            // given
            InterestCreateRequest request = new InterestCreateRequest("스포츠", List.of("축구", ""));

            // when
            Set<ConstraintViolation<InterestCreateRequest>> violations = validator.validate(request);

            // then
            assertThat(violations)
                    .extracting(ConstraintViolation::getMessage)
                    .contains("키워드는 빈 값일 수 없습니다.");
        }
    }
}
