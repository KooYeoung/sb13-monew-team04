package com.codeit.sb13.monew.activity.service;

import static com.codeit.sb13.monew.comment.domain.QComment.comment;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ActivityVisibilityUpdaterTest {

    @Mock
    private EntityManager entityManager;

    @Mock
    private JPAQueryFactory queryFactory;

    @Test
    @DisplayName("articleViewTargetCondition은 COMMENT 시 IllegalStateException을 던진다")
    void articleViewTargetConditionThrowsForCommentCause() {
        ActivityVisibilityUpdater updater = new ActivityVisibilityUpdater(entityManager, queryFactory);
        ActivityDeletionTarget target = ActivityDeletionTarget.deletedComment(UUID.randomUUID());

        assertThatThrownBy(() -> updater.articleViewTargetCondition(target))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("commentTargetCondition은 COMMENT 시 댓글 id 조건을 반환한다")
    void commentTargetConditionReturnsCommentIdConditionForCommentCause() {
        ActivityVisibilityUpdater updater = new ActivityVisibilityUpdater(entityManager, queryFactory);
        UUID commentId = UUID.randomUUID();
        ActivityDeletionTarget target = ActivityDeletionTarget.deletedComment(commentId);

        BooleanExpression condition = updater.commentTargetCondition(target);

        assertThat(condition).isEqualTo(comment.id.eq(commentId));
    }
}
