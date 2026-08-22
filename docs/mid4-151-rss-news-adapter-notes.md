# MID4-151 RSS 뉴스 수집 어댑터 확인 사항

## 작업 기준

- Jira: MID4-151
- 작업 브랜치: `feat/MID4-151-rss-news-adapter`
- 기준 커밋: `a7732aa MID4-150 NAVER 뉴스 수집 어댑터 구현 (#31)`
- 대상 출처: `HANKYUNG`, `CHOSUN`, `YEONHAP`

## Jira 범위

- RSS 기반 뉴스 출처 어댑터를 구현합니다.
- 각 RSS 응답을 공통 기사 후보 DTO인 `CollectedArticle`로 변환합니다.
- `NewsSourceAdapter.fetch()` 계약을 구현합니다.
- 뉴스 출처는 기존 `ArticleSource.HANKYUNG`, `ArticleSource.CHOSUN`, `ArticleSource.YEONHAP`을 사용합니다.
- RSS URL은 코드 고정값이 아니라 설정값으로 분리합니다.

## 현재 구현 기준

- RSS 전용 어댑터는 `RssNewsSourceAdapter.fetch(List<String> categoryKeys)` 계약을 추가합니다.
- 공통 어댑터는 `AbstractRssNewsSourceAdapter`에서 출처, 기본 카테고리 enum, `RssNewsClient`를 주입받아 처리합니다.
- 기본 `fetch()`는 출처별 대표 카테고리 enum 1개만 호출합니다.
- `fetch(List<String>)`는 작업자가 전달한 카테고리 key만 enum으로 변환해 호출합니다.
- 카테고리 key는 앞뒤 공백 제거 후 lowercase로 정규화하고, 그 외 문자열은 비교 대상으로 보지 않습니다.
- XML 파싱은 Rome(`SyndFeedInput`)을 사용합니다.

## 제외 범위

- 기사 저장
- 링크 중복 방지
- 관심사 키워드 매칭

## 한국경제 RSS 확인 사항

- RSS 목록 페이지: `https://www.hankyung.com/feed`
- 전체뉴스 RSS: `https://www.hankyung.com/feed/all-news`
- URL 구조: `https://www.hankyung.com/feed/{category}`

확인된 카테고리는 다음과 같습니다.

| 한글명 | 내부 키 | URL |
| --- | --- | --- |
| 전체뉴스 | `all-news` | `https://www.hankyung.com/feed/all-news` |
| 증권 | `finance` | `https://www.hankyung.com/feed/finance` |
| 경제 | `economy` | `https://www.hankyung.com/feed/economy` |
| 부동산 | `realestate` | `https://www.hankyung.com/feed/realestate` |
| IT | `it` | `https://www.hankyung.com/feed/it` |
| 정치 | `politics` | `https://www.hankyung.com/feed/politics` |
| 국제 | `international` | `https://www.hankyung.com/feed/international` |
| 사회 | `society` | `https://www.hankyung.com/feed/society` |
| 생활 | `life` | `https://www.hankyung.com/feed/life` |
| 오피니언 | `opinion` | `https://www.hankyung.com/feed/opinion` |
| 스포츠 | `sports` | `https://www.hankyung.com/feed/sports` |
| 연예 | `entertainment` | `https://www.hankyung.com/feed/entertainment` |
| VIDEO | `video` | `https://www.hankyung.com/feed/video` |

## 한국경제 RSS 응답 구조

한국경제 `all-news` 응답은 RSS 2.0 XML 구조입니다.

```xml
<rss version="2.0">
  <channel>
    <title><![CDATA[한국경제 | 전체뉴스]]></title>
    <link>https://www.hankyung.com/all-news</link>
    <language>ko</language>
    <lastBuildDate>Fri, 21 Aug 2026 09:24:53 +0900</lastBuildDate>
    <description>한경닷컴 RSS 서비스</description>
    <item>
      <title><![CDATA[기사 제목]]></title>
      <link><![CDATA[https://www.hankyung.com/article/...]]></link>
      <author><![CDATA[작성자]]></author>
      <pubDate>Fri, 21 Aug 2026 09:16:44 +0900</pubDate>
    </item>
  </channel>
</rss>
```

