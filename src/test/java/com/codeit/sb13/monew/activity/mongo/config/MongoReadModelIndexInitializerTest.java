package com.codeit.sb13.monew.activity.mongo.config;

import static com.codeit.sb13.monew.activity.mongo.MongoReadModelCollections.ACTIVITY_HISTORIES;
import static com.codeit.sb13.monew.activity.mongo.MongoReadModelCollections.ARTICLE_SNAPSHOTS;
import static com.codeit.sb13.monew.activity.mongo.MongoReadModelCollections.COMMENT_SNAPSHOTS;
import static com.codeit.sb13.monew.activity.mongo.MongoReadModelCollections.INTEREST_SNAPSHOTS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexDefinition;
import org.springframework.data.mongodb.core.index.IndexOperations;

class MongoReadModelIndexInitializerTest {

    @Test
    void readModelIndexesMatchDocumentedCollectionsAndNaturalKey() {
        Map<String, List<IndexDefinition>> definitions = MongoReadModelIndexInitializer.indexDefinitions();

        assertThat(definitions).containsOnlyKeys(
                ACTIVITY_HISTORIES,
                COMMENT_SNAPSHOTS,
                ARTICLE_SNAPSHOTS,
                INTEREST_SNAPSHOTS
        );
        assertThat(definitions.get(ACTIVITY_HISTORIES)).hasSize(6);

        IndexDefinition naturalKey = definitions.get(ACTIVITY_HISTORIES).get(0);
        assertThat(naturalKey.getIndexKeys()).isEqualTo(new Document()
                .append("userId", 1)
                .append("type", 1)
                .append("targetType", 1)
                .append("targetId", 1));
        assertThat(naturalKey.getIndexOptions().getBoolean("unique")).isTrue();
    }

    @Test
    void initializationCanBeDisabledWithoutAccessingMongoDb() throws Exception {
        MongoTemplate mongoTemplate = mock(MongoTemplate.class);
        MongoReadModelIndexInitializer initializer = new MongoReadModelIndexInitializer(
                mongoTemplate,
                new MongoReadModelProperties(true, false)
        );

        initializer.run(null);

        verify(mongoTemplate, never()).indexOps(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void initializationEnsuresEveryDeclaredIndex() throws Exception {
        MongoTemplate mongoTemplate = mock(MongoTemplate.class);
        IndexOperations indexOperations = mock(IndexOperations.class);
        when(mongoTemplate.indexOps(org.mockito.ArgumentMatchers.anyString())).thenReturn(indexOperations);
        MongoReadModelIndexInitializer initializer = new MongoReadModelIndexInitializer(
                mongoTemplate,
                new MongoReadModelProperties(true, true)
        );

        initializer.run(null);

        int expectedIndexCount = MongoReadModelIndexInitializer.indexDefinitions().values().stream()
                .mapToInt(List::size)
                .sum();
        verify(indexOperations, org.mockito.Mockito.times(expectedIndexCount))
                .createIndex(org.mockito.ArgumentMatchers.any(IndexDefinition.class));
    }
}
