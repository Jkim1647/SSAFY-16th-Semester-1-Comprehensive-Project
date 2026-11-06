package com.ssafy.enjoytrip.model.service;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import com.ssafy.enjoytrip.config.AppConfig;
import com.ssafy.enjoytrip.model.dao.BoardCommunityDao;
import com.ssafy.enjoytrip.model.dao.JdbcBoardCommunityDao;
import com.ssafy.enjoytrip.model.dto.*;

/** Real MySQL, isolated schema. Never writes to the application schema. */
class BoardCommunityServiceTest {
    static String schema, url;
    static boolean schemaReady;
    static BoardCommunityDao dao;
    static BoardCommunityService service;
    Member writer = member(1, false), other = member(2, false), admin = member(3, true);
    static Member member(long id, boolean admin) {
        Member m = new Member("u" + id + "@test.kr", "회원" + id);
        m.setMemberId(id);
        m.setRole(admin ? Member.ROLE_ADMIN : Member.ROLE_USER);
        return m;
    }
    static Connection connection() throws SQLException {
        return DriverManager.getConnection(url, AppConfig.get("db.user"), AppConfig.get("db.password"));
    }
    @BeforeAll
    static void setup() throws Exception {
        schema = "enjoytrip_board_test_" + UUID.randomUUID().toString().replace("-", "");
        String base = AppConfig.get("db.url");
        url = base.replaceFirst("/[^/?]+(?=\\?|$)", "/" + schema);
        try (Connection c = connection(); Statement s = c.createStatement()) {
            schemaReady = true;
            for (String resource : List.of("sql/01-core.sql", "sql/40-board.sql", "sql/45-board-community.sql")) {
                try (var in = BoardCommunityServiceTest.class.getClassLoader().getResourceAsStream(resource)) {
                    assertNotNull(in, resource);
                    for (String sql : new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                            .replaceAll("(?m)--.*$", "").split(";"))
                        if (!sql.isBlank())
                            s.execute(sql);
                }
            }
            s.executeUpdate("INSERT INTO members(member_id,email,name,password_hash,role) VALUES "
                    + "(1,'writer@test.kr','작성자','test','USER'),(2,'other@test.kr','다른회원','test','USER'),(3,'admin@test.kr','관리자','test','ADMIN')");
        } catch (SQLException e) {
            if (e.getSQLState() != null && e.getSQLState().startsWith("08")) {
                org.junit.jupiter.api.Assumptions.assumeTrue(false, "MySQL unavailable: " + e.getMessage());
            }
            throw e;
        }
        dao = new JdbcBoardCommunityDao(BoardCommunityServiceTest::connection);
        service = new BoardCommunityService(dao);
    }
    @AfterAll
    static void cleanup() throws Exception {
        if (schemaReady && schema != null && schema.matches("enjoytrip_board_test_[a-f0-9]{32}"))
            try (Connection c = connection(); Statement s = c.createStatement()) {
                s.execute("DROP DATABASE " + schema);
            }
    }
    @BeforeEach
    void reset() throws Exception {
        try (Connection c = connection(); Statement s = c.createStatement()) {
            s.executeUpdate("DELETE FROM boards");
        }
    }
    long post() {
        return service.write(writer, "FREE", "제목", "본문", "TIP");
    }
    BoardQuery query(String type, String category, String sort, String activity) {
        return new BoardQuery(type, category, sort, activity, "", "", 1);
    }
    @Test
    void legacyRowsAndNoticeAreInOneList() throws Exception {
        long id = post();
        service.write(admin, "NOTICE", "공지", "내용", "GENERAL");
        try (Connection c = connection(); Statement s = c.createStatement()) {
            s.executeUpdate("DELETE FROM board_settings WHERE board_id=" + id);
        }
        var page = service.search(null, query("", "", "", ""));
        assertEquals(2, page.getTotalItems());
        assertEquals("GENERAL", service.get(id, null).getCategory());
    }
    @Test
    void likesAndBookmarksAreIndependentAndIdempotent() {
        long id = post();
        service.react(other, id, "like", true);
        service.react(other, id, "like", true);
        service.react(other, id, "bookmark", true);
        var entry = service.get(id, other);
        assertEquals(1, entry.getLikeCount());
        assertTrue(entry.isLiked());
        assertTrue(entry.isBookmarked());
        service.react(other, id, "like", false);
        service.react(other, id, "like", false);
        assertEquals(0, service.get(id, other).getLikeCount());
        assertTrue(service.get(id, other).isBookmarked());
    }
    @Test
    void concurrentLikesNeverDuplicateOrLoseOtherMembers() throws Exception {
        long id = post();
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<?>> tasks = new ArrayList<>();
            for (int i = 0; i < 24; i++)
                tasks.add(pool.submit(() -> service.react(other, id, "like", true)));
            for (var task : tasks)
                task.get(10, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
        service.react(writer, id, "like", true);
        assertEquals(2, service.get(id, other).getLikeCount());
    }
    @Test
    void anonymousChangesAndForgedOwnershipAreRejected() {
        long id = post();
        assertThrows(BoardCommunityService.Problem.class, () -> service.react(null, id, "like", true));
        assertThrows(BoardCommunityService.Problem.class, () -> service.modify(other, id, "변조", "내용", "GENERAL"));
        assertThrows(BoardCommunityService.Problem.class, () -> service.write(writer, "NOTICE", "공지", "내용", "GENERAL"));
        assertThrows(BoardCommunityService.Problem.class, () -> service.search(null, query("", "", "", "bookmarks")));
    }
    @Test
    void privateActivitiesNeverIncludeAnotherMembersRelations() {
        long id = post();
        service.react(other, id, "bookmark", true);
        assertEquals(0, service.search(writer, query("", "", "", "bookmarks")).getTotalItems());
        assertEquals(1, service.search(other, query("", "", "", "bookmarks")).getTotalItems());
        assertEquals(1, service.search(writer, query("", "", "", "posts")).getTotalItems());
    }
    @Test
    void commentOwnershipAndActivities() {
        long id = post();
        long cid = service.addComment(other, id, "댓글");
        assertEquals(1, service.comments(id, null, 1).getTotalItems());
        assertThrows(BoardCommunityService.Problem.class, () -> service.editComment(writer, id, cid, "변조"));
        service.editComment(other, id, cid, "수정");
        assertEquals("수정", service.comments(id, null, 1).getList().get(0).getContent());
        assertEquals(1, service.myComments(other, 1).getTotalItems());
        service.deleteComment(admin, id, cid);
        assertEquals(0, service.comments(id, null, 1).getTotalItems());
    }
    @Test
    void hiddenPostIsExcludedAndCannotBeReactedToOrRead() {
        long id = post();
        service.react(other, id, "bookmark", true);
        assertThrows(BoardCommunityService.Problem.class, () -> service.moderate(other, id, 0, true, "사유"));
        service.moderate(admin, id, 0, true, "운영정책 위반");
        assertThrows(BoardCommunityService.Problem.class, () -> service.get(id, writer));
        assertThrows(BoardCommunityService.Problem.class, () -> service.react(writer, id, "like", true));
        assertEquals(0, service.search(other, query("", "", "", "bookmarks")).getTotalItems());
        assertTrue(service.get(id, admin).isHidden());
        service.moderate(admin, id, 0, false, "검토 후 복구");
        assertNotNull(service.get(id, null));
    }
    @Test
    void hiddenCommentIsPrivateAndReportedOnlyOnce() {
        long id = post(), cid = service.addComment(other, id, "댓글");
        service.report(writer, id, cid, "광고");
        service.report(writer, id, cid, "광고");
        assertEquals(1, service.reports(admin, 1).getTotalItems());
        assertThrows(BoardCommunityService.Problem.class, () -> service.reports(other, 1));
        service.moderate(admin, id, cid, true, "광고 댓글");
        assertEquals(0, service.comments(id, writer, 1).getTotalItems());
        assertEquals(1, service.comments(id, admin, 1).getTotalItems());
    }
    @Test
    void reportCanBeResolvedWithAuditReason() {
        long id = post();
        service.report(other, id, service.addComment(writer, id, "검토할 댓글"), "신고 이유");
        long rid = ((Number) service.reports(admin, 1).getList().get(0).get("reportId")).longValue();
        service.resolveReport(admin, rid, "검토 완료");
        assertEquals("RESOLVED", service.reports(admin, 1).getList().get(0).get("status"));
    }
    @Test
    void pinsRespectTypePeriodOrderAndDoNotDuplicateRows() {
        long a = service.write(admin, "NOTICE", "A", "공지", "GENERAL");
        long b = service.write(admin, "NOTICE", "B", "공지", "GENERAL");
        service.pin(admin, a, true, 2, null, null);
        service.pin(admin, b, true, 1, null, null);
        assertEquals(b, service.pinned(null, query("", "", "", "")).get(0).getBoardId());
        assertEquals(0, service.search(null, query("", "", "", "")).getTotalItems());
        service.pin(admin, b, true, 1, LocalDateTime.now().plusDays(1), null);
        assertEquals(1, service.pinned(null, query("", "", "", "")).size());
        assertEquals(1, service.search(null, query("", "", "", "")).getTotalItems());
        assertThrows(BoardCommunityService.Problem.class, () -> service.pin(admin, post(), true, 1, null, null));
    }
    @Test
    void searchCategorySortAndStateArePreserved() {
        long id = post();
        service.write(writer, "FREE", "다른 제목", "본문", "REVIEW");
        service.react(other, id, "like", true);
        BoardQuery q = new BoardQuery("free", "TIP", "likes", "", "title", "제목 & 여행", 2);
        assertTrue(q.getQueryString().contains("word=%EC%A0%9C%EB%AA%A9+%26+%EC%97%AC%ED%96%89"));
        assertTrue(q.getQueryString().contains("page=2"));
        assertEquals(1, service.search(null, query("", "TIP", "likes", "")).getTotalItems());
        assertEquals(id, service.search(null, query("", "", "likes", "")).getList().get(0).getBoardId());
    }
    @Test
    void deletingPostCascadesAllRelations() throws Exception {
        long id = post();
        long cid = service.addComment(other, id, "댓글");
        service.react(other, id, "like", true);
        service.react(other, id, "bookmark", true);
        service.report(other, id, cid, "신고");
        service.delete(writer, id);
        try (Connection c = connection(); Statement s = c.createStatement()) {
            for (String table : List.of("board_settings", "board_likes", "board_bookmarks", "board_comments",
                    "board_reports"))
                try (ResultSet r = s.executeQuery("SELECT COUNT(*) FROM " + table)) {
                    r.next();
                    assertEquals(0, r.getInt(1), table);
                }
        }
    }
    @Test
    void validationRejectsEmptyOversizedAndInvalidPolicyInputs() {
        long id = post();
        assertThrows(BoardCommunityService.Problem.class, () -> service.addComment(other, id, " "));
        assertThrows(BoardCommunityService.Problem.class, () -> service.addComment(other, id, "가".repeat(2001)));
        assertThrows(BoardCommunityService.Problem.class, () -> service.report(other, id, 0, " "));
        assertThrows(BoardCommunityService.Problem.class, () -> service.moderate(admin, id, 0, true, " "));
        assertThrows(BoardCommunityService.Problem.class, () -> service.write(writer, "BAD", "제목", "내용", "GENERAL"));
        assertThrows(BoardCommunityService.Problem.class, () -> service.react(other, id, "arbitrary_table", true));
    }
    @Test
    void noticePinBoundaryAndSearchVisibility() throws Exception {
        long id = service.write(admin, "NOTICE", "기간 공지", "내용", "GENERAL");
        service.pin(admin, id, true, 0, null, LocalDateTime.now().minusSeconds(1));
        assertEquals(0, service.pinned(null, query("", "", "", "")).size());
        assertEquals(1, service.search(null, query("", "", "", "")).getTotalItems());
        service.pin(admin, id, true, 0, LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1));
        BoardQuery search = new BoardQuery("", "", "latest", "", "title", "기간", 1);
        assertEquals(1, service.search(null, search).getTotalItems());
        assertTrue(service.pinned(null, search).isEmpty());
        assertEquals(1, service.search(null, query("notice", "", "", "")).getTotalItems());
        assertThrows(BoardCommunityService.Problem.class,
                () -> service.pin(admin, id, true, 1, LocalDateTime.now(), LocalDateTime.now().minusDays(1)));
    }
    @Test
    void commentsMustBelongToRequestedBoard() {
        long a = post(), b = post(), cid = service.addComment(other, a, "원래 댓글");
        assertThrows(BoardCommunityService.Problem.class, () -> service.editComment(other, b, cid, "변조"));
        assertThrows(BoardCommunityService.Problem.class, () -> service.report(writer, b, cid, "허위 대상"));
        assertThrows(BoardCommunityService.Problem.class, () -> service.moderate(admin, b, cid, true, "다른 글 댓글"));
        assertEquals("원래 댓글", service.comments(a, null, 1).getList().get(0).getContent());
    }
    @Test
    void deletingMemberCleansOthersBookmarksOfTheirPosts() throws Exception {
        long id = post();
        service.react(other, id, "bookmark", true);
        service.react(other, id, "like", true);
        service.addComment(other, id, "댓글");
        try (Connection c = connection()) {
            c.setAutoCommit(false);
            try (Statement s = c.createStatement()) {
                s.executeUpdate("DELETE FROM members WHERE member_id=1");
                try (ResultSet r = s.executeQuery("SELECT COUNT(*) FROM board_bookmarks")) {
                    r.next();
                    assertEquals(0, r.getInt(1));
                }
                try (ResultSet r = s.executeQuery("SELECT COUNT(*) FROM board_comments")) {
                    r.next();
                    assertEquals(0, r.getInt(1));
                }
            } finally {
                c.rollback();
            }
        }
    }
    @Test
    void privateCommentActivityHidesModeratedContentEvenForItsAuthor() {
        long id = post(), cid = service.addComment(other, id, "숨김 댓글");
        service.moderate(admin, id, cid, true, "사유");
        assertEquals(0, service.myComments(other, 1).getTotalItems());
    }
    @Test
    void invalidQueryValuesNeverBecomeSqlOrReturnUrl() {
        BoardQuery q = new BoardQuery("javascript:bad", "<script>", "like_count;DROP TABLE boards", "member=2", "bad",
                "<script>", Integer.MAX_VALUE);
        assertEquals("", q.getType());
        assertEquals("", q.getCategory());
        assertEquals("latest", q.getSort());
        assertEquals("", q.getActivity());
        assertFalse(q.getQueryString().contains("<"));
        assertFalse(q.getQueryString().contains("DROP"));
        assertTrue(q.getOffset() >= 0);
    }

