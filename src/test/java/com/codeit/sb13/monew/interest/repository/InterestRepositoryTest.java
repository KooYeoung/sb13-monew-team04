package com.codeit.sb13.monew.interest.repository;

import com.codeit.sb13.monew.global.config.QueryDslConfig;
import com.codeit.sb13.monew.global.exception.interest.InterestKeywordRequiredException;
import com.codeit.sb13.monew.interest.domain.Interest;
import com.codeit.sb13.monew.interest.domain.Keyword;
import com.codeit.sb13.monew.interest.domain.Subscribe;
import com.codeit.sb13.monew.interest.service.InterestServiceImpl;
import com.codeit.sb13.monew.user.domain.User;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.*;

/**
 * {@code InterestRepository}가 {@link com.codeit.sb13.monew.interest.repository.InterestRepositoryCustom}을
 * 상속하면서, 이 리포지토리를 주입받는 모든 {@code @DataJpaTest}는 {@code InterestRepositoryCustomImpl}이
 * 요구하는 {@code JPAQueryFactory} 빈도 함께 필요해졌다. {@code @DataJpaTest}는 일반
 * {@code @Configuration}을 자동으로 스캔하지 않으므로 {@link QueryDslConfig}를 명시적으로 임포트한다.
 */
@DataJpaTest
@Import(QueryDslConfig.class)
@ActiveProfiles("test")
class InterestRepositoryTest {

    @Autowired
    private InterestRepository interestRepository;

    @Autowired
    private SubscribeRepository subscribeRepository;

    @Autowired
    private TestEntityManager em;

    private UUID persistUser() {
        User user = User.builder()
                .email(UUID.randomUUID() + "@test.com")
                .nickname("테스터")
                .password("password")
                .build();
        em.persist(user);
        return user.getId();
    }

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

    @Test
    @DisplayName("이름이 같은 관심사를 두 번 저장하면 실제로 유니크 제약 위반 예외가 발생한다")
    void save_duplicateName_violatesUniqueConstraint() {
        interestRepository.saveAndFlush(Interest.create("스포츠"));

        DataIntegrityViolationException e = catchThrowableOfType(
                () -> interestRepository.saveAndFlush(Interest.create("스포츠")),
                DataIntegrityViolationException.class
        );

        System.out.println("실제 H2 메시지: " + e.getMostSpecificCause().getMessage());
        assertThat(e.getMostSpecificCause().getMessage()).containsIgnoringCase("uk_interests_name");
    }

    @Test
    @DisplayName("구독과 키워드가 있는 관심사를 삭제하면 FK 제약 위반 없이 구독·키워드까지 모두 지워진다")
    void delete_interestWithKeywordsAndSubscriptions_deletesEverythingWithoutConstraintViolation() {
        // given: InterestServiceImpl#delete가 실제로 하는 것과 동일한 절차(구독을 먼저 지운
        // 뒤 관심사를 지우는 것)를 실제 DB 제약 위에서 검증하기 위해, 목이 아니라 이
        // @DataJpaTest가 제공하는 실제 리포지토리들로 InterestServiceImpl을 직접 구성한다.
        UUID userId = persistUser();

        Interest interest = Interest.create("스포츠");
        interest.addKeyword("축구");
        interestRepository.save(interest);
        em.persist(Subscribe.of(interest, userId));
        em.flush();
        em.clear();

        InterestServiceImpl interestService = new InterestServiceImpl(interestRepository, subscribeRepository);

        // when
        interestService.delete(interest.getId());
        em.flush();
        em.clear();

        // then
        assertThat(interestRepository.findById(interest.getId())).isEmpty();
    }
}
