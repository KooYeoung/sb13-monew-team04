package com.codeit.sb13.monew.activity.mongo.querydsl;

import com.querydsl.core.types.EntityPath;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Predicate;
import java.util.List;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.BasicQuery;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.repository.support.QuerydslRepositorySupport;
import org.springframework.data.mongodb.repository.support.SpringDataMongodbQuery;
import org.springframework.stereotype.Component;

/**
 * MongoDB Read Model의 Querydsl 조회와 predicate 변환을 한곳에서 제공한다.
 *
 * <p>조회는 {@link SpringDataMongodbQuery}가 직접 실행하고, 원자적 update/upsert처럼
 * {@code MongoTemplate}이 필요한 경로는 같은 Querydsl predicate를 Spring Data
 * {@link Query} 또는 BSON {@link Document}로 변환해 사용한다.</p>
 */
@Component
public class MongoQuerydslSupport extends QuerydslRepositorySupport {

    public MongoQuerydslSupport(MongoOperations operations) {
        super(operations);
    }

    /** 지정한 document와 collection을 대상으로 새 Querydsl query를 만든다. */
    public <T> SpringDataMongodbQuery<T> selectFrom(
            EntityPath<T> path,
            String collection
    ) {
        return from(path, collection);
    }

    /** predicate와 일치하는 document 목록을 조회한다. */
    public <T> List<T> fetch(
            EntityPath<T> path,
            String collection,
            Predicate predicate
    ) {
        return selectFrom(path, collection)
                .where(predicate)
                .fetch();
    }

    /** predicate, 정렬과 limit을 함께 적용해 document 목록을 조회한다. */
    public <T> List<T> fetch(
            EntityPath<T> path,
            String collection,
            Predicate predicate,
            long limit,
            OrderSpecifier<?>... orderSpecifiers
    ) {
        return selectFrom(path, collection)
                .where(predicate)
                .orderBy(orderSpecifiers)
                .limit(limit)
                .fetch();
    }

    /** predicate와 일치하는 document 수를 조회한다. */
    public <T> long count(
            EntityPath<T> path,
            String collection,
            Predicate predicate
    ) {
        return selectFrom(path, collection)
                .where(predicate)
                .fetchCount();
    }

    /** Querydsl predicate를 MongoTemplate update 계열에서 사용할 query로 변환한다. */
    public <T> Query toQuery(
            EntityPath<T> path,
            String collection,
            Predicate predicate
    ) {
        return new BasicQuery(toDocument(path, collection, predicate));
    }

    /** Querydsl predicate를 partial index 등에서 사용할 BSON filter로 변환한다. */
    public <T> Document toDocument(
            EntityPath<T> path,
            String collection,
            Predicate predicate
    ) {
        return selectFrom(path, collection)
                .where(predicate)
                .asDocument();
    }
}
