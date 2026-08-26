-- MID4-134: MID4-132와 동일하게 각 조회 SQL을 3회 warm-up 후 EXPLAIN 및 5회 반복 측정한다.
-- 기존 Hibernate SQL은 변경하지 않고 측정 절차만 맞춘다.

\timing on
\pset pager off
\echo scale=__SCALE__
\echo query=recent_comments warmup
\o /dev/null
SELECT c1_0.id, a1_0.id, a1_0.title, u1_0.id, u1_0.nickname, c1_0.content, (SELECT COUNT(cl1_0.id) FROM comment_likes cl1_0 JOIN users lb1_0 ON lb1_0.id = cl1_0.liked_by WHERE cl1_0.comment_id = c1_0.id AND lb1_0.deleted_at IS NULL), c1_0.created_at FROM comments c1_0 JOIN users u1_0 ON u1_0.id = c1_0.user_id JOIN articles a1_0 ON a1_0.id = c1_0.article_id WHERE u1_0.id = '00000001-0000-4000-8000-000000000001' AND u1_0.deleted_at IS NULL AND c1_0.deleted_at IS NULL AND a1_0.deleted_at IS NULL ORDER BY c1_0.created_at DESC, c1_0.id DESC FETCH FIRST 10 ROWS ONLY;
SELECT c1_0.id, a1_0.id, a1_0.title, u1_0.id, u1_0.nickname, c1_0.content, (SELECT COUNT(cl1_0.id) FROM comment_likes cl1_0 JOIN users lb1_0 ON lb1_0.id = cl1_0.liked_by WHERE cl1_0.comment_id = c1_0.id AND lb1_0.deleted_at IS NULL), c1_0.created_at FROM comments c1_0 JOIN users u1_0 ON u1_0.id = c1_0.user_id JOIN articles a1_0 ON a1_0.id = c1_0.article_id WHERE u1_0.id = '00000001-0000-4000-8000-000000000001' AND u1_0.deleted_at IS NULL AND c1_0.deleted_at IS NULL AND a1_0.deleted_at IS NULL ORDER BY c1_0.created_at DESC, c1_0.id DESC FETCH FIRST 10 ROWS ONLY;
SELECT c1_0.id, a1_0.id, a1_0.title, u1_0.id, u1_0.nickname, c1_0.content, (SELECT COUNT(cl1_0.id) FROM comment_likes cl1_0 JOIN users lb1_0 ON lb1_0.id = cl1_0.liked_by WHERE cl1_0.comment_id = c1_0.id AND lb1_0.deleted_at IS NULL), c1_0.created_at FROM comments c1_0 JOIN users u1_0 ON u1_0.id = c1_0.user_id JOIN articles a1_0 ON a1_0.id = c1_0.article_id WHERE u1_0.id = '00000001-0000-4000-8000-000000000001' AND u1_0.deleted_at IS NULL AND c1_0.deleted_at IS NULL AND a1_0.deleted_at IS NULL ORDER BY c1_0.created_at DESC, c1_0.id DESC FETCH FIRST 10 ROWS ONLY;
\o
\echo query=recent_comments explain
EXPLAIN (ANALYZE, BUFFERS)
SELECT
    c1_0.id,
    a1_0.id,
    a1_0.title,
    u1_0.id,
    u1_0.nickname,
    c1_0.content,
    (
        SELECT COUNT(cl1_0.id)
        FROM comment_likes cl1_0
        JOIN users lb1_0 ON lb1_0.id = cl1_0.liked_by
        WHERE cl1_0.comment_id = c1_0.id
          AND lb1_0.deleted_at IS NULL
    ),
    c1_0.created_at
FROM comments c1_0
JOIN users u1_0 ON u1_0.id = c1_0.user_id
JOIN articles a1_0 ON a1_0.id = c1_0.article_id
WHERE u1_0.id = '00000001-0000-4000-8000-000000000001'
  AND u1_0.deleted_at IS NULL
  AND c1_0.deleted_at IS NULL
  AND a1_0.deleted_at IS NULL
