package com.codeit.sb13.monew.activity.outbox.worker.source;

import static com.codeit.sb13.monew.article.domain.QArticle.article;
import static com.codeit.sb13.monew.article.domain.QArticleView.articleView;
import static com.codeit.sb13.monew.comment.domain.QComment.comment;
import static com.codeit.sb13.monew.comment.domain.QCommentLike.commentLike;
import static com.codeit.sb13.monew.global.domain.ActivityVisibilityStatus.ACTIVE;
import static com.codeit.sb13.monew.interest.domain.QInterest.interest;
import static com.codeit.sb13.monew.interest.domain.QSubscribe.subscribe;
import static com.codeit.sb13.monew.user.domain.QUser.user;

import com.codeit.sb13.monew.activity.outbox.payload.CommentLikeOutboxPayload;
import com.codeit.sb13.monew.activity.outbox.payload.CommentOutboxPayload;
import com.codeit.sb13.monew.activity.outbox.payload.UserHardDeleteOutboxPayload;
import com.codeit.sb13.monew.activity.outbox.payload.ArticleOutboxPayload;
import com.codeit.sb13.monew.activity.outbox.payload.InterestOutboxPayload;
import com.codeit.sb13.monew.activity.outbox.payload.OutboxEventPayload;
import com.codeit.sb13.monew.activity.outbox.payload.ProjectionImpact;
import com.codeit.sb13.monew.activity.outbox.payload.UserOutboxPayload;
import com.codeit.sb13.monew.activity.outbox.worker.DecodedOutboxEvent;
import com.codeit.sb13.monew.activity.outbox.worker.source.ProjectionSourceBatch.ArticleState;
import com.codeit.sb13.monew.activity.outbox.worker.source.ProjectionSourceBatch.CommentState;
import com.codeit.sb13.monew.activity.outbox.worker.source.ProjectionSourceBatch.InterestState;
import com.codeit.sb13.monew.activity.outbox.worker.source.ProjectionSourceBatch.RelationKey;
import com.codeit.sb13.monew.activity.outbox.worker.source.ProjectionSourceBatch.RelationState;
import com.codeit.sb13.monew.activity.outbox.worker.source.ProjectionSourceBatch.UserState;
import com.codeit.sb13.monew.article.domain.ArticleSource;
import com.codeit.sb13.monew.article.domain.QArticle;
import com.codeit.sb13.monew.global.domain.ActivityVisibilityStatus;
import com.codeit.sb13.monew.interest.domain.QKeyword;
import com.codeit.sb13.monew.user.domain.QUser;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * claim batch에 필요한 원본 엔티티와 관계의 현재 상태를 RDB에서 묶어 조회한다.
 *
 * <p>이벤트 envelope와 payload에서 사용자·관심사·댓글·기사 ID 집합을 먼저
 * 수집한 뒤 도메인별 batch query를 실행한다. projection handler는 이 결과를
 * 사용하므로 payload에 들어 있던 과거 표시값에 의존하지 않는다.</p>
 */
@Component
@RequiredArgsConstructor
public class OutboxProjectionSourceReader {

    private final JPAQueryFactory queryFactory;

    /**
     * 여러 이벤트가 공유하는 원본과 관계 상태를 읽기 전용 트랜잭션에서 조회한다.
     *
     * @param events 같은 claim batch에서 decode된 이벤트 목록
     * @return 대상 ID로 즉시 조회할 수 있는 불변 source batch
     */
    @Transactional(readOnly = true)
    public ProjectionSourceBatch read(List<DecodedOutboxEvent> events) {
        SourceIds ids = collectIds(events);
        return new ProjectionSourceBatch(
                readUsers(ids.userIds()),
                readInterests(ids.interestIds()),
                readComments(ids.commentIds()),
                readArticles(ids.articleIds()),
                readSubscriptions(ids.interestIds(), ids.userIds()),
                readCommentLikes(ids.commentIds(), ids.userIds()),
                readArticleViews(ids.articleIds(), ids.userIds())
        );
    }

