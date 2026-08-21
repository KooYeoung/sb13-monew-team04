package com.codeit.sb13.monew.interest.controller;

import com.codeit.sb13.monew.global.dto.CursorPageResponseDto;
import com.codeit.sb13.monew.global.exception.interest.InterestNotFoundException;
import com.codeit.sb13.monew.global.exception.interest.InterestSearchConditionInvalidException;
import com.codeit.sb13.monew.interest.controller.dto.InterestCreateRequest;
import com.codeit.sb13.monew.interest.controller.dto.InterestResponse;
import com.codeit.sb13.monew.interest.controller.dto.InterestSearchRequest;
import com.codeit.sb13.monew.interest.service.InterestService;
import com.codeit.sb13.monew.interest.service.dto.InterestCreateCommand;
import com.codeit.sb13.monew.interest.service.dto.InterestSearchCommand;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/interests")
public class InterestController {

    private final InterestService interestService;

    /**
     * 새로운 관심사를 등록한다.
     *
     * <p>이름과 키워드 목록의 형식 검증({@code @NotBlank}, {@code @Size} 등)은
     * {@link InterestCreateRequest}에서 처리되며, 여기서 실패하면 서비스 계층에
     * 도달하기 전에 {@code GLB_001}로 응답된다. 이름 중복 검사와 도메인 불변조건은
     * {@link InterestService#create}가 책임진다.</p>
     *
     * @param request 등록할 관심사의 이름과 키워드 목록
     * @return 201 상태코드와 등록된 관심사 정보
     */
    @PostMapping
    public ResponseEntity<InterestResponse> create(@Valid @RequestBody InterestCreateRequest request) {
        InterestResponse response =
                interestService.create(new InterestCreateCommand(request.name(), request.keywords()));

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * 조건에 맞는 관심사 목록을 커서 기반으로 조회한다.
     *
     * <p>{@code orderBy}, {@code direction}이 허용된 값(각각 {@code name}/{@code subscriberCount},
     * {@code ASC}/{@code DESC}) 밖이면 {@code @ModelAttribute} 바인딩 단계에서
     * {@code BindException}이 발생하고, {@code GlobalExceptionHandler}가 이를
     * {@code GLB_001}(400)로 응답한다. 두 값이 아예 생략되면 바인딩 단계에서는
     * 예외 없이 {@code null}로 채워지므로, 이 메서드에서 직접 {@code null} 여부를
     * 확인해 {@code INT_006}(400)으로 응답한다. {@code cursor}/{@code after}/{@code idAfter}는
     * 이전 응답의 {@code nextCursor}/{@code nextAfter}/{@code nextIdAfter}를 그대로 돌려보내는
     * 값으로, 첫 페이지 조회 시에는 생략한다. {@code idAfter}는 {@code cursor}와 {@code after}가
     * 모두 같은 항목이 여러 건 있을 때 순서를 확정하는 타이브레이커다.</p>
     *
     * <p>{@code Monew-Request-User-ID} 헤더로 전달된 사용자를 기준으로 각 관심사의
     * {@code subscribedByMe}를 계산한다. 이 값이 실제로 유효한 사용자인지는 이
     * 계층에서 검증하지 않는다.</p>
     *
     * @param request 검색어, 정렬 기준/방향, 커서, 조회 개수를 담은 쿼리 파라미터
     * @param requestUserId 요청자 id
     * @return 200 상태코드와 조회된 관심사 목록, 다음 페이지를 위한 커서 정보
     */
    @GetMapping
    public ResponseEntity<CursorPageResponseDto<InterestResponse>> search(
            @ModelAttribute InterestSearchRequest request,
            @RequestHeader("Monew-Request-User-ID") UUID requestUserId
    ) {
        if (request.orderBy() == null) {
            throw new InterestSearchConditionInvalidException("orderBy는 필수입니다.");
        }
        if (request.direction() == null) {
            throw new InterestSearchConditionInvalidException("direction은 필수입니다.");
        }
        if (request.limit() < 1) {
            throw new InterestSearchConditionInvalidException("limit은 1 이상이어야 합니다: " + request.limit());
        }

        CursorPageResponseDto<InterestResponse> response = interestService.search(
                new InterestSearchCommand(
                        request.keyword(),
                        request.orderBy(),
                        request.direction(),
                        request.cursor(),
                        request.after(),
                        request.idAfter(),
                        request.limit(),
                        requestUserId
                )
        );

        return ResponseEntity.ok(response);
    }

    /**
     * 관심사를 물리적으로 삭제한다.
     *
     * <p>키워드와 구독 정보도 함께 삭제되며, 삭제된 데이터는 복구할 수 없다. 존재하지 않는
     * {@code interestId}면 {@link InterestNotFoundException}이 발생해 {@code INT_001}(404)로
     * 응답한다.</p>
     *
     * @param interestId 삭제할 관심사 id
     * @return 204 상태코드
     */
    @DeleteMapping("/{interestId}")
    public ResponseEntity<Void> delete(@PathVariable UUID interestId) {
        interestService.delete(interestId);
        return ResponseEntity.noContent().build();
    }
}