현재 확인한 `all-news` 응답의 `item`에는 다음 필드가 있습니다.

- `title`
- `link`
- `author`
- `pubDate`

`item.description`은 현재 확인한 `all-news` 응답에서는 확인되지 않았습니다.

## 조선일보 RSS 확인 사항

- RSS 목록 페이지: `https://rssplus.chosun.com/`
- base URL: `https://www.chosun.com/arc/outboundfeeds/rss`
- 전체기사 RSS: `https://www.chosun.com/arc/outboundfeeds/rss/?outputType=xml`
- 카테고리 URL 구조: `https://www.chosun.com/arc/outboundfeeds/rss/category/{category}/?outputType=xml`

구현 설정의 RSS base URL은 trailing slash 없이 관리합니다. 전체기사일 때만 `category/{category}/`를 붙이지 않고 base RSS URL에 `/?outputType=xml`을 붙입니다.

확인된 카테고리는 다음과 같습니다.

| 한글명 | 내부 키 | URL |
| --- | --- | --- |
| 전체기사 | `all` | `https://www.chosun.com/arc/outboundfeeds/rss/?outputType=xml` |
| 정치 | `politics` | `https://www.chosun.com/arc/outboundfeeds/rss/category/politics/?outputType=xml` |
| 경제 | `economy` | `https://www.chosun.com/arc/outboundfeeds/rss/category/economy/?outputType=xml` |
| 사회 | `national` | `https://www.chosun.com/arc/outboundfeeds/rss/category/national/?outputType=xml` |
| 국제 | `international` | `https://www.chosun.com/arc/outboundfeeds/rss/category/international/?outputType=xml` |
| 문화/라이프 | `culture-life` | `https://www.chosun.com/arc/outboundfeeds/rss/category/culture-life/?outputType=xml` |
| 오피니언 | `opinion` | `https://www.chosun.com/arc/outboundfeeds/rss/category/opinion/?outputType=xml` |
| 스포츠 | `sports` | `https://www.chosun.com/arc/outboundfeeds/rss/category/sports/?outputType=xml` |
| 연예 | `entertainments` | `https://www.chosun.com/arc/outboundfeeds/rss/category/entertainments/?outputType=xml` |

## 조선일보 RSS 응답 구조

조선일보 전체기사 응답은 RSS 2.0 XML 구조입니다.

```xml
<rss version="2.0">
  <channel>
    <title><![CDATA[조선일보]]></title>
    <link>https://www.chosun.com</link>
    <description><![CDATA[1등 인터넷뉴스 조선닷컴 | 전체기사]]></description>
    <lastBuildDate>Fri, 21 Aug 2026 01:06:39 +0000</lastBuildDate>
    <item>
      <title><![CDATA[기사 제목]]></title>
      <link>https://www.chosun.com/...</link>
      <guid isPermaLink="true">https://www.chosun.com/...</guid>
      <dc:creator><![CDATA[작성자]]></dc:creator>
      <description><![CDATA[요약]]></description>
      <pubDate>Fri, 21 Aug 2026 01:04:43 +0000</pubDate>
      <content:encoded><![CDATA[<p>본문 일부</p>]]></content:encoded>
      <media:content url="https://www.chosun.com/..." type="image/jpeg" />
    </item>
  </channel>
</rss>
```

현재 확인한 응답의 `item`에는 다음 필드가 있습니다.

- `title`
- `link`
- `guid`
- `dc:creator`
- `description`
- `pubDate`
- `content:encoded`
- `media:content`

`description`은 비어 있거나 특수 공백만 있는 경우가 있으므로, 조선일보 summary는 `content:encoded` fallback이 필요합니다.

## 연합뉴스TV RSS 확인 사항

- RSS 목록 페이지: `https://www.yonhapnewstv.co.kr/add/rss`
- 최신 RSS: `https://www.yonhapnewstv.co.kr/browse/feed/`
- 카테고리 URL 구조: `https://www.yonhapnewstv.co.kr/category/news/{category}/feed/`

