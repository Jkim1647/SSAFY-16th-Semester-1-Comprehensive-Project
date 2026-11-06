package com.ssafy.enjoytrip.model.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import com.ssafy.enjoytrip.model.dao.JdbcNewsDao;
import com.ssafy.enjoytrip.model.dao.NewsDao;
import com.ssafy.enjoytrip.model.dto.News;
import com.ssafy.enjoytrip.model.dto.Page;
import com.ssafy.enjoytrip.model.dto.SearchCondition;

/**
 * F106 관광지 관련 뉴스 크롤링.
 * 키 없이 쓸 수 있는 Google News RSS 검색 결과(XML)를 받아 제목·링크·언론사·발행 시각만 뽑아 news 테이블에 저장한다.
 * 같은 기사(link)는 INSERT IGNORE 로 건너뛰므로 여러 번 수집해도 중복되지 않는다.
 */
public class NewsService {

    public static final List<String> DEFAULT_KEYWORDS = List.of("광주 여행", "전남 여행", "여수 관광", "순천만", "전기차 충전소");
    private static final String RSS_URL = "https://news.google.com/rss/search?q=%s&hl=ko&gl=KR&ceid=KR:ko";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
            + " (KHTML, like Gecko) Chrome/130.0 Safari/537.36 EnjoyTripEV/1.0";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final int ITEMS_PER_KEYWORD = 30;
    private static final int MAX_KEYWORDS = 10;
    private static final int KEYWORD_MAX = 50; // news.keyword VARCHAR(50)
    private static final int TITLE_MAX = 300;
    private static final int LINK_MAX = 700;
    private static final int SOURCE_MAX = 100;
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    /** 싱글톤은 위 상수들보다 뒤에 만들어야 생성자에서 TIMEOUT 이 null 이 아니다 (static 초기화는 선언 순서대로). */
    private static final NewsService INSTANCE = new NewsService();

    private final NewsDao dao = JdbcNewsDao.getInstance();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    /** 관리자가 버튼을 연달아 눌러도 수집은 한 번에 하나만 돈다. */
    private final AtomicBoolean crawling = new AtomicBoolean(false);

    private NewsService() {
    }

    public static NewsService getInstance() {
        return INSTANCE;
    }

    public Page<News> search(String keyword, int page, int size) {
        SearchCondition cond = new SearchCondition(Set.of("keyword"), "keyword", keyword, page, size);
        return new Page<>(cond, dao.count(cond), dao.search(cond));
    }

    public Map<String, Integer> keywordCounts() {
        return dao.countByKeyword();
    }

    /** 관리자 입력(줄바꿈·쉼표 구분)을 검색어 목록으로. 비어 있으면 기본 검색어를 쓴다. */
    public List<String> parseKeywords(String text) {
        Set<String> set = new LinkedHashSet<>();
        if (text != null) {
            Arrays.stream(text.split("[\\r\\n,]+")).map(String::strip).filter(s -> !s.isEmpty()).forEach(set::add);
        }
        if (set.isEmpty()) {
            return DEFAULT_KEYWORDS;
        }
        if (set.size() > MAX_KEYWORDS) {
            throw new ValidationException("검색어는 한 번에 " + MAX_KEYWORDS + "개까지 수집할 수 있습니다.");
        }
        for (String k : set) {
            if (k.length() > KEYWORD_MAX) {
                throw new ValidationException("검색어는 " + KEYWORD_MAX + "자 이하로 입력하세요.");
            }
        }
        return new ArrayList<>(set);
    }

    /** 검색어마다 RSS 를 받아 저장하고, 검색어별로 가져온 기사 수·새로 저장된 수를 돌려준다. 한 검색어가 실패해도 나머지는 계속한다. */
    public List<CrawlResult> crawl(List<String> keywords) {
        if (!crawling.compareAndSet(false, true)) {
            throw new ValidationException("이미 수집 중입니다. 잠시 후 다시 시도하세요.");
        }
        try {
            List<CrawlResult> results = new ArrayList<>();
            for (String keyword : keywords) {
                try {
                    List<News> items = fetch(keyword);
                    results.add(new CrawlResult(keyword, items.size(), dao.insertIgnore(items), null));
                } catch (IOException | SAXException e) {
                    System.err.println("[news] '" + keyword + "' 수집 실패: " + e);
                    results.add(new CrawlResult(keyword, 0, 0, "RSS를 가져오지 못했습니다 (" + e.getClass().getSimpleName() + ")"));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    results.add(new CrawlResult(keyword, 0, 0, "수집이 중단되었습니다."));
                    break;
                }
            }
            return results;
        } finally {
            crawling.set(false);
        }
    }