ORDER BY c1_0.created_at DESC, c1_0.id DESC
FETCH FIRST 10 ROWS ONLY;
\echo query=recent_comments repeats
\o /dev/null
SELECT c1_0.id, a1_0.id, a1_0.title, u1_0.id, u1_0.nickname, c1_0.content, (SELECT COUNT(cl1_0.id) FROM comment_likes cl1_0 JOIN users lb1_0 ON lb1_0.id = cl1_0.liked_by WHERE cl1_0.comment_id = c1_0.id AND lb1_0.deleted_at IS NULL), c1_0.created_at FROM comments c1_0 JOIN users u1_0 ON u1_0.id = c1_0.user_id JOIN articles a1_0 ON a1_0.id = c1_0.article_id WHERE u1_0.id = '00000001-0000-4000-8000-000000000001' AND u1_0.deleted_at IS NULL AND c1_0.deleted_at IS NULL AND a1_0.deleted_at IS NULL ORDER BY c1_0.created_at DESC, c1_0.id DESC FETCH FIRST 10 ROWS ONLY;
SELECT c1_0.id, a1_0.id, a1_0.title, u1_0.id, u1_0.nickname, c1_0.content, (SELECT COUNT(cl1_0.id) FROM comment_likes cl1_0 JOIN users lb1_0 ON lb1_0.id = cl1_0.liked_by WHERE cl1_0.comment_id = c1_0.id AND lb1_0.deleted_at IS NULL), c1_0.created_at FROM comments c1_0 JOIN users u1_0 ON u1_0.id = c1_0.user_id JOIN articles a1_0 ON a1_0.id = c1_0.article_id WHERE u1_0.id = '00000001-0000-4000-8000-000000000001' AND u1_0.deleted_at IS NULL AND c1_0.deleted_at IS NULL AND a1_0.deleted_at IS NULL ORDER BY c1_0.created_at DESC, c1_0.id DESC FETCH FIRST 10 ROWS ONLY;
SELECT c1_0.id, a1_0.id, a1_0.title, u1_0.id, u1_0.nickname, c1_0.content, (SELECT COUNT(cl1_0.id) FROM comment_likes cl1_0 JOIN users lb1_0 ON lb1_0.id = cl1_0.liked_by WHERE cl1_0.comment_id = c1_0.id AND lb1_0.deleted_at IS NULL), c1_0.created_at FROM comments c1_0 JOIN users u1_0 ON u1_0.id = c1_0.user_id JOIN articles a1_0 ON a1_0.id = c1_0.article_id WHERE u1_0.id = '00000001-0000-4000-8000-000000000001' AND u1_0.deleted_at IS NULL AND c1_0.deleted_at IS NULL AND a1_0.deleted_at IS NULL ORDER BY c1_0.created_at DESC, c1_0.id DESC FETCH FIRST 10 ROWS ONLY;
SELECT c1_0.id, a1_0.id, a1_0.title, u1_0.id, u1_0.nickname, c1_0.content, (SELECT COUNT(cl1_0.id) FROM comment_likes cl1_0 JOIN users lb1_0 ON lb1_0.id = cl1_0.liked_by WHERE cl1_0.comment_id = c1_0.id AND lb1_0.deleted_at IS NULL), c1_0.created_at FROM comments c1_0 JOIN users u1_0 ON u1_0.id = c1_0.user_id JOIN articles a1_0 ON a1_0.id = c1_0.article_id WHERE u1_0.id = '00000001-0000-4000-8000-000000000001' AND u1_0.deleted_at IS NULL AND c1_0.deleted_at IS NULL AND a1_0.deleted_at IS NULL ORDER BY c1_0.created_at DESC, c1_0.id DESC FETCH FIRST 10 ROWS ONLY;
SELECT c1_0.id, a1_0.id, a1_0.title, u1_0.id, u1_0.nickname, c1_0.content, (SELECT COUNT(cl1_0.id) FROM comment_likes cl1_0 JOIN users lb1_0 ON lb1_0.id = cl1_0.liked_by WHERE cl1_0.comment_id = c1_0.id AND lb1_0.deleted_at IS NULL), c1_0.created_at FROM comments c1_0 JOIN users u1_0 ON u1_0.id = c1_0.user_id JOIN articles a1_0 ON a1_0.id = c1_0.article_id WHERE u1_0.id = '00000001-0000-4000-8000-000000000001' AND u1_0.deleted_at IS NULL AND c1_0.deleted_at IS NULL AND a1_0.deleted_at IS NULL ORDER BY c1_0.created_at DESC, c1_0.id DESC FETCH FIRST 10 ROWS ONLY;
\o
\echo query=recent_liked_comments warmup
\o /dev/null
SELECT cl1_0.id, cl1_0.created_at, c1_0.id, a1_0.id, a1_0.title, u1_0.id, u1_0.nickname, c1_0.content, (SELECT COUNT(cl2_0.id) FROM comment_likes cl2_0 JOIN comments c2_0 ON c2_0.id = cl2_0.comment_id JOIN users lb2_0 ON lb2_0.id = cl2_0.liked_by WHERE c2_0.id = c1_0.id AND lb2_0.deleted_at IS NULL), c1_0.created_at FROM comment_likes cl1_0 JOIN comments c1_0 ON c1_0.id = cl1_0.comment_id JOIN users lb1_0 ON lb1_0.id = cl1_0.liked_by JOIN users u1_0 ON u1_0.id = c1_0.user_id JOIN articles a1_0 ON a1_0.id = c1_0.article_id WHERE lb1_0.id = '00000001-0000-4000-8000-000000000001' AND lb1_0.deleted_at IS NULL AND u1_0.deleted_at IS NULL AND a1_0.deleted_at IS NULL AND c1_0.deleted_at IS NULL ORDER BY cl1_0.created_at DESC, cl1_0.id DESC FETCH FIRST 10 ROWS ONLY;
SELECT cl1_0.id, cl1_0.created_at, c1_0.id, a1_0.id, a1_0.title, u1_0.id, u1_0.nickname, c1_0.content, (SELECT COUNT(cl2_0.id) FROM comment_likes cl2_0 JOIN comments c2_0 ON c2_0.id = cl2_0.comment_id JOIN users lb2_0 ON lb2_0.id = cl2_0.liked_by WHERE c2_0.id = c1_0.id AND lb2_0.deleted_at IS NULL), c1_0.created_at FROM comment_likes cl1_0 JOIN comments c1_0 ON c1_0.id = cl1_0.comment_id JOIN users lb1_0 ON lb1_0.id = cl1_0.liked_by JOIN users u1_0 ON u1_0.id = c1_0.user_id JOIN articles a1_0 ON a1_0.id = c1_0.article_id WHERE lb1_0.id = '00000001-0000-4000-8000-000000000001' AND lb1_0.deleted_at IS NULL AND u1_0.deleted_at IS NULL AND a1_0.deleted_at IS NULL AND c1_0.deleted_at IS NULL ORDER BY cl1_0.created_at DESC, cl1_0.id DESC FETCH FIRST 10 ROWS ONLY;
SELECT cl1_0.id, cl1_0.created_at, c1_0.id, a1_0.id, a1_0.title, u1_0.id, u1_0.nickname, c1_0.content, (SELECT COUNT(cl2_0.id) FROM comment_likes cl2_0 JOIN comments c2_0 ON c2_0.id = cl2_0.comment_id JOIN users lb2_0 ON lb2_0.id = cl2_0.liked_by WHERE c2_0.id = c1_0.id AND lb2_0.deleted_at IS NULL), c1_0.created_at FROM comment_likes cl1_0 JOIN comments c1_0 ON c1_0.id = cl1_0.comment_id JOIN users lb1_0 ON lb1_0.id = cl1_0.liked_by JOIN users u1_0 ON u1_0.id = c1_0.user_id JOIN articles a1_0 ON a1_0.id = c1_0.article_id WHERE lb1_0.id = '00000001-0000-4000-8000-000000000001' AND lb1_0.deleted_at IS NULL AND u1_0.deleted_at IS NULL AND a1_0.deleted_at IS NULL AND c1_0.deleted_at IS NULL ORDER BY cl1_0.created_at DESC, cl1_0.id DESC FETCH FIRST 10 ROWS ONLY;
\o
\echo query=recent_liked_comments explain
EXPLAIN (ANALYZE, BUFFERS)
SELECT
    cl1_0.id,
    cl1_0.created_at,
    c1_0.id,
    a1_0.id,
    a1_0.title,
    u1_0.id,
    u1_0.nickname,
    c1_0.content,
    (
        SELECT COUNT(cl2_0.id)
        FROM comment_likes cl2_0
        JOIN comments c2_0 ON c2_0.id = cl2_0.comment_id
        JOIN users lb2_0 ON lb2_0.id = cl2_0.liked_by
        WHERE c2_0.id = c1_0.id
          AND lb2_0.deleted_at IS NULL
    ),
    c1_0.created_at