    private SourceIds collectIds(List<DecodedOutboxEvent> events) {
        Set<UUID> userIds = new LinkedHashSet<>();
        Set<UUID> interestIds = new LinkedHashSet<>();
        Set<UUID> commentIds = new LinkedHashSet<>();
        Set<UUID> articleIds = new LinkedHashSet<>();

        for (DecodedOutboxEvent event : events) {
            if (event.actorUserId() != null) {
                userIds.add(event.actorUserId());
            }
            switch (event.aggregateType()) {
                case USER -> userIds.add(event.aggregateId());
                case INTEREST -> interestIds.add(event.aggregateId());
                case COMMENT -> commentIds.add(event.aggregateId());
                case ARTICLE -> articleIds.add(event.aggregateId());
            }

            if (event.payload() instanceof CommentOutboxPayload payload) {
                articleIds.add(payload.articleId());
            } else if (event.payload() instanceof CommentLikeOutboxPayload payload) {
                articleIds.add(payload.articleId());
            } else if (event.payload() instanceof UserHardDeleteOutboxPayload payload) {
                commentIds.addAll(payload.authoredCommentIds());
                commentIds.addAll(payload.likedCommentIds());
                articleIds.addAll(payload.impactedArticleIds());
                articleIds.addAll(payload.viewedArticleIds());
                interestIds.addAll(payload.subscribedInterestIds());
            }
            collectImpactIds(
                    impactOf(event.payload()), userIds, interestIds, commentIds, articleIds);
        }
        return new SourceIds(userIds, interestIds, commentIds, articleIds);
    }

    private void collectImpactIds(
            ProjectionImpact impact,
            Set<UUID> userIds,
            Set<UUID> interestIds,
            Set<UUID> commentIds,
            Set<UUID> articleIds
    ) {
        commentIds.addAll(impact.commentSnapshotIds());
        impact.activityKeys().forEach(key -> {
            userIds.add(key.userId());
            switch (key.activityEventType()) {
                case INTEREST_SUBSCRIBED -> interestIds.add(key.targetId());
                case COMMENT_WRITTEN, COMMENT_LIKED -> commentIds.add(key.targetId());
                case ARTICLE_VIEWED -> articleIds.add(key.targetId());
                default -> throw new IllegalStateException(
                        "지원하지 않는 activity key type: " + key.activityEventType());
            }
        });
    }

    private ProjectionImpact impactOf(OutboxEventPayload payload) {
        if (payload instanceof ArticleOutboxPayload value) {
            return value.impact();
        }
        if (payload instanceof CommentOutboxPayload value) {
            return value.impact();
        }
        if (payload instanceof InterestOutboxPayload value) {
            return value.impact();
        }
        if (payload instanceof UserOutboxPayload value) {
            return value.impact();
        }
        if (payload instanceof UserHardDeleteOutboxPayload value) {
            return value.impact();
        }
        return ProjectionImpact.EMPTY;
    }

    private Map<UUID, UserState> readUsers(Set<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return queryFactory
                .select(Projections.constructor(
                        UserRow.class,
                        user.id,
                        user.nickname,
                        user.deletedAt
                ))
                .from(user)
                .where(user.id.in(ids))
                .fetch()
                .stream()
                .collect(Collectors.toMap(
                        UserRow::id,
                        row -> new UserState(row.id(), row.nickname(), row.deletedAt() == null)
                ));
    }

    private Map<UUID, InterestState> readInterests(Set<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        QKeyword projectionKeyword = new QKeyword("projectionKeyword");
        List<InterestKeywordRow> rows = queryFactory
                .select(Projections.constructor(
                        InterestKeywordRow.class,
                        interest.id,
                        interest.name,
                        interest.updatedAt,
                        projectionKeyword.keyword
                ))
                .from(interest)
                .leftJoin(interest.keywords, projectionKeyword)
                .where(interest.id.in(ids))
                .orderBy(interest.id.asc(), projectionKeyword.keyword.asc())
                .fetch();
        Map<UUID, Long> counts = readInterestSubscriberCounts(ids);
        return rows.stream()
                .collect(Collectors.groupingBy(
                        InterestKeywordRow::id,
                        LinkedHashMap::new,
                        Collectors.collectingAndThen(
                                Collectors.toList(),
                                group -> toInterestState(group, counts)
                        )
                ));
    }

