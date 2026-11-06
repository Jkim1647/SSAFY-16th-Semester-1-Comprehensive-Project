package com.ssafy.enjoytrip.listener;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import com.ssafy.enjoytrip.util.Csv;

/** One-time enrichment of unchanged v1 sample posts; never matches ordinary members. */
final class BoardSampleDetails {
    private BoardSampleDetails() { }
    static void seed(Connection con) throws SQLException {
        if (!con.getAutoCommit()) throw new SQLException("독립 트랜잭션이 필요합니다.");
        Map<String,String> originals = new HashMap<>();
        for (var row : Csv.readResource("data/board-samples.csv")) originals.put(row.get("title"),row.get("content"));
        var details = Csv.readResource("data/board-sample-details.csv");
        var comments = Csv.readResource("data/board-sample-comments.csv");
        con.setAutoCommit(false);
        try {
            try (PreparedStatement p = con.prepareStatement("INSERT IGNORE INTO board_seed_state(seed_key) VALUES('board-samples-details-v2')")) {
                if (p.executeUpdate() == 0) { con.commit(); return; }
            }
            Map<String,Long> posts = new HashMap<>();
            for (var row : details) {
                try (PreparedStatement p = con.prepareStatement("SELECT b.board_id FROM boards b JOIN members m ON m.member_id=b.member_id WHERE m.email LIKE 'board-sample-%@example.invalid' AND m.password_hash='!' AND b.board_type='FREE' AND b.title=? AND b.content=? FOR UPDATE")) {
                    p.setString(1,row.get("source_title")); p.setString(2,originals.get(row.get("source_title")));
                    try (ResultSet r=p.executeQuery()) { if (r.next()) posts.put(row.get("source_title"),r.getLong(1)); }
                }
            }
            if (posts.isEmpty()) { con.commit(); return; }
            Map<String,Long> authors=new LinkedHashMap<>();
            for (var row:details) {
                String name=row.get("author");
                if (!authors.containsKey(name)) authors.put(name,insert(con,"INSERT INTO members(email,name,password_hash,role) VALUES(?,?,'!','USER')","board-detail-"+UUID.randomUUID()+"@example.invalid",name));
            }
            List<Long> people=new ArrayList<>(authors.values());
            Map<String,LocalDateTime> dates=new HashMap<>();
            for (var row:details) {
                Long id=posts.get(row.get("source_title")); if (id==null) continue;
                LocalDateTime date=LocalDateTime.parse(row.get("created_at").replace(' ','T'));
                int views=Integer.parseInt(row.get("view_count")), likes=Integer.parseInt(row.get("like_count"));
                if(views<0||likes<0||likes>people.size()||row.get("title").length()>200||row.get("content").length()>10000) throw new SQLException("잘못된 상세 샘플 데이터");
                update(con,"UPDATE boards SET member_id=?,title=?,content=?,view_count=GREATEST(view_count,?),created_at=?,updated_at=? WHERE board_id=?",authors.get(row.get("author")),row.get("title"),row.get("content"),views,date,date,id);
                dates.put(row.get("source_title"),date);
                for(int i=0;i<likes;i++) update(con,"INSERT INTO board_likes(board_id,member_id,created_at) VALUES(?,?,?) ON DUPLICATE KEY UPDATE board_id=board_id",id,people.get(i),date.plusHours(1));
            }
            for(var row:comments) {
                Long id=posts.get(row.get("source_title")); if(id==null) continue;
                LocalDateTime date=LocalDateTime.parse(row.get("created_at").replace(' ','T'));
                if(date.isBefore(dates.get(row.get("source_title")))||row.get("content").isBlank()||row.get("content").length()>2000||!authors.containsKey(row.get("author"))) throw new SQLException("잘못된 샘플 댓글");
                update(con,"INSERT INTO board_comments(board_id,member_id,content,created_at,updated_at) VALUES(?,?,?,?,?)",id,authors.get(row.get("author")),row.get("content"),date,date);
            }
            con.commit();
        } catch(SQLException|RuntimeException e) { con.rollback(); throw e; }
        finally { con.setAutoCommit(true); }
    }
    private static long insert(Connection con,String sql,Object... args) throws SQLException {
        try(PreparedStatement p=con.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS)) {
            for(int i=0;i<args.length;i++) p.setObject(i+1,args[i]); p.executeUpdate();
            try(ResultSet r=p.getGeneratedKeys()) { if(!r.next()) throw new SQLException("샘플 ID 없음"); return r.getLong(1); }
        }
    }
    private static void update(Connection con,String sql,Object... args) throws SQLException {
        try(PreparedStatement p=con.prepareStatement(sql)) { for(int i=0;i<args.length;i++) p.setObject(i+1,args[i]); p.executeUpdate(); }
    }
}
