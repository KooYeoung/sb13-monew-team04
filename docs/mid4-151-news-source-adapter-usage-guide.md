# MID4-151 뉴스 수집 어댑터 사용 가이드

## 목적

이 문서는 뉴스 수집 작업자가 NAVER 뉴스 검색 어댑터와 RSS 뉴스 수집 어댑터를 사용하는 기준을 정리합니다.

어댑터는 외부 뉴스 출처 호출, 응답 파싱, `CollectedArticle` 변환까지만 담당합니다. 관심사 키워드 매칭, 링크 중복 제거, `Article` 저장, 실패 기사 재처리는 작업자 또는 별도 서비스 정책에서 처리합니다.

## 공통 계약

모든 뉴스 출처 어댑터는 `NewsSourceAdapter` 계약을 따릅니다.

```java
public interface NewsSourceAdapter {
    ArticleSource source();
    List<CollectedArticle> fetch();
}
```

`fetch()`는 해당 출처의 기본 수집 단위로 기사 후보 목록을 반환합니다.

```java
public record CollectedArticle(
        ArticleSource source,
        String title,
        String summary,
        String link,
        LocalDateTime publishedAt
) {
}
```

작업자는 `CollectedArticle`을 바로 저장 엔티티로 보지 말고, 저장 가능 여부를 한 번 더 판단해야 합니다.

## 기본 수집 흐름

Spring Bean으로 등록된 전체 어댑터를 주입받아 출처별로 호출할 수 있습니다.

```java
@Component
@RequiredArgsConstructor
public class NewsCollectWorker {

    private final List<NewsSourceAdapter> adapters;

    public void collect() {
        for (NewsSourceAdapter adapter : adapters) {
            try {
                List<CollectedArticle> articles = adapter.fetch();
                // 중복 링크 확인, summary 보정, Article 저장 정책 적용
            } catch (ArticleFetchFailedException | ArticleFetchParseException e) {
                // 출처 단위 실패 로그 또는 재시도 정책 적용
            }
        }
    }
}
```

한 출처의 호출 실패가 전체 수집 실패로 번지지 않도록 출처 단위로 예외를 분리해서 처리하는 것을 권장합니다.

## NAVER 사용법

NAVER는 `NaverNewsSourceAdapter`가 `NewsSourceAdapter`를 구현합니다.

```java
List<CollectedArticle> articles = naverNewsSourceAdapter.fetch();
```

NAVER 검색 요청 목록은 `NaverNewsSearchRequestProvider.getRequests()`에서 제공합니다. 현재 기본 구현인 `DefaultNaverNewsSearchRequestProvider`는 빈 목록을 반환하므로, 실제 수집 작업자는 관심사 키워드 기준에 맞게 provider 구현을 교체하거나 확장해야 합니다.

```java
public interface NaverNewsSearchRequestProvider {
    List<NaverNewsSearchRequest> getRequests();
}
```

`NaverNewsSearchRequest` 기준은 다음과 같습니다.

| 필드 | 기준 |
| --- | --- |
| `query` | 필수 검색어 |
| `display` | 1-100, 기본값 10 |
| `start` | 1-1000, 기본값 1 |
| `sort` | `sim` 또는 `date`, 기본값 `sim` |

NAVER 매핑 정책은 다음과 같습니다.

- `source`는 `ArticleSource.NAVER`입니다.
- `title`, `description`은 HTML 태그와 entity를 정리해 반환합니다.
- `link`는 `originallink`를 우선 사용하고, 없으면 `link`를 사용합니다.
- `originallink`와 `link`가 모두 없으면 해당 item은 제외합니다.
- `pubDate` 파싱에 실패하면 `ArticleFetchParseException`이 발생합니다.

## RSS 사용법

RSS 출처는 `RssNewsSourceAdapter`를 구현합니다.

```java
public interface RssNewsSourceAdapter extends NewsSourceAdapter {
    List<CollectedArticle> fetch(List<String> categoryKeys);
}
```

기본 `fetch()`는 출처별 대표 카테고리 1개만 호출합니다.

| 출처 | 기본 카테고리 key | 한글명 |
| --- | --- | --- |
| `HANKYUNG` | `all-news` | 전체뉴스 |
| `CHOSUN` | `all` | 전체기사 |
| `YEONHAP` | `latest` | 최신 |

