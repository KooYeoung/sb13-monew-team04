package com.codeit.sb13.monew.interest.controller;

import com.codeit.sb13.monew.interest.controller.dto.InterestCreateRequest;
import com.codeit.sb13.monew.interest.controller.dto.InterestResponse;
import com.codeit.sb13.monew.interest.service.InterestService;
import com.codeit.sb13.monew.interest.service.dto.InterestCreateCommand;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
}
