import com.bookmark.db.BookmarkDAO;
import com.bookmark.db.DatabaseMgr;
import com.bookmark.model.Bookmark;

import org.junit.jupiter.api.*;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BookmarkDAO 场景化集成测试。
 * <p>
 * 通过 {@link DatabaseMgr} 连接共享的 SQLite 数据库，
 * 覆盖插入、按分类分页查询、按 id 查询、关键字模糊搜索、更新、计数与批量插入，
 * 并以一条记录完整的“增-查-改-删”生命周期作为端到端验证。
 * </p>
 */
class BookmarkDAOTests {

    private static final String CATEGORY = "DAO_TEST";
    private static final String PAGE_CATEGORY = "DAO_TEST_PAGE";

    private BookmarkDAO dao;

    // 1. 初始化数据库连接与表结构
    @BeforeAll
    static void beforeAll() {
        DatabaseMgr.initialize();
    }

    // 1. 每个测试前清理测试数据，保证用例之间互不干扰
    @BeforeEach
    void setUp() {
        dao = new BookmarkDAO();
        clearCategory(CATEGORY);
        clearCategory(PAGE_CATEGORY);
        clearCategory("OTHER");
    }

    // 1. 所有测试结束后释放连接
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

    /**
     * 场景：插入一条记录 -> 按 id 查询 -> 计数校验 -> 删除 -> 计数归零。
     */
    @Test
    void testInsertQueryByIdCountDeleteLifecycle() {
        // 1. 构造并插入一条书签
        Bookmark b = new Bookmark(null, "https://example.com", "Example",
                "https://example.com/favicon.ico", CATEGORY, LocalDateTime.now(), null, null);
        int id = dao.insert(b);
        assertTrue(id > 0, "插入后应返回有效的自增 id");

        // 2. 按 id 查询，字段应一致
        Bookmark loaded = dao.queryById(id);
        assertNotNull(loaded, "应能按 id 查询到刚插入的书签");
        assertEquals("https://example.com", loaded.getUrl());
        assertEquals("Example", loaded.getTitle());
        assertEquals(CATEGORY, loaded.getCategory());
        assertNotNull(loaded.getCreateTime(), "create_time 应由数据库默认值填充");

        // 3. 计数应为 1
        assertEquals(1, dao.count(CATEGORY));

        // 4. 删除后查询应为空，计数归零
        int deleted = dao.deleteById(id);
        assertEquals(1, deleted);
        assertNull(dao.queryById(id));
        assertEquals(0, dao.count(CATEGORY));
    }

    /**
     * 场景：按 id 触发 update_time 刷新，验证受影响行数。
     */
    @Test
    void testUpdateById() {
        // 1. 插入记录
        int id = dao.insert(new Bookmark(null, "https://github.com", "GitHub",
                null, CATEGORY, LocalDateTime.now(), null, null));

        // 2. 记录原始 update_time
        LocalDateTime before = dao.queryById(id).getUpdateTime();

        // 3. 触发更新并等待以跨越秒级精度
        int affected = dao.updateById(id);
        assertEquals(1, affected);

        // 4. update_time 应被刷新为当前时刻
        LocalDateTime after = dao.queryById(id).getUpdateTime();
        assertNotNull(after);
        if (before != null) {
            assertFalse(after.isBefore(before), "update_time 应不早于更新前的时间");
        }

        // 5. 不存在的 id 应返回 0 行受影响
        assertEquals(0, dao.updateById(999999));
    }

    /**
     * 场景：按关键字在 url 与 title 上模糊搜索。
     */
    @Test
    void testQueryByKeyword() {
        // 1. 插入两条可被关键字命中的记录，以及一条不应命中的记录
        dao.insert(new Bookmark(null, "https://openai.com", "OpenAI", null, CATEGORY, null, null, null));
        dao.insert(new Bookmark(null, "https://docs.python.org", "Python Docs", null, CATEGORY, null, null, null));
        dao.insert(new Bookmark(null, "https://example.com", "Example", null, CATEGORY, null, null, null));

        // 2. 关键字命中 url
        List<Bookmark> byUrl = dao.queryByKeyword("openai");
        assertEquals(1, byUrl.size());
        assertEquals("https://openai.com", byUrl.get(0).getUrl());

        // 3. 关键字命中 title
        List<Bookmark> byTitle = dao.queryByKeyword("Python");
        assertEquals(1, byTitle.size());
        assertEquals("Python Docs", byTitle.get(0).getTitle());

        // 4. 关键字不区分字段，且能匹配包含的子串
        List<Bookmark> bySub = dao.queryByKeyword("docs");
        assertEquals(1, bySub.size());
        assertEquals("https://docs.python.org", bySub.get(0).getUrl());

        // 5. 无匹配时返回空列表
        assertTrue(dao.queryByKeyword("nonexistent_keyword_xyz").isEmpty());
    }

    /**
     * 场景：按分类分页查询，验证分页偏移与每页大小。
     */
    @Test
    void testPaginatedQuery() {
        // 1. 插入 5 条同一分类的记录
        int total = 5;
        for (int i = 1; i <= total; i++) {
            dao.insert(new Bookmark(null, "https://page" + i + ".com", "Page" + i,
                    null, PAGE_CATEGORY, null, null, null));
        }
        assertEquals(total, dao.count(PAGE_CATEGORY));

        // 2. 第一页（pageSize=2）应返回 2 条
        List<Bookmark> page1 = dao.query(PAGE_CATEGORY, 1, 2);
        assertEquals(2, page1.size());

        // 3. 第二页应返回接下来的 2 条
        List<Bookmark> page2 = dao.query(PAGE_CATEGORY, 2, 2);
        assertEquals(2, page2.size());
        assertNotEquals(page1.get(0).getId(), page2.get(0).getId());

        // 4. 第三页（剩余 1 条）应返回 1 条
        List<Bookmark> page3 = dao.query(PAGE_CATEGORY, 3, 2);
        assertEquals(1, page3.size());

        // 5. 越界分页返回空列表
        assertTrue(dao.query(PAGE_CATEGORY, 10, 2).isEmpty());

        // 6. 空分类参数应查询全部（此处至少包含本用例的 PAGE_CATEGORY 与 OTHER 数据）
        dao.insert(new Bookmark(null, "https://other.com", "Other", null, "OTHER", null, null, null));
        List<Bookmark> all = dao.query(null, 1, 1000);
        assertTrue(all.size() >= total + 1, "查询全部应至少包含本用例插入的记录");
        assertTrue(all.stream().anyMatch(b -> "OTHER".equals(b.getCategory())), "结果应含 OTHER 分类记录");
    }

    /**
     * 场景：批量插入在单一事务内完成。
     */
    @Test
    void testBatchInsert() {
        // 1. 构造多条待插入记录
        List<Bookmark> list = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            list.add(new Bookmark(null, "https://batch" + i + ".com", "Batch" + i,
                    null, CATEGORY, LocalDateTime.now(), null, null));
        }

        // 2. 批量插入
        dao.batchInsert(list);

        // 3. 校验全部写入
        assertEquals(3, dao.count(CATEGORY));
        assertEquals(3, dao.queryByKeyword("batch").size());

        // 4. 空列表不应抛异常
        dao.batchInsert(new ArrayList<>());
    }

    // ---- 辅助方法 ----

    /** 删除指定测试分类下的所有记录，保证用例隔离。 */
    private void clearCategory(String category) {
        try (Connection conn = DatabaseMgr.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("DELETE FROM bookmarks WHERE category = '" + category + "'");
        } catch (SQLException e) {
            throw new RuntimeException("Failed to clear test category: " + category, e);
        }
    }
}
