package com.codeit.sb13.monew.activity.mongo.config;

import static com.codeit.sb13.monew.activity.mongo.MongoReadModelCollections.ACTIVITY_HISTORIES;
import static com.codeit.sb13.monew.activity.mongo.MongoReadModelCollections.ARTICLE_SNAPSHOTS;
import static com.codeit.sb13.monew.activity.mongo.MongoReadModelCollections.COMMENT_SNAPSHOTS;
import static com.codeit.sb13.monew.activity.mongo.MongoReadModelCollections.INTEREST_SNAPSHOTS;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexDefinition;
import org.springframework.data.mongodb.core.index.PartialIndexFilter;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "monew.mongodb", name = "enabled", havingValue = "true")
public class MongoReadModelIndexInitializer implements ApplicationRunner {

    private final MongoTemplate mongoTemplate;
    private final MongoReadModelProperties properties;

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.initializeIndexes()) {
            return;
        }

        indexDefinitions().forEach((collection, indexes) -> indexes.forEach(
                index -> mongoTemplate.indexOps(collection).createIndex(index)
        ));
    }

    static Map<String, List<IndexDefinition>> indexDefinitions() {
        Map<String, List<IndexDefinition>> definitions = new LinkedHashMap<>();
        definitions.put(ACTIVITY_HISTORIES, List.of(
                new Index()
                        .on("userId", Direction.ASC)
                        .on("type", Direction.ASC)
                        .on("targetType", Direction.ASC)
                        .on("targetId", Direction.ASC)
                        .unique()
                        .partial(PartialIndexFilter.of(Criteria.where("tombstone").is(false)))
                        .named("ux_activity_histories_natural_key"),
                new Index()
                        .on("userId", Direction.ASC)
                        .on("type", Direction.ASC)
                        .on("visible", Direction.ASC)
                        .on("occurredAt", Direction.DESC)
                        .on("_id", Direction.DESC)
                        .named("idx_activity_histories_user_type_visible_cursor"),
                new Index()
                        .on("userId", Direction.ASC)
                        .on("visible", Direction.ASC)
                        .named("idx_activity_histories_user_visible"),
                new Index()
                        .on("targetType", Direction.ASC)
                        .on("targetId", Direction.ASC)
                        .named("idx_activity_histories_target"),
                new Index()
                        .on("targetType", Direction.ASC)
                        .on("parentTargetType", Direction.ASC)
                        .on("parentTargetId", Direction.ASC)
                        .named("idx_activity_histories_parent_target"),
                new Index()
                        .on("hiddenByTargetType", Direction.ASC)
                        .on("hiddenByTargetId", Direction.ASC)
                        .on("status", Direction.ASC)
                        .named("idx_activity_histories_hidden_by")
        ));
        definitions.put(COMMENT_SNAPSHOTS, List.of(
                new Index().on("commentId", Direction.ASC).unique()
                        .partial(PartialIndexFilter.of(Criteria.where("tombstone").is(false)))
                        .named("ux_comment_activity_snapshots_comment_id"),
                new Index().on("articleId", Direction.ASC).on("visible", Direction.ASC)
                        .named("idx_comment_activity_snapshots_article_visible")
        ));
        definitions.put(ARTICLE_SNAPSHOTS, List.of(
                new Index().on("articleId", Direction.ASC).unique()
                        .partial(PartialIndexFilter.of(Criteria.where("tombstone").is(false)))
                        .named("ux_article_activity_snapshots_article_id")
        ));
        definitions.put(INTEREST_SNAPSHOTS, List.of(
                new Index().on("interestId", Direction.ASC).unique()
                        .partial(PartialIndexFilter.of(Criteria.where("tombstone").is(false)))
                        .named("ux_interest_activity_snapshots_interest_id")
        ));
        return definitions;
    }
}