FROM comment_likes cl1_0
JOIN comments c1_0 ON c1_0.id = cl1_0.comment_id
JOIN users lb1_0 ON lb1_0.id = cl1_0.liked_by
JOIN users u1_0 ON u1_0.id = c1_0.user_id
JOIN articles a1_0 ON a1_0.id = c1_0.article_id
WHERE lb1_0.id = '00000001-0000-4000-8000-000000000001'
  AND lb1_0.deleted_at IS NULL
  AND u1_0.deleted_at IS NULL
  AND a1_0.deleted_at IS NULL
  AND c1_0.deleted_at IS NULL
ORDER BY cl1_0.created_at DESC, cl1_0.id DESC
FETCH FIRST 10 ROWS ONLY;
\echo query=recent_liked_comments repeats
\o /dev/null
SELECT cl1_0.id, cl1_0.created_at, c1_0.id, a1_0.id, a1_0.title, u1_0.id, u1_0.nickname, c1_0.content, (SELECT COUNT(cl2_0.id) FROM comment_likes cl2_0 JOIN comments c2_0 ON c2_0.id = cl2_0.comment_id JOIN users lb2_0 ON lb2_0.id = cl2_0.liked_by WHERE c2_0.id = c1_0.id AND lb2_0.deleted_at IS NULL), c1_0.created_at FROM comment_likes cl1_0 JOIN comments c1_0 ON c1_0.id = cl1_0.comment_id JOIN users lb1_0 ON lb1_0.id = cl1_0.liked_by JOIN users u1_0 ON u1_0.id = c1_0.user_id JOIN articles a1_0 ON a1_0.id = c1_0.article_id WHERE lb1_0.id = '00000001-0000-4000-8000-000000000001' AND lb1_0.deleted_at IS NULL AND u1_0.deleted_at IS NULL AND a1_0.deleted_at IS NULL AND c1_0.deleted_at IS NULL ORDER BY cl1_0.created_at DESC, cl1_0.id DESC FETCH FIRST 10 ROWS ONLY;
SELECT cl1_0.id, cl1_0.created_at, c1_0.id, a1_0.id, a1_0.title, u1_0.id, u1_0.nickname, c1_0.content, (SELECT COUNT(cl2_0.id) FROM comment_likes cl2_0 JOIN comments c2_0 ON c2_0.id = cl2_0.comment_id JOIN users lb2_0 ON lb2_0.id = cl2_0.liked_by WHERE c2_0.id = c1_0.id AND lb2_0.deleted_at IS NULL), c1_0.created_at FROM comment_likes cl1_0 JOIN comments c1_0 ON c1_0.id = cl1_0.comment_id JOIN users lb1_0 ON lb1_0.id = cl1_0.liked_by JOIN users u1_0 ON u1_0.id = c1_0.user_id JOIN articles a1_0 ON a1_0.id = c1_0.article_id WHERE lb1_0.id = '00000001-0000-4000-8000-000000000001' AND lb1_0.deleted_at IS NULL AND u1_0.deleted_at IS NULL AND a1_0.deleted_at IS NULL AND c1_0.deleted_at IS NULL ORDER BY cl1_0.created_at DESC, cl1_0.id DESC FETCH FIRST 10 ROWS ONLY;
SELECT cl1_0.id, cl1_0.created_at, c1_0.id, a1_0.id, a1_0.title, u1_0.id, u1_0.nickname, c1_0.content, (SELECT COUNT(cl2_0.id) FROM comment_likes cl2_0 JOIN comments c2_0 ON c2_0.id = cl2_0.comment_id JOIN users lb2_0 ON lb2_0.id = cl2_0.liked_by WHERE c2_0.id = c1_0.id AND lb2_0.deleted_at IS NULL), c1_0.created_at FROM comment_likes cl1_0 JOIN comments c1_0 ON c1_0.id = cl1_0.comment_id JOIN users lb1_0 ON lb1_0.id = cl1_0.liked_by JOIN users u1_0 ON u1_0.id = c1_0.user_id JOIN articles a1_0 ON a1_0.id = c1_0.article_id WHERE lb1_0.id = '00000001-0000-4000-8000-000000000001' AND lb1_0.deleted_at IS NULL AND u1_0.deleted_at IS NULL AND a1_0.deleted_at IS NULL AND c1_0.deleted_at IS NULL ORDER BY cl1_0.created_at DESC, cl1_0.id DESC FETCH FIRST 10 ROWS ONLY;
SELECT cl1_0.id, cl1_0.created_at, c1_0.id, a1_0.id, a1_0.title, u1_0.id, u1_0.nickname, c1_0.content, (SELECT COUNT(cl2_0.id) FROM comment_likes cl2_0 JOIN comments c2_0 ON c2_0.id = cl2_0.comment_id JOIN users lb2_0 ON lb2_0.id = cl2_0.liked_by WHERE c2_0.id = c1_0.id AND lb2_0.deleted_at IS NULL), c1_0.created_at FROM comment_likes cl1_0 JOIN comments c1_0 ON c1_0.id = cl1_0.comment_id JOIN users lb1_0 ON lb1_0.id = cl1_0.liked_by JOIN users u1_0 ON u1_0.id = c1_0.user_id JOIN articles a1_0 ON a1_0.id = c1_0.article_id WHERE lb1_0.id = '00000001-0000-4000-8000-000000000001' AND lb1_0.deleted_at IS NULL AND u1_0.deleted_at IS NULL AND a1_0.deleted_at IS NULL AND c1_0.deleted_at IS NULL ORDER BY cl1_0.created_at DESC, cl1_0.id DESC FETCH FIRST 10 ROWS ONLY;
SELECT cl1_0.id, cl1_0.created_at, c1_0.id, a1_0.id, a1_0.title, u1_0.id, u1_0.nickname, c1_0.content, (SELECT COUNT(cl2_0.id) FROM comment_likes cl2_0 JOIN comments c2_0 ON c2_0.id = cl2_0.comment_id JOIN users lb2_0 ON lb2_0.id = cl2_0.liked_by WHERE c2_0.id = c1_0.id AND lb2_0.deleted_at IS NULL), c1_0.created_at FROM comment_likes cl1_0 JOIN comments c1_0 ON c1_0.id = cl1_0.comment_id JOIN users lb1_0 ON lb1_0.id = cl1_0.liked_by JOIN users u1_0 ON u1_0.id = c1_0.user_id JOIN articles a1_0 ON a1_0.id = c1_0.article_id WHERE lb1_0.id = '00000001-0000-4000-8000-000000000001' AND lb1_0.deleted_at IS NULL AND u1_0.deleted_at IS NULL AND a1_0.deleted_at IS NULL AND c1_0.deleted_at IS NULL ORDER BY cl1_0.created_at DESC, cl1_0.id DESC FETCH FIRST 10 ROWS ONLY;
\o
\echo query=recent_article_views warmup
\o /dev/null
SELECT av1_0.id, u1_0.id, av1_0.viewed_at, a1_0.id, a1_0.source, a1_0.link, a1_0.title, a1_0.date, a1_0.summary, (SELECT COUNT(c1_0.id) FROM comments c1_0 JOIN users u2_0 ON u2_0.id = c1_0.user_id WHERE c1_0.article_id = a1_0.id AND c1_0.deleted_at IS NULL AND u2_0.deleted_at IS NULL), (SELECT COUNT(av2_0.id) FROM article_views av2_0 JOIN users u3_0 ON u3_0.id = av2_0.user_id WHERE av2_0.article_id = a1_0.id AND u3_0.deleted_at IS NULL) FROM article_views av1_0 JOIN articles a1_0 ON a1_0.id = av1_0.article_id JOIN users u1_0 ON u1_0.id = av1_0.user_id WHERE u1_0.id = '00000001-0000-4000-8000-000000000001' AND a1_0.deleted_at IS NULL AND u1_0.deleted_at IS NULL ORDER BY av1_0.viewed_at DESC, av1_0.id DESC FETCH FIRST 10 ROWS ONLY;
SELECT av1_0.id, u1_0.id, av1_0.viewed_at, a1_0.id, a1_0.source, a1_0.link, a1_0.title, a1_0.date, a1_0.summary, (SELECT COUNT(c1_0.id) FROM comments c1_0 JOIN users u2_0 ON u2_0.id = c1_0.user_id WHERE c1_0.article_id = a1_0.id AND c1_0.deleted_at IS NULL AND u2_0.deleted_at IS NULL), (SELECT COUNT(av2_0.id) FROM article_views av2_0 JOIN users u3_0 ON u3_0.id = av2_0.user_id WHERE av2_0.article_id = a1_0.id AND u3_0.deleted_at IS NULL) FROM article_views av1_0 JOIN articles a1_0 ON a1_0.id = av1_0.article_id JOIN users u1_0 ON u1_0.id = av1_0.user_id WHERE u1_0.id = '00000001-0000-4000-8000-000000000001' AND a1_0.deleted_at IS NULL AND u1_0.deleted_at IS NULL ORDER BY av1_0.viewed_at DESC, av1_0.id DESC FETCH FIRST 10 ROWS ONLY;
SELECT av1_0.id, u1_0.id, av1_0.viewed_at, a1_0.id, a1_0.source, a1_0.link, a1_0.title, a1_0.date, a1_0.summary, (SELECT COUNT(c1_0.id) FROM comments c1_0 JOIN users u2_0 ON u2_0.id = c1_0.user_id WHERE c1_0.article_id = a1_0.id AND c1_0.deleted_at IS NULL AND u2_0.deleted_at IS NULL), (SELECT COUNT(av2_0.id) FROM article_views av2_0 JOIN users u3_0 ON u3_0.id = av2_0.user_id WHERE av2_0.article_id = a1_0.id AND u3_0.deleted_at IS NULL) FROM article_views av1_0 JOIN articles a1_0 ON a1_0.id = av1_0.article_id JOIN users u1_0 ON u1_0.id = av1_0.user_id WHERE u1_0.id = '00000001-0000-4000-8000-000000000001' AND a1_0.deleted_at IS NULL AND u1_0.deleted_at IS NULL ORDER BY av1_0.viewed_at DESC, av1_0.id DESC FETCH FIRST 10 ROWS ONLY;
\o
\echo query=recent_article_views explain
EXPLAIN (ANALYZE, BUFFERS)
SELECT
    av1_0.id,
    u1_0.id,
    av1_0.viewed_at,
    a1_0.id,
    a1_0.source,
    a1_0.link,
    a1_0.title,
    a1_0.date,
    a1_0.summary,
    (
        SELECT COUNT(c1_0.id)
        FROM comments c1_0
        JOIN users u2_0 ON u2_0.id = c1_0.user_id
        WHERE c1_0.article_id = a1_0.id
          AND c1_0.deleted_at IS NULL
          AND u2_0.deleted_at IS NULL
    ),
    (
        SELECT COUNT(av2_0.id)
        FROM article_views av2_0
        JOIN users u3_0 ON u3_0.id = av2_0.user_id
        WHERE av2_0.article_id = a1_0.id
          AND u3_0.deleted_at IS NULL
    )
