package com.ssafy.enjoytrip.model.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

import com.ssafy.enjoytrip.model.dto.News;

class NewsServiceTest {

    private final NewsService service = NewsService.getInstance();

    private List<News> parse(String xml) throws Exception {
        return service.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)), "순천만");
    }

    @Test
    void parsesGoogleNewsItem() throws Exception {
        List<News> list = parse("""
                <?xml version="1.0" encoding="UTF-8"?><rss version="2.0"><channel>
                <item><title>순천만 갈대 축제 개막 - 광주일보</title><link>https://news.google.com/rss/articles/abc</link>
                <pubDate>Thu, 29 Oct 2026 15:30:00 GMT</pubDate><source url="https://kwangju.co.kr">광주일보</source></item>
                <item><title>스크립트 링크</title><link>javascript:alert(1)</link></item>
                </channel></rss>""");
        assertEquals(1, list.size(), "http(s)가 아닌 링크는 저장하지 않는다");
        News n = list.get(0);
        assertEquals("순천만 갈대 축제 개막", n.getTitle(), "제목 끝의 ' - 언론사'는 떼어 낸다");
        assertEquals("광주일보", n.getSource());
        assertEquals("순천만", n.getKeyword());
        assertEquals(LocalDateTime.of(2026, 10, 30, 0, 30), n.getPublishedAt(), "GMT → 서울 시각(+9)");
    }

    @Test
    void rejectsDoctypeToBlockXxe() {
        String xxe = """
                <?xml version="1.0"?><!DOCTYPE rss [<!ENTITY x SYSTEM "file:///C:/Windows/win.ini">]>
                <rss><channel><item><title>&x;</title><link>https://a.b/c</link></item></channel></rss>""";
        assertThrows(SAXException.class, () -> parse(xxe));
    }

    @Test
    void badPubDateBecomesNull() {
        assertNull(NewsService.parsePubDate("어제 오후"));
    }

    @Test
    void keywordInputIsCleanedAndLimited() {
        assertEquals(List.of("여수", "순천만"), service.parseKeywords(" 여수 \r\n\r\n순천만, 여수 "));
        assertEquals(NewsService.DEFAULT_KEYWORDS, service.parseKeywords("  "));
        assertThrows(ValidationException.class, () -> service.parseKeywords("a,b,c,d,e,f,g,h,i,j,k"));
    }
}