    private List<News> fetch(String keyword) throws IOException, InterruptedException, SAXException {
        URI uri = URI.create(String.format(RSS_URL, URLEncoder.encode(keyword, StandardCharsets.UTF_8)));
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(TIMEOUT)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/rss+xml, application/xml;q=0.9, */*;q=0.8")
                .GET()
                .build();
        HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream body = response.body()) {
            if (response.statusCode() != 200) {
                throw new IOException("HTTP " + response.statusCode());
            }
            return parse(body, keyword);
        }
    }

    /** RSS 2.0 의 <item> 에서 title / link / source / pubDate 를 뽑는다. */
    List<News> parse(InputStream xml, String keyword) throws IOException, SAXException {
        Document doc = newSecureBuilder().parse(xml);
        NodeList items = doc.getElementsByTagName("item");
        List<News> list = new ArrayList<>();
        for (int i = 0; i < items.getLength() && list.size() < ITEMS_PER_KEYWORD; i++) {
            Element item = (Element) items.item(i);
            String link = text(item, "link");
            String title = text(item, "title");
            // 링크가 없거나 http(s)가 아니거나 컬럼보다 길면 저장하지 않는다
            if (title == null || !News.isHttpUrl(link) || link.length() > LINK_MAX) {
                continue;
            }
            String source = text(item, "source");
            // Google News 제목은 "기사 제목 - 언론사" 형식이라 언론사 부분은 따로 보관한다
            if (source != null && title.endsWith(" - " + source)) {
                title = title.substring(0, title.length() - source.length() - 3).strip();
            }
            list.add(new News(keyword, cut(title, TITLE_MAX), link.strip(), cut(source, SOURCE_MAX),
                    parsePubDate(text(item, "pubDate"))));
        }
        return list;
    }

    /**
     * 외부에서 받은 XML 이므로 DOCTYPE·외부 엔티티를 막아 XXE(파일 읽기, 내부망 요청)와 엔티티 폭탄을 차단한다.
     */
    private static DocumentBuilder newSecureBuilder() {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            factory.setNamespaceAware(false);
            return factory.newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            throw new IllegalStateException("XML 파서 설정 실패", e);
        }
    }

    private static String text(Element parent, String tag) {
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n.getNodeType() == Node.ELEMENT_NODE && tag.equals(n.getNodeName())) {
                String t = n.getTextContent();
                return t == null || t.isBlank() ? null : t.strip();
            }
        }
        return null;
    }

    /** RFC 1123 (예: "Thu, 30 Oct 2026 01:00:00 GMT") → 서울 시각. 형식이 다르면 null (정렬은 수집 시각으로). */
    static LocalDateTime parsePubDate(String value) {
        if (value == null) {
            return null;
        }
        try {
            return ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).withZoneSameInstant(SEOUL).toLocalDateTime();
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static String cut(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }

    /** 검색어 하나의 수집 결과. error 가 null 이면 성공. */
    public static class CrawlResult implements Serializable {

        private static final long serialVersionUID = 1L;
        private final String keyword;
        private final int fetched;
        private final int saved;
        private final String error;

        public CrawlResult(String keyword, int fetched, int saved, String error) {
            this.keyword = keyword;
            this.fetched = fetched;
            this.saved = saved;
            this.error = error;
        }

        public boolean isSuccess() {
            return error == null;
        }

        public String getKeyword() {
            return keyword;
        }

        public int getFetched() {
            return fetched;
        }

        public int getSaved() {
            return saved;
        }

        public String getError() {
            return error;
        }
    }
}