FROM article_views av1_0
JOIN articles a1_0 ON a1_0.id = av1_0.article_id
JOIN users u1_0 ON u1_0.id = av1_0.user_id
WHERE u1_0.id = '00000001-0000-4000-8000-000000000001'
  AND a1_0.deleted_at IS NULL
  AND u1_0.deleted_at IS NULL
ORDER BY av1_0.viewed_at DESC, av1_0.id DESC
FETCH FIRST 10 ROWS ONLY;
\echo query=recent_article_views repeats
\o /dev/null
SELECT av1_0.id, u1_0.id, av1_0.viewed_at, a1_0.id, a1_0.source, a1_0.link, a1_0.title, a1_0.date, a1_0.summary, (SELECT COUNT(c1_0.id) FROM comments c1_0 JOIN users u2_0 ON u2_0.id = c1_0.user_id WHERE c1_0.article_id = a1_0.id AND c1_0.deleted_at IS NULL AND u2_0.deleted_at IS NULL), (SELECT COUNT(av2_0.id) FROM article_views av2_0 JOIN users u3_0 ON u3_0.id = av2_0.user_id WHERE av2_0.article_id = a1_0.id AND u3_0.deleted_at IS NULL) FROM article_views av1_0 JOIN articles a1_0 ON a1_0.id = av1_0.article_id JOIN users u1_0 ON u1_0.id = av1_0.user_id WHERE u1_0.id = '00000001-0000-4000-8000-000000000001' AND a1_0.deleted_at IS NULL AND u1_0.deleted_at IS NULL ORDER BY av1_0.viewed_at DESC, av1_0.id DESC FETCH FIRST 10 ROWS ONLY;
SELECT av1_0.id, u1_0.id, av1_0.viewed_at, a1_0.id, a1_0.source, a1_0.link, a1_0.title, a1_0.date, a1_0.summary, (SELECT COUNT(c1_0.id) FROM comments c1_0 JOIN users u2_0 ON u2_0.id = c1_0.user_id WHERE c1_0.article_id = a1_0.id AND c1_0.deleted_at IS NULL AND u2_0.deleted_at IS NULL), (SELECT COUNT(av2_0.id) FROM article_views av2_0 JOIN users u3_0 ON u3_0.id = av2_0.user_id WHERE av2_0.article_id = a1_0.id AND u3_0.deleted_at IS NULL) FROM article_views av1_0 JOIN articles a1_0 ON a1_0.id = av1_0.article_id JOIN users u1_0 ON u1_0.id = av1_0.user_id WHERE u1_0.id = '00000001-0000-4000-8000-000000000001' AND a1_0.deleted_at IS NULL AND u1_0.deleted_at IS NULL ORDER BY av1_0.viewed_at DESC, av1_0.id DESC FETCH FIRST 10 ROWS ONLY;
SELECT av1_0.id, u1_0.id, av1_0.viewed_at, a1_0.id, a1_0.source, a1_0.link, a1_0.title, a1_0.date, a1_0.summary, (SELECT COUNT(c1_0.id) FROM comments c1_0 JOIN users u2_0 ON u2_0.id = c1_0.user_id WHERE c1_0.article_id = a1_0.id AND c1_0.deleted_at IS NULL AND u2_0.deleted_at IS NULL), (SELECT COUNT(av2_0.id) FROM article_views av2_0 JOIN users u3_0 ON u3_0.id = av2_0.user_id WHERE av2_0.article_id = a1_0.id AND u3_0.deleted_at IS NULL) FROM article_views av1_0 JOIN articles a1_0 ON a1_0.id = av1_0.article_id JOIN users u1_0 ON u1_0.id = av1_0.user_id WHERE u1_0.id = '00000001-0000-4000-8000-000000000001' AND a1_0.deleted_at IS NULL AND u1_0.deleted_at IS NULL ORDER BY av1_0.viewed_at DESC, av1_0.id DESC FETCH FIRST 10 ROWS ONLY;
SELECT av1_0.id, u1_0.id, av1_0.viewed_at, a1_0.id, a1_0.source, a1_0.link, a1_0.title, a1_0.date, a1_0.summary, (SELECT COUNT(c1_0.id) FROM comments c1_0 JOIN users u2_0 ON u2_0.id = c1_0.user_id WHERE c1_0.article_id = a1_0.id AND c1_0.deleted_at IS NULL AND u2_0.deleted_at IS NULL), (SELECT COUNT(av2_0.id) FROM article_views av2_0 JOIN users u3_0 ON u3_0.id = av2_0.user_id WHERE av2_0.article_id = a1_0.id AND u3_0.deleted_at IS NULL) FROM article_views av1_0 JOIN articles a1_0 ON a1_0.id = av1_0.article_id JOIN users u1_0 ON u1_0.id = av1_0.user_id WHERE u1_0.id = '00000001-0000-4000-8000-000000000001' AND a1_0.deleted_at IS NULL AND u1_0.deleted_at IS NULL ORDER BY av1_0.viewed_at DESC, av1_0.id DESC FETCH FIRST 10 ROWS ONLY;
SELECT av1_0.id, u1_0.id, av1_0.viewed_at, a1_0.id, a1_0.source, a1_0.link, a1_0.title, a1_0.date, a1_0.summary, (SELECT COUNT(c1_0.id) FROM comments c1_0 JOIN users u2_0 ON u2_0.id = c1_0.user_id WHERE c1_0.article_id = a1_0.id AND c1_0.deleted_at IS NULL AND u2_0.deleted_at IS NULL), (SELECT COUNT(av2_0.id) FROM article_views av2_0 JOIN users u3_0 ON u3_0.id = av2_0.user_id WHERE av2_0.article_id = a1_0.id AND u3_0.deleted_at IS NULL) FROM article_views av1_0 JOIN articles a1_0 ON a1_0.id = av1_0.article_id JOIN users u1_0 ON u1_0.id = av1_0.user_id WHERE u1_0.id = '00000001-0000-4000-8000-000000000001' AND a1_0.deleted_at IS NULL AND u1_0.deleted_at IS NULL ORDER BY av1_0.viewed_at DESC, av1_0.id DESC FETCH FIRST 10 ROWS ONLY;
\o
\echo query=subscribed_interests_main warmup
\o /dev/null
SELECT s1_0.id, s1_0.created_at, i1_0.id, i1_0.name, i1_0.created_at, i1_0.updated_at, (SELECT COUNT(s2_0.id) FROM subscriptions s2_0 JOIN users u2_0 ON s2_0.user_id = u2_0.id JOIN interests i2_0 ON i2_0.id = s2_0.interest_id WHERE i2_0.id = i1_0.id AND u2_0.deleted_at IS NULL) FROM subscriptions s1_0 JOIN interests i1_0 ON i1_0.id = s1_0.interest_id JOIN users u1_0 ON u1_0.id = s1_0.user_id WHERE s1_0.user_id = '00000001-0000-4000-8000-000000000001' AND u1_0.deleted_at IS NULL ORDER BY s1_0.created_at DESC, s1_0.id DESC;
SELECT s1_0.id, s1_0.created_at, i1_0.id, i1_0.name, i1_0.created_at, i1_0.updated_at, (SELECT COUNT(s2_0.id) FROM subscriptions s2_0 JOIN users u2_0 ON s2_0.user_id = u2_0.id JOIN interests i2_0 ON i2_0.id = s2_0.interest_id WHERE i2_0.id = i1_0.id AND u2_0.deleted_at IS NULL) FROM subscriptions s1_0 JOIN interests i1_0 ON i1_0.id = s1_0.interest_id JOIN users u1_0 ON u1_0.id = s1_0.user_id WHERE s1_0.user_id = '00000001-0000-4000-8000-000000000001' AND u1_0.deleted_at IS NULL ORDER BY s1_0.created_at DESC, s1_0.id DESC;
SELECT s1_0.id, s1_0.created_at, i1_0.id, i1_0.name, i1_0.created_at, i1_0.updated_at, (SELECT COUNT(s2_0.id) FROM subscriptions s2_0 JOIN users u2_0 ON s2_0.user_id = u2_0.id JOIN interests i2_0 ON i2_0.id = s2_0.interest_id WHERE i2_0.id = i1_0.id AND u2_0.deleted_at IS NULL) FROM subscriptions s1_0 JOIN interests i1_0 ON i1_0.id = s1_0.interest_id JOIN users u1_0 ON u1_0.id = s1_0.user_id WHERE s1_0.user_id = '00000001-0000-4000-8000-000000000001' AND u1_0.deleted_at IS NULL ORDER BY s1_0.created_at DESC, s1_0.id DESC;
\o
\echo query=subscribed_interests_main explain
EXPLAIN (ANALYZE, BUFFERS)
SELECT
    s1_0.id,
    s1_0.created_at,
    i1_0.id,
    i1_0.name,
    i1_0.created_at,
    i1_0.updated_at,
    (
        SELECT COUNT(s2_0.id)
        FROM subscriptions s2_0
        JOIN users u2_0 ON s2_0.user_id = u2_0.id
        JOIN interests i2_0 ON i2_0.id = s2_0.interest_id
        WHERE i2_0.id = i1_0.id
          AND u2_0.deleted_at IS NULL
    )
