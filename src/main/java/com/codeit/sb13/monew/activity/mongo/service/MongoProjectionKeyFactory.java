package com.codeit.sb13.monew.activity.mongo.service;

import com.codeit.sb13.monew.activity.mongo.document.ActivityHistoryType;
import com.codeit.sb13.monew.activity.mongo.document.ActivityTargetType;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/** MongoDB projection의 논리 키를 결정적인 SHA-256 {@code _id}로 변환한다. */
public final class MongoProjectionKeyFactory {

    private MongoProjectionKeyFactory() {
    }

    public static String activity(
            UUID userId,
            ActivityHistoryType type,
            ActivityTargetType targetType,
            UUID targetId
    ) {
        return hash("activity|" + userId + '|' + type + '|' + targetType + '|' + targetId);
    }

    public static String comment(UUID commentId) {
        return hash("comment|" + commentId);
    }

    public static String article(UUID articleId) {
        return hash("article|" + articleId);
    }

    public static String interest(UUID interestId) {
        return hash("interest|" + interestId);
    }

    private static String hash(String canonicalKey) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonicalKey.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm is unavailable", e);
        }
    }
}
