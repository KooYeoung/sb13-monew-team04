package com.codeit.sb13.monew.comment.repository;

import static com.codeit.sb13.monew.comment.domain.QComment.comment;
import static com.codeit.sb13.monew.comment.domain.QCommentLike.commentLike;

import com.codeit.sb13.monew.comment.repository.dto.CommentSearchCondition;
import com.codeit.sb13.monew.comment.repository.dto.CommentSearchProjection;
import com.codeit.sb13.monew.comment.repository.dto.CommentSearchResult;
import com.codeit.sb13.monew.comment.service.CommentOrderBy;
import com.querydsl.core.Tuple;
import com.codeit.sb13.monew.global.exception.comment.CommentSearchConditionInvalidException;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.util.StringUtils;

@RequiredArgsConstructor
public class CommentRepositoryCustomImpl implements CommentRepositoryCustom {

  private final JPAQueryFactory queryFactory;

  @Override
  public CommentSearchResult search(CommentSearchCondition condition) {

    UUID articleId = condition.articleId();
    int limit = condition.limit();
    BooleanExpression articleCondition = articleId == null ? null : comment.article.id.eq(articleId); // Swagger API의 articleId는 required!=true

    NumberExpression<Long> likeCount = likeCountExpression();
    BooleanExpression likedByMe = likedByMeExpression(condition.requestUserId());
    CommentCursor cursor = findCursor(condition, likeCount);
    BooleanExpression keysetCondition = keysetCondition(condition, likeCount, cursor);

    // 다음 페이지의 존재 여부(hasNext)를 별도의 쿼리 없이 판단하기 위해 요청한 limit+1 건을 조회한다
    List<CommentSearchProjection> rows = queryFactory
        .select(Projections.constructor(
            CommentSearchProjection.class, comment.id, comment.article.id, comment.user.id, comment.user.nickname, comment.content, likeCount.longValue(), likedByMe, comment.createdAt))
        .from(comment)
        .where(
            articleCondition,
            comment.deletedAt.isNull(),
            comment.user.deletedAt.isNull(),
            comment.article.deletedAt.isNull(),
            keysetCondition)
        .orderBy(orderSpecifiers(condition.orderBy(), condition.direction(), likeCount))
        .limit(limit + 1L)
        .fetch();

    boolean hasNext = rows.size() > limit;
    List<CommentSearchProjection> pageRows = hasNext ? rows.subList(0, limit) : rows;

    Long totalElements = queryFactory
        .select(comment.count())
        .from(comment)
        .where(articleCondition,
            comment.deletedAt.isNull(),
            comment.user.deletedAt.isNull(),
            comment.article.deletedAt.isNull())
        .fetchOne();

    return new CommentSearchResult(
        pageRows,
        hasNext,
        Objects.requireNonNullElse(totalElements, 0L)
    );
  }

  // 요청 사용자가 댓글에 좋아요 했는지 계산
  private BooleanExpression likedByMeExpression(UUID requestUserId) {
    if (requestUserId == null) {
      return Expressions.asBoolean(false);
    }
    return JPAExpressions.selectOne()
        .from(commentLike)
        .where(commentLike.comment.eq(comment),
            commentLike.likedBy.id.eq(requestUserId),
            commentLike.likedBy.deletedAt.isNull())
        .exists();
  }

  // 댓글 행이 증가할 수 있는 CommentLike 조인 대신 상관 서브쿼리로 좋아요 수 계산
  private NumberExpression<Long> likeCountExpression() {
    return Expressions.asNumber(
        JPAExpressions.select(commentLike.count())
            .from(commentLike)
            .where(commentLike.comment.eq(comment),
                commentLike.likedBy.deletedAt.isNull())
    );
  }