    @Test
    void koreanPinScheduleWorksWhenDatabaseSessionUsesUtc() {
        BoardCommunityService utcService = new BoardCommunityService(new JdbcBoardCommunityDao(() -> {
            Connection c = connection();
            try (Statement st = c.createStatement()) {
                st.execute("SET time_zone='+00:00'");
            }
            return c;
        }));
        long id = utcService.write(admin, "NOTICE", "한국 시간 공지", "내용", "GENERAL");
        LocalDateTime now = LocalDateTime.now(java.time.ZoneId.of("Asia/Seoul"));
        utcService.pin(admin, id, true, 1, now.minusMinutes(1), now.plusMinutes(1));
        assertEquals(1, utcService.pinned(null, query("", "", "", "")).size());
    }

    @Test
    void deletingWhileLikesArriveLeavesNoOrphans() throws Exception {
        long id = post();
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<?>> tasks = new ArrayList<>();
        try {
            for (int i = 0; i < 16; i++)
                tasks.add(pool.submit(() -> {
                    go.await();
                    try {
                        service.react(other, id, "like", true);
                    } catch (BoardCommunityService.Problem p) {
                        assertEquals(404, p.getStatus());
                    }
                    return null;
                }));
            tasks.add(pool.submit(() -> {
                go.await();
                service.delete(writer, id);
                return null;
            }));
            go.countDown();
            for (var task : tasks)
                task.get(10, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
        assertThrows(BoardCommunityService.Problem.class, () -> service.get(id, writer));
        try (Connection c = connection();
                Statement st = c.createStatement();
                ResultSet r = st.executeQuery("SELECT COUNT(*) FROM board_likes")) {
            r.next();
            assertEquals(0, r.getInt(1));
        }
    }
    @Test
    void listingManyRowsUsesABoundedNumberOfDatabaseRoundTrips() {
        for (int i = 0; i < 15; i++)
            post();
        java.util.concurrent.atomic.AtomicInteger statements = new java.util.concurrent.atomic.AtomicInteger();
        BoardCommunityService measured = new BoardCommunityService(new JdbcBoardCommunityDao(() -> {
            Connection real = connection();
            return (Connection) java.lang.reflect.Proxy.newProxyInstance(Connection.class.getClassLoader(),
                    new Class<?>[]{Connection.class}, (proxy, method, args) -> {
                        if (method.getName().equals("prepareStatement"))
                            statements.incrementAndGet();
                        try {
                            return method.invoke(real, args);
                        } catch (java.lang.reflect.InvocationTargetException e) {
                            throw e.getCause();
                        }
                    });
        }));
        assertEquals(10, measured.search(other, query("", "", "", "")).getList().size());
        assertTrue(statements.get() <= 3, "A page must not issue separate queries for each post");
    }

    @Test
    @org.junit.jupiter.api.condition.EnabledIfSystemProperty(named = "board.load", matches = "true")
    void measureListQueriesWithTenThousandPostsAndEightConcurrentReaders() throws Exception {
        try (Connection c = connection();
                PreparedStatement p = c.prepareStatement(
                        "INSERT INTO boards(board_type,member_id,title,content) VALUES('FREE',1,?,'본문')")) {
            for (int i = 0; i < 10000; i++) {
                p.setString(1, "부하 검증 " + i);
                p.addBatch();
                if (i % 1000 == 999)
                    p.executeBatch();
            }
        }
        service.search(other, query("", "", "latest", ""));
        ExecutorService pool = Executors.newFixedThreadPool(8);
        List<Future<Long>> tasks = new ArrayList<>();
        try {
            for (int i = 0; i < 32; i++) {
                String sort = List.of("latest", "views", "likes").get(i % 3);
                tasks.add(pool.submit(() -> {
                    long start = System.nanoTime();
                    assertEquals(10, service.search(other, query("", "", sort, "")).getList().size());
                    return (System.nanoTime() - start) / 1000000;
                }));
            }
            List<Long> times = new ArrayList<>();
            for (var task : tasks)
                times.add(task.get(30, TimeUnit.SECONDS));
            Collections.sort(times);
            System.out.println(
                    "BOARD_LOAD posts=10000 readers=8 requests=32 p95Ms=" + times.get(30) + " maxMs=" + times.get(31));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void adminActivityLinksCountHiddenCommentsShownInAdminDetail() {
        long id = post();
        for (int i = 0; i < 20; i++) {
            long cid = service.addComment(other, id, "숨길 댓글");
            service.moderate(admin, id, cid, true, "숨김 사유");
        }
        long cid = service.addComment(admin, id, "관리자 댓글");
        var mine = service.myComments(admin, 1);
        assertEquals(1, mine.getTotalItems());
        assertEquals(cid, mine.getList().get(0).getCommentId());
        assertEquals(2, mine.getList().get(0).getPage());
        assertEquals(cid, service.comments(id, admin, 2).getList().get(0).getCommentId());
    }

    @Test void csvSeedIsAppliedOnceWithoutOverwritingExistingPosts() throws Exception {
        try (Connection c = connection()) {
            try (Statement st = c.createStatement()) { st.executeUpdate("DELETE FROM board_seed_state"); }
            com.ssafy.enjoytrip.listener.BoardSeed.seed(c);
            int initial;
            try (Statement st = c.createStatement(); ResultSet r = st.executeQuery("SELECT COUNT(*) FROM boards")) { r.next(); initial = r.getInt(1); }
            assertTrue(initial >= 10);
            com.ssafy.enjoytrip.listener.BoardSeed.seed(c);
            try (Statement st = c.createStatement(); ResultSet r = st.executeQuery("SELECT COUNT(*) FROM boards")) { r.next(); assertEquals(initial, r.getInt(1)); }
            try (Statement st = c.createStatement()) { st.executeUpdate("DELETE FROM boards"); }
            com.ssafy.enjoytrip.listener.BoardSeed.seed(c);
            try (Statement st = c.createStatement(); ResultSet r = st.executeQuery("SELECT COUNT(*) FROM boards")) { r.next(); assertEquals(0, r.getInt(1)); }
        }
    }

    @Test void csvSeedDoesNotReplaceExistingBoard() throws Exception {
        long id = post();
        try (Connection c = connection(); Statement st = c.createStatement()) {
            st.executeUpdate("DELETE FROM board_seed_state");
            com.ssafy.enjoytrip.listener.BoardSeed.seed(c);
            try (ResultSet r = st.executeQuery("SELECT COUNT(*) FROM boards")) { r.next(); assertEquals(1, r.getInt(1)); }
            assertNotNull(service.get(id, writer));
            st.executeUpdate("DELETE FROM boards");
            com.ssafy.enjoytrip.listener.BoardSeed.seed(c);
            try (ResultSet r = st.executeQuery("SELECT COUNT(*) FROM boards")) { r.next(); assertEquals(0, r.getInt(1)); }
        }
    }

    @Test void postReportingIsRemoved() {
        long id = post();
        assertThrows(BoardCommunityService.Problem.class, () -> service.report(other, id, 0, "신고"));
        assertEquals(0, service.reports(admin, 1).getTotalItems());
    }

    @Test void detailedSamplesHaveAuthorsDatesViewsAndCommentsWithoutTouchingUserPosts() throws Exception {
        try (Connection c = connection(); Statement st = c.createStatement()) {
            st.executeUpdate("DELETE FROM board_seed_state");
            com.ssafy.enjoytrip.listener.BoardSeed.seed(c);
            long real = post();
            com.ssafy.enjoytrip.listener.BoardSeed.seedDetails(c);
            try (ResultSet r=st.executeQuery("SELECT COUNT(DISTINCT member_id), MIN(view_count) FROM boards WHERE board_id<>"+real)) {
                r.next(); assertTrue(r.getInt(1)>3); assertTrue(r.getInt(2)>0);
            }
            int comments;
            try (ResultSet r=st.executeQuery("SELECT COUNT(*) FROM board_comments")) { r.next(); comments=r.getInt(1); assertTrue(comments>=20); }
            try (ResultSet r=st.executeQuery("SELECT COUNT(*) FROM board_comments c JOIN boards b ON b.board_id=c.board_id WHERE c.created_at<b.created_at")) { r.next(); assertEquals(0,r.getInt(1)); }
            com.ssafy.enjoytrip.listener.BoardSeed.seedDetails(c);
            try (ResultSet r=st.executeQuery("SELECT COUNT(*) FROM board_comments")) { r.next(); assertEquals(comments,r.getInt(1)); }
            assertEquals("제목",service.get(real,writer).getTitle());
        }
    }

}
