package com.codeit.sb13.monew.article.repository;

import com.codeit.sb13.monew.article.domain.ArticleView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;


@Repository
public interface ArticleViewRepository extends JpaRepository<ArticleView, UUID> {

    Optional<ArticleView> findByArticleIdAndUserId(UUID articleId, UUID userId);

    boolean existsByArticleIdAndUserId(UUID articleId, UUID userId);

    List<ArticleView> findByArticleIdOrderByViewedAtDesc(UUID articleId);

    List<ArticleView> findByUserIdOrderByViewedAtDesc(UUID userId);

    /**
     * 특정 기사의 조회수 집계
     * articleId 기사 ID
     * 조회 기록 건수
     */
    long countByArticleId(UUID articleId);

    /**
     * 특정 사용자의 전체 조회 기록 건수
     * userId 사용자 ID
     * 조회 기록 건수
     */
    long countByUserId(UUID userId);
}