  // Swagger API에 따르면 cursor는 댓글 ID, after는 보조 커서
  // cursor가 있으면 서버가 anchor 댓글의 정렬 값을 직접 조회해 신뢰할 수 있는 keyset 기준을 만든다
  private CommentCursor findCursor(
      CommentSearchCondition condition,
      NumberExpression<Long> likeCount
  ) {
    boolean hasCursor = StringUtils.hasText(condition.cursor());
    if (!hasCursor) {
      return null;
    }

    UUID cursorId;
    try {
      cursorId = UUID.fromString(condition.cursor());
    } catch (IllegalArgumentException e) {
      throw new CommentSearchConditionInvalidException("댓글 ID 커서 값이 올바르지 않습니다: " + condition.cursor());
    }

    Tuple anchor = queryFactory
        .select(comment.createdAt, likeCount)
        .from(comment)
        .where(
            comment.id.eq(cursorId),
            condition.articleId() == null ? null : comment.article.id.eq(condition.articleId()),
            comment.deletedAt.isNull(),
            comment.user.deletedAt.isNull(),
            comment.article.deletedAt.isNull())
        .fetchOne();

    if (anchor == null) {
      throw new CommentSearchConditionInvalidException("현재 조회 조건에 맞는 댓글 커서가 아닙니다.");
    }

    return new CommentCursor(
        cursorId,
        anchor.get(comment.createdAt),
        Objects.requireNonNullElse(anchor.get(likeCount), 0L));
  }

  // 좋아요 수, 생성 시각까지 같은 동일한 정렬값을 가진 댓글 사이 중복 및 누락을 막기 위해서 id를 최종 타이브레이커로 사용
  // orderBy=createdAt: createdAt -> id
  // orderBy=likeCount: likeCount -> createdAt -> id
  private BooleanExpression keysetCondition(
      CommentSearchCondition condition,
      NumberExpression<Long> likeCount,
      CommentCursor cursor
  ) {
    if (cursor == null) {
      return null;
    }

    if (condition.orderBy() == CommentOrderBy.CREATED_AT) {
      BooleanExpression sameCreatedAt = comment.createdAt.eq(cursor.createdAt());
      return condition.direction().isAscending()
          ? comment.createdAt.gt(cursor.createdAt())
              .or(sameCreatedAt.and(comment.id.gt(cursor.id())))
          : comment.createdAt.lt(cursor.createdAt())
              .or(sameCreatedAt.and(comment.id.lt(cursor.id())));
    }

    BooleanExpression sameLikeCount = likeCount.eq(cursor.likeCount());
    BooleanExpression sameCreatedAt = comment.createdAt.eq(cursor.createdAt());

    return condition.direction().isAscending()
        ? likeCount.gt(cursor.likeCount())
            .or(sameLikeCount.and(comment.createdAt.gt(cursor.createdAt())))
            .or(sameLikeCount.and(sameCreatedAt).and(comment.id.gt(cursor.id())))
        : likeCount.lt(cursor.likeCount())
            .or(sameLikeCount.and(comment.createdAt.lt(cursor.createdAt())))
            .or(sameLikeCount.and(sameCreatedAt).and(comment.id.lt(cursor.id())));
  }

  private record CommentCursor(UUID id, LocalDateTime createdAt, long likeCount) {
  }

  // 페이지 간 중복/누락을 방지하기 위해 정렬 순서와 keyset 비교 순서는 일치해야 한다
  private OrderSpecifier<?>[] orderSpecifiers(CommentOrderBy orderBy, Sort.Direction direction, NumberExpression<Long> likeCount) {
    boolean ascending = direction.isAscending();

    OrderSpecifier<?> createdAt = ascending ? comment.createdAt.asc() : comment.createdAt.desc();
    OrderSpecifier<?> id = ascending ? comment.id.asc() : comment.id.desc();
    if (orderBy == CommentOrderBy.CREATED_AT) {
      return new OrderSpecifier<?>[] {createdAt, id};
    }

    OrderSpecifier<?> primary = ascending ? likeCount.asc() : likeCount.desc();
    return new OrderSpecifier<?>[] {primary, createdAt, id};
  }
}
