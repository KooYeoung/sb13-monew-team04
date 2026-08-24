package com.codeit.sb13.monew.interest.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record InterestUpdateRequest(

        @NotEmpty(message = "키워드는 최소 1개 이상 등록해야 합니다.")
        List<
                @NotBlank(message = "키워드는 빈 값일 수 없습니다.")
                @Size(max = 50, message = "키워드는 50자를 넘을 수 없습니다.")
                String
        > keywords
) {
}