FROM subscriptions s1_0
JOIN interests i1_0 ON i1_0.id = s1_0.interest_id
JOIN users u1_0 ON u1_0.id = s1_0.user_id
WHERE s1_0.user_id = '00000001-0000-4000-8000-000000000001'
  AND u1_0.deleted_at IS NULL
ORDER BY s1_0.created_at DESC, s1_0.id DESC;
\echo query=subscribed_interests_main repeats
\o /dev/null
SELECT s1_0.id, s1_0.created_at, i1_0.id, i1_0.name, i1_0.created_at, i1_0.updated_at, (SELECT COUNT(s2_0.id) FROM subscriptions s2_0 JOIN users u2_0 ON s2_0.user_id = u2_0.id JOIN interests i2_0 ON i2_0.id = s2_0.interest_id WHERE i2_0.id = i1_0.id AND u2_0.deleted_at IS NULL) FROM subscriptions s1_0 JOIN interests i1_0 ON i1_0.id = s1_0.interest_id JOIN users u1_0 ON u1_0.id = s1_0.user_id WHERE s1_0.user_id = '00000001-0000-4000-8000-000000000001' AND u1_0.deleted_at IS NULL ORDER BY s1_0.created_at DESC, s1_0.id DESC;
SELECT s1_0.id, s1_0.created_at, i1_0.id, i1_0.name, i1_0.created_at, i1_0.updated_at, (SELECT COUNT(s2_0.id) FROM subscriptions s2_0 JOIN users u2_0 ON s2_0.user_id = u2_0.id JOIN interests i2_0 ON i2_0.id = s2_0.interest_id WHERE i2_0.id = i1_0.id AND u2_0.deleted_at IS NULL) FROM subscriptions s1_0 JOIN interests i1_0 ON i1_0.id = s1_0.interest_id JOIN users u1_0 ON u1_0.id = s1_0.user_id WHERE s1_0.user_id = '00000001-0000-4000-8000-000000000001' AND u1_0.deleted_at IS NULL ORDER BY s1_0.created_at DESC, s1_0.id DESC;
SELECT s1_0.id, s1_0.created_at, i1_0.id, i1_0.name, i1_0.created_at, i1_0.updated_at, (SELECT COUNT(s2_0.id) FROM subscriptions s2_0 JOIN users u2_0 ON s2_0.user_id = u2_0.id JOIN interests i2_0 ON i2_0.id = s2_0.interest_id WHERE i2_0.id = i1_0.id AND u2_0.deleted_at IS NULL) FROM subscriptions s1_0 JOIN interests i1_0 ON i1_0.id = s1_0.interest_id JOIN users u1_0 ON u1_0.id = s1_0.user_id WHERE s1_0.user_id = '00000001-0000-4000-8000-000000000001' AND u1_0.deleted_at IS NULL ORDER BY s1_0.created_at DESC, s1_0.id DESC;
SELECT s1_0.id, s1_0.created_at, i1_0.id, i1_0.name, i1_0.created_at, i1_0.updated_at, (SELECT COUNT(s2_0.id) FROM subscriptions s2_0 JOIN users u2_0 ON s2_0.user_id = u2_0.id JOIN interests i2_0 ON i2_0.id = s2_0.interest_id WHERE i2_0.id = i1_0.id AND u2_0.deleted_at IS NULL) FROM subscriptions s1_0 JOIN interests i1_0 ON i1_0.id = s1_0.interest_id JOIN users u1_0 ON u1_0.id = s1_0.user_id WHERE s1_0.user_id = '00000001-0000-4000-8000-000000000001' AND u1_0.deleted_at IS NULL ORDER BY s1_0.created_at DESC, s1_0.id DESC;
SELECT s1_0.id, s1_0.created_at, i1_0.id, i1_0.name, i1_0.created_at, i1_0.updated_at, (SELECT COUNT(s2_0.id) FROM subscriptions s2_0 JOIN users u2_0 ON s2_0.user_id = u2_0.id JOIN interests i2_0 ON i2_0.id = s2_0.interest_id WHERE i2_0.id = i1_0.id AND u2_0.deleted_at IS NULL) FROM subscriptions s1_0 JOIN interests i1_0 ON i1_0.id = s1_0.interest_id JOIN users u1_0 ON u1_0.id = s1_0.user_id WHERE s1_0.user_id = '00000001-0000-4000-8000-000000000001' AND u1_0.deleted_at IS NULL ORDER BY s1_0.created_at DESC, s1_0.id DESC;
\o
\echo query=subscribed_interests_keywords warmup
\o /dev/null
WITH target_interest_ids AS (SELECT s1_0.interest_id FROM subscriptions s1_0 WHERE s1_0.user_id = '00000001-0000-4000-8000-000000000001') SELECT k1_0.interest_id, k1_0.id, k1_0.keyword FROM keywords k1_0 WHERE k1_0.interest_id = ANY (ARRAY(SELECT interest_id FROM target_interest_ids));
WITH target_interest_ids AS (SELECT s1_0.interest_id FROM subscriptions s1_0 WHERE s1_0.user_id = '00000001-0000-4000-8000-000000000001') SELECT k1_0.interest_id, k1_0.id, k1_0.keyword FROM keywords k1_0 WHERE k1_0.interest_id = ANY (ARRAY(SELECT interest_id FROM target_interest_ids));
WITH target_interest_ids AS (SELECT s1_0.interest_id FROM subscriptions s1_0 WHERE s1_0.user_id = '00000001-0000-4000-8000-000000000001') SELECT k1_0.interest_id, k1_0.id, k1_0.keyword FROM keywords k1_0 WHERE k1_0.interest_id = ANY (ARRAY(SELECT interest_id FROM target_interest_ids));
\o
\echo query=subscribed_interests_keywords explain
EXPLAIN (ANALYZE, BUFFERS)
WITH target_interest_ids AS (
    SELECT s1_0.interest_id
    FROM subscriptions s1_0
    WHERE s1_0.user_id = '00000001-0000-4000-8000-000000000001'
)
SELECT k1_0.interest_id, k1_0.id, k1_0.keyword
FROM keywords k1_0
WHERE k1_0.interest_id = ANY (ARRAY(SELECT interest_id FROM target_interest_ids));
\echo query=subscribed_interests_keywords repeats
\o /dev/null
WITH target_interest_ids AS (SELECT s1_0.interest_id FROM subscriptions s1_0 WHERE s1_0.user_id = '00000001-0000-4000-8000-000000000001') SELECT k1_0.interest_id, k1_0.id, k1_0.keyword FROM keywords k1_0 WHERE k1_0.interest_id = ANY (ARRAY(SELECT interest_id FROM target_interest_ids));
WITH target_interest_ids AS (SELECT s1_0.interest_id FROM subscriptions s1_0 WHERE s1_0.user_id = '00000001-0000-4000-8000-000000000001') SELECT k1_0.interest_id, k1_0.id, k1_0.keyword FROM keywords k1_0 WHERE k1_0.interest_id = ANY (ARRAY(SELECT interest_id FROM target_interest_ids));
WITH target_interest_ids AS (SELECT s1_0.interest_id FROM subscriptions s1_0 WHERE s1_0.user_id = '00000001-0000-4000-8000-000000000001') SELECT k1_0.interest_id, k1_0.id, k1_0.keyword FROM keywords k1_0 WHERE k1_0.interest_id = ANY (ARRAY(SELECT interest_id FROM target_interest_ids));
WITH target_interest_ids AS (SELECT s1_0.interest_id FROM subscriptions s1_0 WHERE s1_0.user_id = '00000001-0000-4000-8000-000000000001') SELECT k1_0.interest_id, k1_0.id, k1_0.keyword FROM keywords k1_0 WHERE k1_0.interest_id = ANY (ARRAY(SELECT interest_id FROM target_interest_ids));
WITH target_interest_ids AS (SELECT s1_0.interest_id FROM subscriptions s1_0 WHERE s1_0.user_id = '00000001-0000-4000-8000-000000000001') SELECT k1_0.interest_id, k1_0.id, k1_0.keyword FROM keywords k1_0 WHERE k1_0.interest_id = ANY (ARRAY(SELECT interest_id FROM target_interest_ids));
\o