    private Map<UUID, CommentState> readComments(Set<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        QArticle commentArticle = new QArticle("projectionCommentArticle");
        QUser commentAuthor = new QUser("projectionCommentAuthor");
        Map<UUID, Long> counts = readCommentLikeCounts(ids);
        return queryFactory
                .select(Projections.constructor(
                        CommentRow.class,
                        comment.id,
                        commentArticle.id,
                        commentArticle.title,
                        commentArticle.deletedAt,
                        commentAuthor.id,
                        commentAuthor.nickname,
                        commentAuthor.deletedAt,
                        comment.content,
                        comment.visibilityStatus,
                        comment.deletedAt,
                        comment.createdAt,
                        comment.updatedAt
                ))
                .from(comment)
                .join(comment.article, commentArticle)
                .join(comment.user, commentAuthor)
                .where(comment.id.in(ids))
                .fetch()
                .stream()
                .collect(Collectors.toMap(
                        CommentRow::id,
                        row -> toCommentState(row, counts)
                ));
    }

    private Map<UUID, ArticleState> readArticles(Set<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Long> viewCounts = readArticleViewCounts(ids);
        Map<UUID, Long> commentCounts = readArticleCommentCounts(ids);
        return queryFactory
                .select(Projections.constructor(
                        ArticleRow.class,
                        article.id,
                        article.title,
                        article.summary,
                        article.source,
                        article.link,
                        article.date,
                        article.deletedAt,
                        article.updatedAt
                ))
                .from(article)
                .where(article.id.in(ids))
                .fetch()
                .stream()
                .collect(Collectors.toMap(
                        ArticleRow::id,
                        row -> toArticleState(row, viewCounts, commentCounts)
                ));
    }

    private Map<RelationKey, RelationState> readSubscriptions(
            Set<UUID> interestIds,
            Set<UUID> userIds
    ) {
        if (interestIds.isEmpty() || userIds.isEmpty()) {
            return Map.of();
        }
        return queryFactory
                .select(Projections.constructor(
                        RelationRow.class,
                        subscribe.id,
                        subscribe.interest.id,
                        subscribe.userId,
                        subscribe.visibilityStatus,
                        subscribe.createdAt
                ))
                .from(subscribe)
                .where(subscribe.interest.id.in(interestIds), subscribe.userId.in(userIds))
                .fetch()
                .stream()
                .collect(Collectors.toMap(
                        this::toRelationKey,
                        this::toRelationState
                ));
    }

    private Map<RelationKey, RelationState> readCommentLikes(
            Set<UUID> commentIds,
            Set<UUID> userIds
    ) {
        if (commentIds.isEmpty() || userIds.isEmpty()) {
            return Map.of();
        }
        return queryFactory
                .select(Projections.constructor(
                        RelationRow.class,
                        commentLike.id,
                        commentLike.comment.id,
                        commentLike.likedBy.id,
                        commentLike.visibilityStatus,
                        commentLike.createdAt
                ))
                .from(commentLike)
                .where(commentLike.comment.id.in(commentIds), commentLike.likedBy.id.in(userIds))
                .fetch()
                .stream()
                .collect(Collectors.toMap(
                        this::toRelationKey,
                        this::toRelationState
                ));
    }

    private Map<RelationKey, RelationState> readArticleViews(
            Set<UUID> articleIds,
            Set<UUID> userIds
    ) {
        if (articleIds.isEmpty() || userIds.isEmpty()) {
            return Map.of();
        }
        return queryFactory
                .select(Projections.constructor(
                        RelationRow.class,
                        articleView.id,
                        articleView.article.id,
                        articleView.user.id,
                        articleView.visibilityStatus,
                        articleView.viewedAt
                ))
                .from(articleView)
                .where(articleView.article.id.in(articleIds), articleView.user.id.in(userIds))
                .fetch()
                .stream()
                .collect(Collectors.toMap(
                        this::toRelationKey,
                        this::toRelationState
                ));
    }