목록 페이지에는 `http://...`로 표시되지만 실제 요청은 `https://...`로 리다이렉트되므로, 문서와 설정 기준은 `https` URL로 정리합니다.

확인된 카테고리는 다음과 같습니다.

| 한글명 | 내부 키 | URL |
| --- | --- | --- |
| 최신 | `latest` | `https://www.yonhapnewstv.co.kr/browse/feed/` |
| 정치 | `politics` | `https://www.yonhapnewstv.co.kr/category/news/politics/feed/` |
| 경제 | `economy` | `https://www.yonhapnewstv.co.kr/category/news/economy/feed/` |
| 비즈& | `bizn` | `https://www.yonhapnewstv.co.kr/category/news/bizn/feed/` |
| 증권 | `stocks` | `https://www.yonhapnewstv.co.kr/category/news/stocks/feed/` |
| 사회 | `society` | `https://www.yonhapnewstv.co.kr/category/news/society/feed/` |
| 지역 | `local` | `https://www.yonhapnewstv.co.kr/category/news/local/feed/` |
| 세계 | `international` | `https://www.yonhapnewstv.co.kr/category/news/international/feed/` |
| 문화ㆍ연예 | `culture` | `https://www.yonhapnewstv.co.kr/category/news/culture/feed/` |
| 스포츠 | `sports` | `https://www.yonhapnewstv.co.kr/category/news/sports/feed/` |
| 날씨 | `weather` | `https://www.yonhapnewstv.co.kr/category/news/weather/feed/` |

## 연합뉴스TV RSS 응답 구조

연합뉴스TV 최신 응답은 RSS 2.0 XML 구조입니다.

```xml
<rss version="2.0">
  <channel>
    <title><![CDATA[연합뉴스TV :: 대한민국 뉴스의 시작. 채널 23 » 최신]]></title>
    <link>https://www.yonhapnewstv.co.kr</link>
    <description />
    <lastBuildDate>Fri, 21 Aug 2026 10:28:09 +0900</lastBuildDate>
    <item>
      <title><![CDATA[기사 제목]]></title>
      <link>https://www.yonhapnewstv.co.kr/news/...</link>
      <comments>https://www.yonhapnewstv.co.kr/news/...#comments</comments>
      <pubDate>Fri, 21 Aug 2026 10:28:09 +0900</pubDate>
      <dc:creator>작성자</dc:creator>
      <category><![CDATA[최신]]></category>
      <guid isPermaLink="false">...</guid>
      <enclosure url="https://media.yonhapnewstv.co.kr/..." type="image/jpeg" />
      <description><![CDATA[요약]]></description>
      <content:encoded><![CDATA[<div>본문 일부</div>]]></content:encoded>
    </item>
  </channel>
</rss>
```

현재 확인한 응답의 `item`에는 다음 필드가 있습니다.

- `title`
- `link`
- `comments`
- `pubDate`
- `dc:creator`
- `category`
- `guid`
- `enclosure`
- `description`
- `content:encoded`

연합뉴스TV 응답은 `description`이 대부분 제공되지만, RSS 공통 정책에 맞춰 비어 있으면 `content:encoded` fallback을 적용합니다.

## 매핑 기준

### 공통 매핑 기준

| RSS item 필드 | `CollectedArticle` 필드 | 비고 |
| --- | --- | --- |
| `title` | `title` | CDATA 또는 텍스트 값 사용 |
| `link` | `link` | CDATA 또는 텍스트 값 사용 |
| `description`, `content:encoded` | `summary` | RSS 공통 summary 정책 적용 |
| `pubDate` | `publishedAt` | Rome이 파싱한 `publishedDate` 사용, 없으면 item 제외 |
| `author`, `dc:creator` | 사용하지 않음 | 현재 `CollectedArticle`에 대응 필드 없음 |
| `guid`, `comments`, `category`, `enclosure`, `media:content` | 사용하지 않음 | 현재 `CollectedArticle`에 대응 필드 없음 |

### RSS 공통 summary 정책

모든 RSS 출처의 `summary`는 같은 규칙으로 변환합니다.

