package com.ssafy.enjoytrip.model.dao;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import com.ssafy.enjoytrip.model.dto.*;

/**
 * Persistence contract for community queries and transaction-scoped mutations.
 */
public interface BoardCommunityDao {
    @FunctionalInterface
    interface Work<T> {
        T run(Connection connection) throws SQLException;
    }
    <T> T read(Work<T> work);
    <T> T transaction(Work<T> work);
    int update(Connection c, String sql, Object... args) throws SQLException;
    long insert(Connection c, String sql, Object... args) throws SQLException;
    BoardEntry get(Connection c, long id, long viewer, boolean lock) throws SQLException;
    Page<BoardEntry> search(Connection c, BoardQuery q, long viewer, boolean admin) throws SQLException;
    List<BoardEntry> pinned(Connection c, long viewer) throws SQLException;
    void ensureSettings(Connection c, long id) throws SQLException;
    void relation(Connection c, String table, long id, long viewer, boolean active) throws SQLException;
    BoardComment comment(Connection c, long boardId, long id, boolean lock) throws SQLException;
    Page<BoardComment> comments(Connection c, long boardId, long viewer, boolean admin, int page, boolean mine)
            throws SQLException;
    List<BoardComment> allComments(Connection c, long boardId, boolean admin) throws SQLException;
    int commentPage(Connection c, long boardId, long cid, boolean admin) throws SQLException;
    Page<Map<String, Object>> reports(Connection c, int page) throws SQLException;
    List<Map<String, Object>> history(Connection c, long id) throws SQLException;
}