    private Map<UUID, Long> readInterestSubscriberCounts(Set<UUID> ids) {
        NumberExpression<Long> count = subscribe.count();
        return countByTarget(queryFactory
                .select(Projections.constructor(
                        CountRow.class,
                        subscribe.interest.id,
                        count
                ))
                .from(subscribe)
                .where(subscribe.interest.id.in(ids), subscribe.visibilityStatus.eq(ACTIVE))
                .groupBy(subscribe.interest.id)
                .fetch());
    }

    private Map<UUID, Long> readCommentLikeCounts(Set<UUID> ids) {
        NumberExpression<Long> count = commentLike.count();
        return countByTarget(queryFactory
                .select(Projections.constructor(
                        CountRow.class,
                        commentLike.comment.id,
                        count
                ))
                .from(commentLike)
                .where(commentLike.comment.id.in(ids), commentLike.visibilityStatus.eq(ACTIVE))
                .groupBy(commentLike.comment.id)
                .fetch());
    }

    private Map<UUID, Long> readArticleViewCounts(Set<UUID> ids) {
        NumberExpression<Long> count = articleView.count();
        return countByTarget(queryFactory
                .select(Projections.constructor(
                        CountRow.class,
                        articleView.article.id,
                        count
                ))
                .from(articleView)
                .where(articleView.article.id.in(ids), articleView.visibilityStatus.eq(ACTIVE))
                .groupBy(articleView.article.id)
                .fetch());
    }

    private Map<UUID, Long> readArticleCommentCounts(Set<UUID> ids) {
        NumberExpression<Long> count = comment.count();
        return countByTarget(queryFactory
                .select(Projections.constructor(
                        CountRow.class,
                        comment.article.id,
                        count
                ))
                .from(comment)
                .where(comment.article.id.in(ids), comment.visibilityStatus.eq(ACTIVE))
                .groupBy(comment.article.id)
                .fetch());
    }

    private Map<UUID, Long> countByTarget(List<CountRow> rows) {
        return rows.stream()
                .collect(Collectors.toMap(CountRow::targetId, CountRow::count));
    }

    private InterestState toInterestState(
            List<InterestKeywordRow> rows,
            Map<UUID, Long> counts
    ) {
        InterestKeywordRow first = rows.get(0);
        List<String> keywords = rows.stream()
                .map(InterestKeywordRow::keyword)
                .filter(keyword -> keyword != null)
                .toList();
        return new InterestState(
                first.id(),
                first.name(),
                keywords,
                counts.getOrDefault(first.id(), 0L),
                first.updatedAt()
        );
    }

    private CommentState toCommentState(CommentRow row, Map<UUID, Long> counts) {
        boolean visible = row.visibilityStatus() == ACTIVE
                && row.deletedAt() == null
                && row.articleDeletedAt() == null
                && row.authorDeletedAt() == null;
        return new CommentState(
                row.id(),
                row.articleId(),
                row.articleTitle(),
                row.authorUserId(),
                row.authorNickname(),
                row.content(),
                counts.getOrDefault(row.id(), 0L),
                visible,
                row.createdAt(),
                row.updatedAt()
        );
    }

    private ArticleState toArticleState(
            ArticleRow row,
            Map<UUID, Long> viewCounts,
            Map<UUID, Long> commentCounts
    ) {
        return new ArticleState(
                row.id(),
                row.title(),
                row.summary(),
                row.source(),
                row.sourceUrl(),
                row.publishedAt(),
                viewCounts.getOrDefault(row.id(), 0L),
                commentCounts.getOrDefault(row.id(), 0L),
                row.deletedAt() == null,
                row.updatedAt()
        );
    }

