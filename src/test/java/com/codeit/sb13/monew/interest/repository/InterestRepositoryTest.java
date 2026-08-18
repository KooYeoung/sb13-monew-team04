package com.codeit.sb13.monew.interest.repository;

import com.codeit.sb13.monew.global.exception.interest.InterestKeywordRequiredException;
import com.codeit.sb13.monew.interest.domain.Interest;
import com.codeit.sb13.monew.interest.domain.Keyword;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
class InterestRepositoryTest {

    @Autowired
    private InterestRepository interestRepository;

    @Autowired
    private TestEntityManager em;

    @Nested
    @DisplayName("removeKeyword() 이후 영속성 컨텍스트 반영")
    class RemoveKeyword {

        @Test
        @DisplayName("removeKeyword 후 flush해도 NOT NULL 제약을 위반하지 않고, 키워드 로우가 orphan 삭제된다")
        void removeKeyword_thenFlush() {
            // given
            Interest interest = Interest.create("스포츠");
            interest.addKeyword("축구");
            interest.addKeyword("야구");
            interestRepository.save(interest);
            em.flush();
            em.clear();

            Interest reloaded = interestRepository.findById(interest.getId()).orElseThrow();
            Keyword keyword = reloaded.getKeywords().stream()
                    .filter(k -> k.getKeyword().equals("축구"))
                    .findFirst()
                    .orElseThrow();

            // when
            reloaded.removeKeyword(keyword);

            // then
            assertThatCode(() -> em.flush()).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("removeKeyword 후 flush 및 재조회하면 DB에서도 키워드가 실제로 사라져 있다")
        void removeKeyword_thenFlushAndReload() {
            // given
            Interest interest = Interest.create("스포츠");
            interest.addKeyword("축구");
            interest.addKeyword("야구");
            interestRepository.save(interest);
            em.flush();
            em.clear();

            Interest reloaded = interestRepository.findById(interest.getId()).orElseThrow();
            Keyword target = reloaded.getKeywords().stream()
                    .filter(k -> k.getKeyword().equals("축구"))
                    .findFirst()
                    .orElseThrow();

            // when
            reloaded.removeKeyword(target);
            em.flush();
            em.clear();

            // then
            Interest afterReload = interestRepository.findById(interest.getId()).orElseThrow();
            assertThat(afterReload.getKeywords())
                    .hasSize(1)
                    .extracting(Keyword::getKeyword)
                    .containsExactly("야구");
        }
    }
}
