package com.codeit.sb13.monew.article.repository;

import com.codeit.sb13.monew.article.domain.Article;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ArticleRepository extends JpaRepository<Article, UUID>, ArticleRepositoryCustom {

    Optional<Article> findByLink(String link);

    List<Article> findAllBySourceAndDeletedAtIsNull(String source);

    List<Article> findAllByDeletedAtIsNull();

    /**
     * 활성 기사를 최신순으로 조회
     * date 내림차순으로 정렬
     * @return 활성 기사 목록 (최신순)
     */
    List<Article> findAllByDeletedAtIsNullOrderByDateDesc();

    Optional<Article> findByIdAndDeletedAtIsNull(UUID id);

    List<Article> findAllBySourceAndDeletedAtIsNullOrderByDateDesc(String source);
}