    private RelationKey toRelationKey(RelationRow row) {
        return new RelationKey(row.targetId(), row.userId());
    }

    private RelationState toRelationState(RelationRow row) {
        return new RelationState(
                row.id(),
                row.targetId(),
                row.userId(),
                row.visibilityStatus() == ACTIVE,
                row.occurredAt()
        );
    }

    /**
     * 사용자 현재 상태 조회 결과다.
     *
     * <p>QueryDSL constructor projection이 public 생성자만 지원하므로 public 중첩 record로
     * 선언하지만, {@link OutboxProjectionSourceReader} 내부 조립에만 사용하는 조회 모델이다.</p>
     *
     * @param id 사용자 식별자
     * @param nickname 현재 닉네임
     * @param deletedAt 논리삭제 시각
     */
    public record UserRow(UUID id, String nickname, LocalDateTime deletedAt) {
    }

    /**
     * 관심사와 키워드를 left join한 행 하나다.
     *
     * @param id 관심사 식별자
     * @param name 관심사 이름
     * @param updatedAt 관심사 수정 시각
     * @param keyword 키워드. 키워드가 없는 관심사는 {@code null}
     */
    public record InterestKeywordRow(
            UUID id,
            String name,
            LocalDateTime updatedAt,
            String keyword
    ) {
    }

    /**
     * 댓글 snapshot을 구성하는 데 필요한 댓글·기사·작성자 조회 결과다.
     *
     * @param id 댓글 식별자
     * @param articleId 부모 기사 식별자
     * @param articleTitle 부모 기사 제목
     * @param articleDeletedAt 부모 기사 논리삭제 시각
     * @param authorUserId 작성자 식별자
     * @param authorNickname 작성자 닉네임
     * @param authorDeletedAt 작성자 논리삭제 시각
     * @param content 댓글 내용
     * @param visibilityStatus 댓글 가시성 상태
     * @param deletedAt 댓글 논리삭제 시각
     * @param createdAt 댓글 생성 시각
     * @param updatedAt 댓글 수정 시각
     */
    public record CommentRow(
            UUID id,
            UUID articleId,
            String articleTitle,
            LocalDateTime articleDeletedAt,
            UUID authorUserId,
            String authorNickname,
            LocalDateTime authorDeletedAt,
            String content,
            ActivityVisibilityStatus visibilityStatus,
            LocalDateTime deletedAt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }

    /**
     * 기사 snapshot을 구성하는 데 필요한 기사 조회 결과다.
     *
     * @param id 기사 식별자
     * @param title 기사 제목
     * @param summary 기사 요약
     * @param source 기사 출처
     * @param sourceUrl 원문 URL
     * @param publishedAt 게시 시각
     * @param deletedAt 논리삭제 시각
     * @param updatedAt 수정 시각
     */
    public record ArticleRow(
            UUID id,
            String title,
            String summary,
            ArticleSource source,
            String sourceUrl,
            LocalDateTime publishedAt,
            LocalDateTime deletedAt,
            LocalDateTime updatedAt
    ) {
    }

    /**
     * 구독·댓글 좋아요·기사 조회에 공통으로 사용하는 관계 조회 결과다.
     *
     * @param id 관계 식별자
     * @param targetId 관계 대상 식별자
     * @param userId 관계 사용자 식별자
     * @param visibilityStatus 관계 가시성 상태
     * @param occurredAt 관계 발생 시각
     */
    public record RelationRow(
            UUID id,
            UUID targetId,
            UUID userId,
            ActivityVisibilityStatus visibilityStatus,
            LocalDateTime occurredAt
    ) {
    }

    /**
     * 대상별 활성 관계 집계 결과다.
     *
     * @param targetId 집계 대상 식별자
     * @param count 활성 관계 수
     */
    public record CountRow(UUID targetId, Long count) {
    }

    private record SourceIds(
            Set<UUID> userIds,
            Set<UUID> interestIds,
            Set<UUID> commentIds,
            Set<UUID> articleIds
    ) {
    }

}
