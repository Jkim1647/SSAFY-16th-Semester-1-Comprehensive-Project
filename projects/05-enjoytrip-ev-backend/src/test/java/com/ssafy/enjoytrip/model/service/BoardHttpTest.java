package com.ssafy.enjoytrip.model.service;

import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.time.Duration;
import java.util.*;
import java.util.regex.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import com.ssafy.enjoytrip.config.AppConfig;

/**
 * Optional end-to-end tests against an installed Tomcat and a temporary MySQL
 * database.
 */
@EnabledIfSystemProperty(named = "board.http", matches = "true")
class BoardHttpTest {
    static Process server;
    static Path base;
    static String root, schema, dbUrl;
    static Client guest, writer, other, admin;
    static final String PASSWORD = "BoardTest123!";
    @BeforeAll
    static void start() throws Exception {
        Path home = Path.of(System.getProperty("board.tomcat", "C:/SSAFY/apache-tomcat-11.0.27"));
        assertTrue(Files.exists(home.resolve("bin/bootstrap.jar")), "Set -Dboard.tomcat to a Tomcat 11 installation");
        base = Files.createTempDirectory("enjoytrip-board-http-");
        for (String dir : List.of("conf", "logs", "temp", "work", "webapps"))
            Files.createDirectories(base.resolve(dir));
        try (var files = Files.list(home.resolve("conf"))) {
            for (Path p : files.toList())
                if (Files.isRegularFile(p))
                    Files.copy(p, base.resolve("conf").resolve(p.getFileName()));
        }
        int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        Path config = base.resolve("conf/server.xml");
        String xml = Files.readString(config).replace("port=\"8080\"", "port=\"" + port + "\"")
                .replace("<Server port=\"8005\"", "<Server port=\"-1\"");
        Files.writeString(config, xml);
        Files.copy(Path.of("target/enjoytrip.war"), base.resolve("webapps/enjoytrip.war"));
        schema = "enjoytrip_board_http_" + UUID.randomUUID().toString().replace("-", "");
        dbUrl = AppConfig.get("db.url").replaceFirst("/[^/?]+(?=\\?|$)", "/" + schema);
        String java = Path.of(System.getProperty("java.home"), "bin/java.exe").toString();
        List<String> cmd = new ArrayList<>(List.of(java, "-Dfile.encoding=UTF-8", "-Ddb.url=" + dbUrl,
                "-Ddb.user=" + AppConfig.get("db.user"),
                "-Ddb.password=" + AppConfig.get("db.password"), "-Dadmin.email=admin@board.test",
                "-Dadmin.password=" + PASSWORD,
                "-Dcatalina.home=" + home, "-Dcatalina.base=" + base, "-Djava.io.tmpdir=" + base.resolve("temp"), "-cp",
                home.resolve("bin/bootstrap.jar") + File.pathSeparator + home.resolve("bin/tomcat-juli.jar"),
                "org.apache.catalina.startup.Bootstrap", "start"));
        server = new ProcessBuilder(cmd).redirectErrorStream(true)
                .redirectOutput(base.resolve("logs/server.log").toFile()).start();
        root = "http://127.0.0.1:" + port + "/enjoytrip";
        guest = new Client();
        boolean ready = false;
        for (int i = 0; i < 100; i++) {
            try {
                if (guest.get("/board").statusCode() == 200) {
                    ready = true;
                    break;
                }
            } catch (IOException ignored) {
            }
            if (!server.isAlive())
                break;
            Thread.sleep(200);
        }
        assertTrue(ready, () -> {
            try {
                return Files.readString(base.resolve("logs/server.log"));
            } catch (IOException e) {
                return e.toString();
            }
        });
        writer = signup("writer@board.test");
        other = signup("other@board.test");
        admin = new Client();
        admin.login("admin@board.test");
    }
    @AfterAll
    static void stop() throws Exception {
        if (server != null) {
            server.destroy();
            if (!server.waitFor(5, java.util.concurrent.TimeUnit.SECONDS))
                server.destroyForcibly().waitFor();
        }
        if (schema != null && schema.matches("enjoytrip_board_http_[a-f0-9]{32}"))
            try (Connection c = DriverManager.getConnection(dbUrl, AppConfig.get("db.user"),
                    AppConfig.get("db.password")); Statement s = c.createStatement()) {
                s.execute("DROP DATABASE " + schema);
            }
        if (base != null)
            System.out.println("HTTP verification logs: " + base.resolve("logs/server.log"));
    }
    static Client signup(String email) throws Exception {
        Client c = new Client();
        c.get("/member?action=regist-form");
        assertEquals(
                302, c
                        .post("/member?action=regist",
                                Map.of("email", email, "name", "테스트", "pass", PASSWORD, "pass2", PASSWORD))
                        .statusCode());
        c.login(email);
        return c;
    }
    static class Client {
        HttpClient http = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                .connectTimeout(Duration.ofSeconds(3)).build();
        String token = "";
        HttpResponse<String> get(String path) throws Exception {
            var r = http.send(
                    HttpRequest.newBuilder(URI.create(root + path)).timeout(Duration.ofSeconds(10)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            Matcher m = Pattern.compile("name=\"csrf-token\" content=\"([^\"]*)\"").matcher(r.body());
            if (m.find())
                token = m.group(1);
            return r;
        }
        HttpResponse<String> post(String path, Map<String, String> fields) throws Exception {
            return post(path, fields, true);
        }
        HttpResponse<String> post(String path, Map<String, String> fields, boolean csrf) throws Exception {
            StringJoiner body = new StringJoiner("&");
            for (var entry : fields.entrySet())
                body.add(enc(entry.getKey()) + "=" + enc(entry.getValue()));
            if (csrf)
                body.add("_csrf=" + enc(token));
            return http.send(
                    HttpRequest.newBuilder(URI.create(root + path)).timeout(Duration.ofSeconds(10))
                            .header("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8")
                            .POST(HttpRequest.BodyPublishers.ofString(body.toString())).build(),
                    HttpResponse.BodyHandlers.ofString());
        }
        void login(String email) throws Exception {
            get("/member?action=login-form");
            assertEquals(302, post("/member?action=login", Map.of("email", email, "pass", PASSWORD)).statusCode());
            assertEquals(200, get("/board").statusCode());
        }
        long write(String type, String title) throws Exception {
            var r = post("/board?action=write", Map.of("boardType", type, "title", title, "content",
                    "본문 <script>alert(1)</script>", "postCategory", "TIP", "state", "category=TIP&sort=likes&page=2"));
            assertEquals(302, r.statusCode(), r.body());
            Matcher m = Pattern.compile("id=(\\d+)").matcher(r.headers().firstValue("location").orElseThrow());
            assertTrue(m.find());
            return Long.parseLong(m.group(1));
        }
    }
    static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
    @Test
    void jspFormsLegacyLinksSearchAndPaginationRender() throws Exception {
        long id = writer.write("FREE", "render-test");
        for (String path : List.of("/board", "/board?type=notice", "/board?type=free", "/board?activity=posts",
                "/board?activity=comments", "/board?activity=likes", "/board?activity=bookmarks",
                "/board?action=write-form", "/board?action=edit-form&id=" + id, "/board?action=detail&id=" + id))
            assertEquals(200, writer.get(path).statusCode(), path);
        String detail = writer.get("/board?action=detail&id=" + id + "&category=TIP&sort=likes&page=2").body();
        assertTrue(detail.contains("&lt;script&gt;"));
        assertFalse(detail.contains("<script>alert(1)</script>"));
        assertTrue(detail.contains("category=TIP&sort=likes"));
        assertTrue(detail.contains("page=2"));
        assertFalse(detail.contains(">자유게시판</a>"));
        assertEquals(200, admin.get("/board?action=reports").statusCode());
        assertEquals(403, other.get("/board?action=reports").statusCode());
    }
    @Test
    void reactionsCsrfOwnershipAndCommentFlows() throws Exception {
        long id = writer.write("FREE", "http-reactions");
        var fields = Map.of("id", "" + id, "kind", "like", "active", "true");
        assertEquals(403, writer.post("/board?action=reaction", fields, false).statusCode());
        var r = writer.post("/board?action=reaction", fields);
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("\"liked\":true"));
        assertEquals(403,
                other.post("/board?action=modify", Map.of("id", "" + id, "title", "변조", "content", "본문")).statusCode());
        assertEquals(302,
                other.post("/board?action=comment-add", Map.of("id", "" + id, "content", "HTTP 댓글")).statusCode());
        assertTrue(guest.get("/board?action=detail&id=" + id).body().contains("HTTP 댓글"));
        assertTrue(other.get("/board?activity=comments").body().contains("HTTP 댓글"));
        assertEquals(400, other.post("/board?action=report", Map.of("id", "" + id, "reason", "신고 이유")).statusCode());
        assertFalse(admin.get("/board?action=reports").body().contains("신고 이유"));
    }
    @Test
    void adminPinHideRestoreAndNoticePermissions() throws Exception {
        long id = admin.write("NOTICE", "HTTP 공지");
        assertEquals(200, admin.get("/board?action=detail&id=" + id).statusCode());
        assertEquals(403,
                writer.post("/board?action=write", Map.of("boardType", "NOTICE", "title", "위조 공지", "content", "본문"))
                        .statusCode());
        assertEquals(302,
                admin.post("/board?action=pin", Map.of("id", "" + id, "pinned", "true", "pinOrder", "1")).statusCode());
        assertTrue(guest.get("/board").body().contains("고정 공지"));
        assertEquals(302,
                admin.post("/board?action=moderate", Map.of("id", "" + id, "hidden", "true", "reason", "검토 필요"))
                        .statusCode());
        assertEquals(404, writer.get("/board?action=detail&id=" + id).statusCode());
        assertEquals(200, admin.get("/board?action=detail&id=" + id).statusCode());
        assertEquals(302,
                admin.post("/board?action=moderate", Map.of("id", "" + id, "hidden", "false", "reason", "검토 완료"))
                        .statusCode());
        assertEquals(200, guest.get("/board?action=detail&id=" + id).statusCode());
    }
    @Test
    void legacyInvalidTypesAndNoticeWritePermissionsArePreserved() throws Exception {
        assertEquals(404, guest.get("/board?type=unexpected").statusCode());
        assertEquals(403, writer.get("/board?action=write-form&type=notice").statusCode());
        assertTrue(admin.get("/board?action=write-form&type=notice").body().contains("value=\"NOTICE\" selected"));
    }
    @Test
    void anonymousAjaxGets401AndResponsesAreTraceable() throws Exception {
        guest.get("/board");
        var r = guest.http.send(HttpRequest.newBuilder(URI.create(root + "/board?action=reaction"))
                .header("Accept", "application/json").header("X-CSRF-Token", guest.token)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString("id=1&kind=like&active=true")).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(401, r.statusCode());
        assertTrue(r.body().contains("error"));
        assertTrue(r.headers().firstValue("X-Request-ID").isPresent());
    }

