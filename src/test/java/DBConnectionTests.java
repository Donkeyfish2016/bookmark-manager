import com.bookmark.db.DatabaseMgr;
import org.junit.jupiter.api.*;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.*;

class DBConnectionTests {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @BeforeAll
    static void beforeAll() {
        DatabaseMgr.initialize();
    }

    @AfterAll
    static void afterAll() {
        try (Connection conn = DatabaseMgr.getConnection()) {
            if (conn != null && !conn.isClosed()) {
                conn.close();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Test
    void testInsertReadDelete() {
        try (Connection conn = DatabaseMgr.getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.executeUpdate("DELETE FROM bookmarks");

            String now = LocalDateTime.now().format(FORMATTER);

            stmt.executeUpdate("INSERT INTO bookmarks (url, title, icon, category, add_date) VALUES " +
                    "('https://example.com', 'Example', 'https://example.com/favicon.ico', 'Dev', '" + now + "')");
            stmt.executeUpdate("INSERT INTO bookmarks (url, title, icon, category, add_date) VALUES " +
                    "('https://github.com', 'GitHub', 'https://github.com/favicon.ico', 'Dev/Tools', '" + now + "')");
            stmt.executeUpdate("INSERT INTO bookmarks (url, title, icon, category, add_date) VALUES " +
                    "('https://stackoverflow.com', 'Stack Overflow', null, 'Dev/QA', '" + now + "')");

            System.out.println("=== After Insert ===");
            try (ResultSet rs = stmt.executeQuery("SELECT id, url, title, icon, category, add_date FROM bookmarks")) {
                while (rs.next()) {
                    System.out.printf("id=%d, url=%s, title=%s, icon=%s, category=%s, add_date=%s%n",
                            rs.getInt("id"),
                            rs.getString("url"),
                            rs.getString("title"),
                            rs.getString("icon"),
                            rs.getString("category"),
                            rs.getString("add_date"));
                }
            }

            int deleted = stmt.executeUpdate("DELETE FROM bookmarks WHERE id IN (1, 2, 3)");
            assertEquals(3, deleted, "Should delete 3 records");

            System.out.println("=== After Delete ===");
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) AS cnt FROM bookmarks")) {
                rs.next();
                int count = rs.getInt("cnt");
                System.out.println("Remaining records: " + count);
                assertEquals(0, count, "Should have 0 records after deletion");
            }

        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
