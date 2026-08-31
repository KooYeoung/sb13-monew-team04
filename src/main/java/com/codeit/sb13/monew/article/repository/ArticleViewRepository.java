package com.codeit.sb13.monew.article.repository;

import com.codeit.sb13.monew.article.domain.Article;
import com.codeit.sb13.monew.article.domain.ArticleView;
import com.codeit.sb13.monew.article.repository.dto.RecentArticleViewActivityProjection;
import com.codeit.sb13.monew.user.domain.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;


@Repository
public interface ArticleViewRepository extends JpaRepository<ArticleView, UUID> {

    // 특정 기사와 사용자의 조회 기록 조회
    @EntityGraph(attributePaths = {"article", "user"})
    Optional<ArticleView> findByArticleAndUser(Article article, User user);

    // 요청자의 조회 여부 (viewedByMe)
    boolean existsByArticle_IdAndUser_Id(UUID articleId, UUID userId);

    // 기사 조회수 집계 (활성 조회 기록만 포함)
    @Query("""
        SELECT COUNT(at)
        FROM ArticleView at
        WHERE at.article.id = :articleId
           AND at.visibilityStatus = 'ACTIVE'
    """)
    long countActiveByArticleId(@Param("articleId") UUID articleId);


    void deleteByUser_Id(UUID userId);

    // 기사 물리 삭제 시 조회 기록 정리 (MID4-146)
    void deleteByArticle_Id(UUID articleId);

    @Query("""
                SELECT new com.codeit.sb13.monew.article.repository.dto.RecentArticleViewActivityProjection(
                    at.id,
                    at.user.id,
                    at.viewedAt,
                    a.id,
                    a.source,
                    a.link,
                    a.title,
                    a.date,
                    a.summary,
                    (SELECT COUNT(c)
                     FROM Comment c
                     WHERE c.article.id = a.id
                       AND c.visibilityStatus = 'ACTIVE'),
                    (SELECT COUNT(at2)
                     FROM ArticleView at2
                     WHERE at2.article.id = a.id
                       AND at2.visibilityStatus = 'ACTIVE')
                    )
                FROM ArticleView at
                JOIN at.article a
                WHERE at.user.id = :userId
                AND at.visibilityStatus = 'ACTIVE'
                ORDER BY at.viewedAt DESC, at.id DESC
                LIMIT 10
            """)
    List<RecentArticleViewActivityProjection> findRecentArticleViewActivities(@Param("userId") UUID userId);

}
