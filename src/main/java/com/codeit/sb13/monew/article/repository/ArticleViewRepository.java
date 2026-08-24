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

    // 특정 기사의 조회 기록 목록 조회 (최신순)
    List<ArticleView> findByArticleOrderByViewedAtDesc(Article article);

    // 특정 사용자의 조회 기록 목록 조회 (최신순)
    List<ArticleView> findByUserOrderByViewedAtDesc(User user);

    // 특정 기사의 조회수 집계
    long countByArticleAndUser_DeletedAtIsNull(Article article);

    // 요청자의 조회 여부 (viewedByMe)
    boolean existsByArticle_IdAndUser_Id(UUID articleId, UUID userId);

    // 기사 조회수 집계 (탈퇴 사용자 조회 이력 제외)
    long countByArticle_IdAndUser_DeletedAtIsNull(UUID articleId);


    void deleteByUser_Id(UUID userId);

    @Query("""
                SELECT new com.codeit.sb13.monew.article.repository.dto.RecentArticleViewActivityProjection(
                    AT.id,
                    U.id,
                    AT.viewedAt,
                    A.id,
                    A.source,
                    A.link,
                    A.title,
                    A.date,
                    A.summary,
                    (SELECT COUNT(C)
                     FROM Comment C
                     JOIN C.user U2
                     WHERE C.article.id = A.id
                          AND C.deletedAt IS NULL
                          AND U2.deletedAt IS NULL),
                    (SELECT COUNT(AT2)
                     FROM ArticleView AT2
                     JOIN AT2.user U2
                     WHERE AT2.article.id = A.id
                           AND U2.deletedAt IS NULL)
                    )
                FROM ArticleView AT
                JOIN AT.article A
                JOIN AT.user U
                WHERE U.id = :userId
                AND A.deletedAt IS NULL
                AND U.deletedAt IS NULL
                ORDER BY AT.viewedAt DESC, AT.id DESC
                LIMIT 10
            """)
    List<RecentArticleViewActivityProjection> findRecentArticleViewActivities(@Param("userId") UUID userId);

}