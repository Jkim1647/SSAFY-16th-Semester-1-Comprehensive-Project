package com.ssafy.enjoytrip.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import com.ssafy.enjoytrip.config.AppConfig;

/**
 * JDBC 연결 관리 (수업 DBUtil 패턴). DAO 는 여기서 Connection 을 얻고, try-with-resources 로 반납한다.
 * 트랜잭션이 필요한 서비스는 같은 Connection 을 DAO 메서드에 넘겨서 하나로 묶는다.
 */
public final class DBUtil {

    private static final String URL = AppConfig.get("db.url");
    private static final String USER = AppConfig.get("db.user");
    private static final String PASSWORD = AppConfig.get("db.password");

    static {
        try {
            // JDBC 4 이후 자동 로딩되지만, Tomcat 웹앱 클래스로더에서는 명시적으로 등록해야 확실하다.
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private DBUtil() {
    }

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }

    public static void rollback(Connection con) {
        if (con == null) {
            return;
        }
        try {
            con.rollback();
        } catch (SQLException e) {
            System.err.println("rollback 실패: " + e.getMessage());
        }
    }
}
