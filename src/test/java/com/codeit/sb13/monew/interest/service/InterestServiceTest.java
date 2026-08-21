package com.codeit.sb13.monew.interest.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codeit.sb13.monew.global.dto.CursorPageResponseDto;
import com.codeit.sb13.monew.global.exception.interest.InterestNameDuplicatedException;
import com.codeit.sb13.monew.global.exception.interest.InterestNotFoundException;
import com.codeit.sb13.monew.interest.controller.dto.InterestResponse;
import com.codeit.sb13.monew.interest.domain.Interest;
import com.codeit.sb13.monew.interest.repository.InterestRepository;
import com.codeit.sb13.monew.interest.repository.SubscribeRepository;
import com.codeit.sb13.monew.interest.repository.dto.InterestSearchCondition;
import com.codeit.sb13.monew.interest.repository.dto.InterestSearchPage;
import com.codeit.sb13.monew.interest.repository.dto.InterestSearchRow;
import com.codeit.sb13.monew.interest.service.dto.InterestCreateCommand;
import com.codeit.sb13.monew.interest.service.dto.InterestOrderBy;
import com.codeit.sb13.monew.interest.service.dto.InterestSearchCommand;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Isolated;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * {@code create_uniqueViolationUnderTurkishDefaultLocale_throwsException}가
 * {@link Locale#setDefault}로 JVM 전역 상태를 일시적으로 바꾸므로,
 * 지금은 JUnit 병렬 실행이 꺼져 있어 문제가 없지만 나중에 켜지더라도
 * 다른 테스트 클래스와 동시에 실행되지 않도록 {@link Isolated}를 붙여둔다.
 */
@Isolated
@ExtendWith(MockitoExtension.class)
class InterestServiceTest {

    @Mock
    InterestRepository interestRepository;

    @Mock
    SubscribeRepository subscribeRepository;

    @Captor
    ArgumentCaptor<Interest> interestCaptor;

    @InjectMocks
    InterestServiceImpl interestServiceImpl;

    private Interest interestWithIdAndCreatedAt(String name, LocalDateTime createdAt) {
        Interest interest = Interest.create(name);
        ReflectionTestUtils.setField(interest, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(interest, "createdAt", createdAt);
        return interest;
    }

    @Test
    @DisplayName("사전 검사에서 이름이 이미 존재하면 InterestNameDuplicatedException을 던지고 저장하지 않는다")
    void create_duplicateName_throwsException() {
        // given
        InterestCreateCommand command = new InterestCreateCommand("스포츠", List.of("축구"));
        when(interestRepository.existsByName(command.name())).thenReturn(true);

        // when & then
        assertThatThrownBy(() -> interestServiceImpl.create(command))
                .isInstanceOf(InterestNameDuplicatedException.class);

        verify(interestRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("기존 이름과 80% 이상 유사한 이름이면 InterestNameDuplicatedException을 던지고 저장하지 않는다")
    void create_nameTooSimilarToExisting_throwsException() {
        // given
        InterestCreateCommand command = new InterestCreateCommand("스포츠뉴스판", List.of("축구"));

        when(interestRepository.existsByName(command.name())).thenReturn(false);
        // "스포츠뉴스"(5자)와 "스포츠뉴스판"(6자)은 편집 거리 1로 유사도 1 - 1/6 ≈ 0.833(83.3%).
        // 새 이름 길이 6이면 후보 길이 범위는 [5, 7]이라 길이 5인 "스포츠뉴스"가 후보에 포함된다.
        when(interestRepository.findNamesByLengthBetween(5, 7)).thenReturn(List.of("스포츠뉴스"));

        // when & then
        assertThatThrownBy(() -> interestServiceImpl.create(command))
                .isInstanceOf(InterestNameDuplicatedException.class);

        verify(interestRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("기존 이름과 유사도가 정확히 80%(임계값)인 이름이면 InterestNameDuplicatedException을 던지고 저장하지 않는다")
    void create_nameExactlyAtSimilarityThreshold_throwsException() {
        // given
        InterestCreateCommand command = new InterestCreateCommand("게임소식판", List.of("게임"));

        when(interestRepository.existsByName(command.name())).thenReturn(false);
        // "게임소식란"과 "게임소식판"은 둘 다 길이 5, 마지막 글자 하나만 달라 편집 거리 1이고,
        // 유사도는 1 - 1/5 = 0.8로 임계값과 정확히 같다. >= 비교가 > 로 바뀌는 회귀를 잡기 위한 경계값 테스트.
        // 새 이름 길이 5이면 후보 길이 범위는 [4, 6]이라 길이 5인 "게임소식란"이 후보에 포함된다.
        when(interestRepository.findNamesByLengthBetween(4, 6)).thenReturn(List.of("게임소식란"));

        // when & then
        assertThatThrownBy(() -> interestServiceImpl.create(command))
                .isInstanceOf(InterestNameDuplicatedException.class);

        verify(interestRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("기존 이름들과 유사도가 80% 미만이면 정상적으로 등록된다")
    void create_nameNotSimilarEnough_savesSuccessfully() {
        // given
        InterestCreateCommand command = new InterestCreateCommand("스포츠", List.of("축구", "야구"));
        UUID generatedId = UUID.randomUUID();

        when(interestRepository.existsByName(command.name())).thenReturn(false);
        // "스포츠"(길이 3)의 후보 길이 범위는 [3, 3]이다. "가나다"는 길이는 같지만 모든 글자가
        // 달라 편집 거리 3, 유사도 0이라 후보로 조회되더라도 임계값 미만으로 걸러진다.
        when(interestRepository.findNamesByLengthBetween(3, 3)).thenReturn(List.of("가나다"));
        when(interestRepository.saveAndFlush(any(Interest.class))).thenAnswer(invocation -> {
            Interest saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", generatedId);
            return saved;
        });

        // when
        InterestResponse response = interestServiceImpl.create(command);

        // then
        assertThat(response.id()).isEqualTo(generatedId);
        verify(interestRepository).saveAndFlush(any(Interest.class));
    }

    @Test
    @DisplayName("정상 요청이면 관심사와 키워드를 저장하고, 구독자 0명/미구독 상태로 응답한다")
    void create_validRequest_savesAndReturnsResponse() {
        // given
        InterestCreateCommand command = new InterestCreateCommand("스포츠", List.of("축구", "야구"));
        UUID generatedId = UUID.randomUUID();

        when(interestRepository.existsByName(command.name())).thenReturn(false);
        when(interestRepository.findNamesByLengthBetween(anyInt(), anyInt())).thenReturn(List.of());
        when(interestRepository.saveAndFlush(any(Interest.class))).thenAnswer(invocation -> {
            Interest saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", generatedId);
            return saved;
        });

        // when
        InterestResponse response = interestServiceImpl.create(command);

        // then
        assertThat(response.id()).isEqualTo(generatedId);
        assertThat(response.name()).isEqualTo("스포츠");
        assertThat(response.keywords()).containsExactly("축구", "야구");
        assertThat(response.subscriberCount()).isEqualTo(0L);
        assertThat(response.subscribedByMe()).isFalse();

        verify(interestRepository).saveAndFlush(interestCaptor.capture());
        Interest captured = interestCaptor.getValue();
        assertThat(captured.getName()).isEqualTo("스포츠");
        assertThat(captured.getKeywords()).hasSize(2);
    }

    @Test
    @DisplayName("사전 검사를 통과했지만 저장 시점에 이름 중복 제약 위반이 발생하면 InterestNameDuplicatedException을 던진다")
    void create_uniqueViolationDetectedAtSaveTime_throwsException() {
        // given
        InterestCreateCommand command = new InterestCreateCommand("스포츠", List.of("축구"));

        DataIntegrityViolationException original = new DataIntegrityViolationException(
                "duplicate key value violates unique constraint \"uk_interests_name\"");

        when(interestRepository.existsByName(command.name())).thenReturn(false);
        when(interestRepository.findNamesByLengthBetween(anyInt(), anyInt())).thenReturn(List.of());
        when(interestRepository.saveAndFlush(any(Interest.class))).thenThrow(original);

        // when & then
        assertThatThrownBy(() -> interestServiceImpl.create(command))
                .isInstanceOf(InterestNameDuplicatedException.class)
                .hasCause(original);

        verify(interestRepository).existsByName(command.name());
    }

    @Test
    @DisplayName("H2처럼 제약 이름을 대문자로 돌려주는 DB에서도 이름 중복으로 판별한다")
    void create_uniqueViolationWithUppercaseConstraintName_throwsException() {
        // given
        InterestCreateCommand command = new InterestCreateCommand("스포츠", List.of("축구"));

        // 실제 H2(test 프로파일)가 던지는 메시지 형태를 그대로 옮긴 것
        DataIntegrityViolationException original = new DataIntegrityViolationException(
                "Unique index or primary key violation: "
                        + "\"PUBLIC.UK_INTERESTS_NAME INDEX PUBLIC.UK_INTERESTS_NAME_INDEX_C "
                        + "ON PUBLIC.INTERESTS(NAME NULLS FIRST) VALUES ( /* 1 */ '스포츠' )\"");

        when(interestRepository.existsByName(command.name())).thenReturn(false);
        when(interestRepository.findNamesByLengthBetween(anyInt(), anyInt())).thenReturn(List.of());
        when(interestRepository.saveAndFlush(any(Interest.class))).thenThrow(original);

        // when & then
        assertThatThrownBy(() -> interestServiceImpl.create(command))
                .isInstanceOf(InterestNameDuplicatedException.class)
                .hasCause(original);
    }

    @Test
    @DisplayName("JVM 기본 로케일이 튀르키예어여도 대문자 I가 포함된 제약 이름을 이름 중복으로 판별한다")
    void create_uniqueViolationUnderTurkishDefaultLocale_throwsException() {
        Locale originalDefault = Locale.getDefault();
        try {
            // given: toLowerCase()를 로케일 없이 쓰면 튀르키예어 로케일에서
            // "INTERESTS"의 I가 점 없는 ı로 바뀌어 매칭에 실패할 수 있다.
            Locale.setDefault(new Locale("tr", "TR"));

            InterestCreateCommand command = new InterestCreateCommand("스포츠", List.of("축구"));

            DataIntegrityViolationException original = new DataIntegrityViolationException(
                    "Unique index or primary key violation: "
                            + "\"PUBLIC.UK_INTERESTS_NAME INDEX PUBLIC.UK_INTERESTS_NAME_INDEX_C "
                            + "ON PUBLIC.INTERESTS(NAME NULLS FIRST) VALUES ( /* 1 */ '스포츠' )\"");

            when(interestRepository.existsByName(command.name())).thenReturn(false);
            when(interestRepository.findNamesByLengthBetween(anyInt(), anyInt())).thenReturn(List.of());
            when(interestRepository.saveAndFlush(any(Interest.class))).thenThrow(original);

            // when & then
            assertThatThrownBy(() -> interestServiceImpl.create(command))
                    .isInstanceOf(InterestNameDuplicatedException.class)
                    .hasCause(original);
        } finally {
            Locale.setDefault(originalDefault);
        }
    }

    @Test
    @DisplayName("이름과 무관한 제약 위반이면 원래 예외를 그대로 던진다")
    void create_unrelatedConstraintViolation_rethrowsOriginalException() {
        // given
        InterestCreateCommand command = new InterestCreateCommand("스포츠", List.of("축구"));

        when(interestRepository.existsByName(command.name())).thenReturn(false);
        when(interestRepository.findNamesByLengthBetween(anyInt(), anyInt())).thenReturn(List.of());
        when(interestRepository.saveAndFlush(any(Interest.class)))
                .thenThrow(new DataIntegrityViolationException(
                        "null value in column \"name\" violates not-null constraint"));

        // when & then
        assertThatThrownBy(() -> interestServiceImpl.create(command))
                .isInstanceOf(DataIntegrityViolationException.class)
                .isNotInstanceOf(InterestNameDuplicatedException.class);
    }

    @Test
    @DisplayName("원인 예외 메시지가 null이면 이름 중복이 아닌 것으로 판별해 원래 예외를 그대로 던진다")
    void create_uniqueViolationWithNullMessage_rethrowsOriginalException() {
        // given
        InterestCreateCommand command = new InterestCreateCommand("스포츠", List.of("축구"));

        DataIntegrityViolationException original = new DataIntegrityViolationException(null);

        when(interestRepository.existsByName(command.name())).thenReturn(false);
        when(interestRepository.findNamesByLengthBetween(anyInt(), anyInt())).thenReturn(List.of());
        when(interestRepository.saveAndFlush(any(Interest.class))).thenThrow(original);

        // when & then
        assertThatThrownBy(() -> interestServiceImpl.create(command))
                .isInstanceOf(DataIntegrityViolationException.class)
                .isNotInstanceOf(InterestNameDuplicatedException.class);
    }

    @Test
    @DisplayName("정렬 기준이 이름이면 마지막 항목의 이름을 다음 커서로 돌려준다")
    void search_orderByName_buildsNextCursorFromLastItemName() {
        // given
        LocalDateTime now = LocalDateTime.now();
        Interest first = interestWithIdAndCreatedAt("가나다", now);
        Interest last = interestWithIdAndCreatedAt("나비", now.plusSeconds(1));

        InterestSearchCommand command =
                new InterestSearchCommand(null, InterestOrderBy.NAME, Sort.Direction.ASC, null, null, null, 10, null);
        InterestSearchPage page = new InterestSearchPage(
                List.of(new InterestSearchRow(first, 0L, false), new InterestSearchRow(last, 0L, false)),
                true, 2L);

        when(interestRepository.search(new InterestSearchCondition(
                command.keyword(), command.orderBy(), command.direction(),
                command.cursor(), command.after(), command.idAfter(), command.limit(), command.requestUserId()
        ))).thenReturn(page);

        // when
        CursorPageResponseDto<InterestResponse> response = interestServiceImpl.search(command);

        // then
        assertThat(response.content()).hasSize(2);
        assertThat(response.nextCursor()).isEqualTo("나비");
        assertThat(response.nextAfter()).isEqualTo(now.plusSeconds(1).toString());
        assertThat(response.nextIdAfter()).isEqualTo(last.getId().toString());
        assertThat(response.hasNext()).isTrue();
        assertThat(response.totalElements()).isEqualTo(2L);
    }

    @Test
    @DisplayName("정렬 기준이 구독자 수면 마지막 항목의 구독자 수를 다음 커서로 돌려준다")
    void search_orderBySubscriberCount_buildsNextCursorFromLastItemSubscriberCount() {
        // given
        LocalDateTime now = LocalDateTime.now();
        Interest first = interestWithIdAndCreatedAt("스포츠", now);
        Interest last = interestWithIdAndCreatedAt("여행", now.plusSeconds(1));

        InterestSearchCommand command = new InterestSearchCommand(
                null, InterestOrderBy.SUBSCRIBER_COUNT, Sort.Direction.DESC, null, null, null, 10, null);
        InterestSearchPage page = new InterestSearchPage(
                List.of(new InterestSearchRow(first, 5L, false), new InterestSearchRow(last, 2L, false)),
                false,
                2L
        );

        when(interestRepository.search(new InterestSearchCondition(
                command.keyword(), command.orderBy(), command.direction(),
                command.cursor(), command.after(), command.idAfter(), command.limit(), command.requestUserId()
        ))).thenReturn(page);

        // when
        CursorPageResponseDto<InterestResponse> response = interestServiceImpl.search(command);

        // then
        assertThat(response.content()).extracting(InterestResponse::subscriberCount)
                .containsExactly(5L, 2L);
        assertThat(response.nextCursor()).isEqualTo("2");
        assertThat(response.hasNext()).isFalse();
    }

    @Test
    @DisplayName("조회 결과가 비어 있으면 커서는 null, 목록은 빈 상태로 응답한다")
    void search_emptyPage_returnsNullCursorsAndEmptyContent() {
        // given
        InterestSearchCommand command =
                new InterestSearchCommand("없는검색어", InterestOrderBy.NAME, Sort.Direction.ASC, null, null, null, 10, null);
        InterestSearchPage page = new InterestSearchPage(List.of(), false, 0L);

        when(interestRepository.search(new InterestSearchCondition(
                command.keyword(), command.orderBy(), command.direction(),
                command.cursor(), command.after(), command.idAfter(), command.limit(), command.requestUserId()
        ))).thenReturn(page);

        // when
        CursorPageResponseDto<InterestResponse> response = interestServiceImpl.search(command);

        // then
        assertThat(response.content()).isEmpty();
        assertThat(response.nextCursor()).isNull();
        assertThat(response.nextAfter()).isNull();
        assertThat(response.hasNext()).isFalse();
        assertThat(response.totalElements()).isEqualTo(0L);
    }

    @Test
    @DisplayName("커맨드의 검색 조건을 그대로 리포지토리에 전달한다")
    void search_passesCommandFieldsToRepository() {
        // given
        UUID requestUserId = UUID.randomUUID();
        UUID idAfter = UUID.randomUUID();
        InterestSearchCommand command = new InterestSearchCommand(
                "스포츠", InterestOrderBy.SUBSCRIBER_COUNT, Sort.Direction.DESC,
                "3", LocalDateTime.now(), idAfter, 20, requestUserId);
        InterestSearchPage page = new InterestSearchPage(List.of(), false, 0L);

        when(interestRepository.search(new InterestSearchCondition(
                command.keyword(), command.orderBy(), command.direction(),
                command.cursor(), command.after(), command.idAfter(), command.limit(), command.requestUserId()
        ))).thenReturn(page);

        // when
        interestServiceImpl.search(command);

        // then
        verify(interestRepository).search(new InterestSearchCondition(
                "스포츠", InterestOrderBy.SUBSCRIBER_COUNT, Sort.Direction.DESC,
                "3", command.after(), idAfter, 20, requestUserId));
    }

    @Test
    @DisplayName("존재하는 관심사를 삭제하면 구독을 먼저 지운 뒤 관심사를 지운다")
    void delete_existingInterest_deletesSubscriptionsBeforeInterest() {
        // given
        Interest interest = interestWithIdAndCreatedAt("스포츠", LocalDateTime.now());
        when(interestRepository.findById(interest.getId())).thenReturn(Optional.of(interest));

        // when
        interestServiceImpl.delete(interest.getId());

        // then: Interest.keywords는 cascade로 함께 지워지지만 구독은 그렇지 않으므로,
        // FK 제약을 위반하지 않으려면 반드시 구독을 먼저 지운 뒤 관심사를 지워야 한다.
        InOrder order = inOrder(subscribeRepository, interestRepository);
        order.verify(subscribeRepository).deleteByInterest_Id(interest.getId());
        order.verify(interestRepository).delete(interest);
    }

    @Test
    @DisplayName("존재하지 않는 관심사를 삭제하려 하면 InterestNotFoundException을 던지고 아무것도 지우지 않는다")
    void delete_nonExistingInterest_throwsExceptionAndDeletesNothing() {
        // given
        UUID interestId = UUID.randomUUID();
        when(interestRepository.findById(interestId)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> interestServiceImpl.delete(interestId))
                .isInstanceOf(InterestNotFoundException.class);

        verify(subscribeRepository, never()).deleteByInterest_Id(any());
        verify(interestRepository, never()).delete(any());
    }
}
