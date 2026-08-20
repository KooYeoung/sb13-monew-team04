package com.codeit.sb13.monew.article.repository;

import com.codeit.sb13.monew.article.domain.Article;
import com.codeit.sb13.monew.article.domain.ArticleView;
import com.codeit.sb13.monew.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;


@Repository
public interface ArticleViewRepository extends JpaRepository<ArticleView, UUID> {

    // 특정 기사와 사용자의 조회 기록 조회
    Optional<ArticleView> findByArticleAndUser(Article article, User user);

    // 특정 기사와 사용자의 조회 기록 존재 여부 확인
    boolean existsByArticleAndUser(Article article, User user);

    // 특정 기사의 조회 기록 목록 조회 (최신순)
    List<ArticleView> findByArticleOrderByViewedAtDesc(Article article);

    // 특정 사용자의 조회 기록 목록 조회 (최신순)
    List<ArticleView> findByUserOrderByViewedAtDesc(User user);

    // 특정 기사의 조회수 집계
    long countByArticle(Article article);

    // 특정 사용자의 전체 조회 기록 건수
    long countByUser(User user);
}