    @Test
    void malformedModifyNeverTurnsAClientErrorInto500() throws Exception {
        assertEquals(400,
                writer.post("/board?action=modify", Map.of("id", "abc", "title", "제목", "content", "본문")).statusCode());
        assertEquals(404,
                writer.post("/board?action=modify", Map.of("id", "9223372036854775806", "title", " ", "content", "본문"))
                        .statusCode());
    }

    @Test
    void commentCreationAndActivityLinkOpenTheCommentsActualPage() throws Exception {
        long id = writer.write("FREE", "many-comments");
        HttpResponse<String> added = null;
        for (int i = 1; i <= 21; i++)
            added = other.post("/board?action=comment-add", Map.of("id", "" + id, "content", "댓글 " + i));
        assertNotNull(added);
        assertEquals(302, added.statusCode());
        assertFalse(added.headers().firstValue("location").orElseThrow().contains("commentPage="));
        String detail = guest.get("/board?action=detail&id=" + id).body();
        assertTrue(detail.contains("댓글 1"));
        assertTrue(detail.contains("댓글 21"));
        assertFalse(detail.contains("댓글 페이지"));
        assertFalse(detail.contains("댓글을 작성하려면 로그인하세요."));
        String mine = other.get("/board?activity=comments").body();
        assertFalse(mine.contains("commentPage="));
    }

    @Test
    void guestSeesCommentFormWithLoginRedirect() throws Exception {
        long id = writer.write("FREE", "guest-comment-form");
        String html = guest.get("/board?action=detail&id=" + id).body();
        assertTrue(html.contains("id=\"new-comment\""));
        assertTrue(html.contains("data-login-url="));
        assertTrue(html.toLowerCase(java.util.Locale.ROOT).contains("%26id%3d" + id), html);
        assertFalse(html.contains("<summary>게시글 신고</summary>"));
        assertEquals(302, guest.post("/board?action=comment-add", Map.of("id", "" + id, "content", "guest")).statusCode());
    }

}
