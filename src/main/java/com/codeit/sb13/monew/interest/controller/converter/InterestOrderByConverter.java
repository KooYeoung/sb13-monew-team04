package com.codeit.sb13.monew.interest.controller.converter;

import com.codeit.sb13.monew.interest.service.dto.InterestOrderBy;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

/**
 * {@code @RequestParam}으로 들어오는 {@code orderBy} 문자열을
 * {@link InterestOrderBy}로 바인딩하기 위한 컨버터.
 *
 * <p>{@code @Component}로 등록해두면 스프링이 자동으로 인식해
 * {@code WebMvcConfigurer}를 따로 건드리지 않아도 된다.</p>
 */
@Component
public class InterestOrderByConverter implements Converter<String, InterestOrderBy> {

    @Override
    public InterestOrderBy convert(String source) {
        return InterestOrderBy.from(source);
    }
}