1. `description`이 의미 있는 텍스트면 우선 사용합니다.
2. `description`이 없거나 비어 있으면 `content:encoded`를 사용합니다.
3. `content:encoded`는 HTML 제거, entity decode, 공백 정규화 후 사용합니다.
4. 둘 다 의미 있는 텍스트가 없으면 `summary=null`로 처리합니다.

다음 값은 비어 있는 값으로 처리합니다.

- `null`
- 빈 문자열
- 일반 공백만 있는 문자열
- `ㅤ` 같은 특수 공백 또는 채움 문자만 있는 문자열
- HTML 제거 후 남는 텍스트가 없는 문자열

현재 확인 기준으로 한국경제 `all-news`는 `description`과 `content:encoded`가 모두 없어 `summary=null`이 됩니다. 조선일보와 연합뉴스TV는 `description`이 비어 있을 때 `content:encoded` fallback을 적용합니다.

### 파싱 실패 정책

- XML 자체가 파싱되지 않으면 `ArticleFetchParseException`으로 처리합니다.
- `pubDate`가 없거나 Rome이 날짜를 제공하지 못하면 해당 item은 수집 후보에서 제외합니다.
- 제외 시 `source`, `title`, `link`를 warn 로그로 남깁니다.
- `Article.date`는 `nullable=false`이므로 `publishedAt=null` 후보를 서비스단에 전달하지 않습니다.
- HTTP 오류, timeout 등 RSS 호출 실패는 `ArticleFetchFailedException`으로 처리합니다.

## 구현 시 고려할 점

- 한국경제는 작업자가 원하는 카테고리를 호출할 수 있는 구조가 필요합니다.
- 조선일보도 작업자가 원하는 카테고리를 호출할 수 있는 구조가 필요합니다.
- 연합뉴스TV도 작업자가 원하는 카테고리를 호출할 수 있는 구조가 필요합니다.
- 조선일보 전체기사 카테고리는 URL 생성 규칙이 다르므로 별도 예외 처리가 필요합니다.
- 연합뉴스TV 최신 카테고리는 URL 생성 규칙이 다르므로 별도 예외 처리가 필요합니다.
- RSS base URL은 trailing slash 없이 관리하고, URL resolver에서 앞뒤 공백만 제거한 뒤 경로를 조합합니다.
- 지원 카테고리는 enum으로 관리하고, 호출할 카테고리 key 목록은 작업자가 넘기는 방식으로 처리합니다.
- 여러 카테고리를 동시에 호출하면 동일 기사가 중복으로 반환될 수 있습니다.
- 링크 중복 방지는 MID4-151 범위 밖이므로, 이 티켓에서 제거할지 여부는 추가 결정이 필요합니다.
- `description`이 없는 RSS item이 있으므로 `CollectedArticle.summary`는 `null`을 허용하는 방향으로 처리해야 합니다.
- `content:encoded`는 HTML 조각일 수 있으므로 summary fallback으로 사용할 때 HTML 제거, entity decode, 공백 정규화가 필요합니다.
- `publishedAt`을 만들 수 없는 RSS item은 저장 가능한 기사 후보가 아니므로 제외합니다.
- 제외 item의 별도 저장과 재처리는 MID4-151 범위 밖이며, 저장 정책 확정 시 후속 티켓으로 다룹니다.

## 검증 기준

기본 테스트는 외부 네트워크 호출을 제외합니다.

```powershell
.\gradlew.bat test
.\gradlew.bat --no-daemon clean build
```

실제 외부 endpoint 호출 smoke 테스트는 `@Tag("external")`로 분리합니다.

```powershell
.\gradlew.bat --no-daemon rssExternalTest
.\gradlew.bat --no-daemon naverExternalTest
```

`rssExternalTest`는 한국경제, 조선일보, 연합뉴스TV 대표 RSS에서 실제 기사 목록이 반환되는지 확인합니다. `naverExternalTest`는 NAVER 인증 환경변수가 없으면 로컬 `.env.dev`를 읽고, 인증값이 없으면 테스트를 skip합니다.

## 미결 항목

- 카테고리 간 중복 기사 처리 위치 확정
- RSS 수집 제외 item 또는 실패건 저장 정책 확정