작업자가 원하는 RSS 카테고리만 수집하려면 출처별 RSS 구현체의 `fetch(List<String> categoryKeys)`를 호출합니다.

```java
if (adapter instanceof HankyungRssNewsSourceAdapter hankyungAdapter) {
    List<CollectedArticle> articles = hankyungAdapter.fetch(List.of("economy", "finance"));
}
```

위 예시는 한국경제 전용입니다. 카테고리 key는 출처별 enum에 정의된 값만 사용할 수 있습니다.
카테고리 key는 앞뒤 공백 제거 후 lowercase로 정규화합니다. 지원하지 않는 key가 들어오면 `ArticleFetchRequestInvalidException`이 발생합니다.

### 한국경제 카테고리

| key | 한글명 |
| --- | --- |
| `all-news` | 전체뉴스 |
| `finance` | 증권 |
| `economy` | 경제 |
| `realestate` | 부동산 |
| `it` | IT |
| `politics` | 정치 |
| `international` | 국제 |
| `society` | 사회 |
| `life` | 생활 |
| `opinion` | 오피니언 |
| `sports` | 스포츠 |
| `entertainment` | 연예 |
| `video` | VIDEO |

### 조선일보 카테고리

| key | 한글명 |
| --- | --- |
| `all` | 전체기사 |
| `politics` | 정치 |
| `economy` | 경제 |
| `national` | 사회 |
| `international` | 국제 |
| `culture-life` | 문화/라이프 |
| `opinion` | 오피니언 |
| `sports` | 스포츠 |
| `entertainments` | 연예 |

### 연합뉴스TV 카테고리

| key | 한글명 |
| --- | --- |
| `latest` | 최신 |
| `politics` | 정치 |
| `economy` | 경제 |
| `bizn` | 비즈& |
| `stocks` | 증권 |
| `society` | 사회 |
| `local` | 지역 |
| `international` | 세계 |
| `culture` | 문화ㆍ연예 |
| `sports` | 스포츠 |
| `weather` | 날씨 |

RSS 매핑 정책은 다음과 같습니다.

- `title`은 RSS item의 `title`을 정리해 반환합니다.
- `link`는 RSS item의 `link`를 정리해 반환합니다.
- `link`가 없거나 비어 있으면 해당 item은 제외합니다.
- `publishedAt`은 Rome이 파싱한 `publishedDate`를 사용합니다.
- `publishedAt`을 만들 수 없으면 해당 item은 제외하고 warn 로그를 남깁니다.
- `summary`는 `description`을 우선 사용하고, 비어 있으면 `content:encoded`를 사용합니다.
- `description`과 `content:encoded`가 모두 비어 있으면 `summary=null`입니다.
- XML 파싱 실패는 `ArticleFetchParseException`으로 처리합니다.
- HTTP 오류, timeout 등 호출 실패는 `ArticleFetchFailedException`으로 처리합니다.

## 저장 작업자 유의사항

`Article` 엔티티 저장 제약은 다음과 같습니다.

| 필드 | 제약 |
| --- | --- |
| `title` | `nullable=false`, 최대 500자 |
| `summary` | `nullable=false`, `TEXT` |
| `link` | `nullable=false`, 최대 1000자, unique |
| `date` | `nullable=false` |
| `source` | `nullable=false` |

작업자는 저장 전에 다음 정책을 반드시 적용해야 합니다.

- `link` 기준 중복 기사는 저장하지 않습니다.
- RSS는 `summary=null` 후보가 반환될 수 있으므로 저장 전 기본 문구 사용, 제목 대체, 제외 중 하나의 정책을 정해야 합니다.
- 여러 RSS 카테고리를 동시에 호출하면 같은 링크가 중복 반환될 수 있습니다.
- 어댑터가 제외한 item을 별도 저장하거나 재처리하는 정책은 MID4-151 범위 밖입니다.

## 검증 명령

기본 테스트는 외부 네트워크 호출을 제외합니다.

```powershell
.\gradlew.bat test
.\gradlew.bat --no-daemon clean build
```

실제 외부 endpoint 호출 smoke 테스트는 `@Tag("external")`로 분리되어 있습니다.

```powershell
.\gradlew.bat --no-daemon rssExternalTest
.\gradlew.bat --no-daemon naverExternalTest
```
