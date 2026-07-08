import com.bookmark.db.BookmarkDAO;
import com.bookmark.db.DatabaseMgr;
import com.bookmark.model.Bookmark;
import com.bookmark.service.BookmarkService;

import org.junit.jupiter.api.*;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BookmarkService 单元测试。
 * <p>
 * 采用手动依赖注入（直接 new BookmarkDAO() 与 new BookmarkService(dao)），
 * 聚焦业务逻辑与校验规则，不引入任何 Mock 框架。数据库使用共享的 SQLite 实例，
 * 每个用例通过独立分类前缀隔离数据。
 * </p>
 */
class BookmarkServiceTests {

    private static final String CATEGORY = "SVC_TEST";

    private BookmarkDAO dao;
    private BookmarkService service;

    // 1. 初始化数据库连接与表结构
    @BeforeAll
    static void beforeAll() {
        DatabaseMgr.initialize();
    }

    // 1. 每个用例前构造服务并清理测试数据，保证隔离
    @BeforeEach
    void setUp() {
        dao = new BookmarkDAO();
        service = new BookmarkService(dao);
        clearCategory(CATEGORY);
    }

    // 1. 所有用例结束后释放连接
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
     * 场景：正常新增书签，返回的实体应包含数据库生成的主键与原始字段。
     */
    @Test
    void testAddSuccess() {
        // 1. 调用新增
        Bookmark saved = service.add("https://example.com", "Example", "https://example.com/icon.png", CATEGORY);

        // 2. 断言主键已回填且字段一致
        assertTrue(saved.getId() > 0, "新增后应回填有效的自增 id");
        assertEquals("https://example.com", saved.getUrl());
        assertEquals("Example", saved.getTitle());
        assertEquals(CATEGORY, saved.getCategory());

        // 3. 数据库中确实存在该记录
        assertEquals(1, dao.count(CATEGORY));
    }

    /**
     * 场景：add 的各入参为空/空串时应抛出 IllegalArgumentException。
     */
    @Test
    void testAddValidation() {
        assertThrows(IllegalArgumentException.class, () -> service.add(null, "t", "i", CATEGORY));
        assertThrows(IllegalArgumentException.class, () -> service.add(" ", "t", "i", CATEGORY));
        assertThrows(IllegalArgumentException.class, () -> service.add("u", null, "i", CATEGORY));
        assertThrows(IllegalArgumentException.class, () -> service.add("u", "t", "i", ""));
    }

    /**
     * 场景：分页参数非法（null / 非正数）时应抛出 IllegalArgumentException。
     */
    @Test
    void testListValidation() {
        assertThrows(IllegalArgumentException.class, () -> service.list(CATEGORY, null, 10));
        assertThrows(IllegalArgumentException.class, () -> service.list(CATEGORY, 0, 10));
        assertThrows(IllegalArgumentException.class, () -> service.list(CATEGORY, -1, 10));
        assertThrows(IllegalArgumentException.class, () -> service.list(CATEGORY, 1, null));
        assertThrows(IllegalArgumentException.class, () -> service.list(CATEGORY, 1, 0));
    }

    /**
     * 场景：分页查询返回正确的分页切片。
     */
    @Test
    void testListPagination() {
        // 1. 插入 5 条同分类记录
        for (int i = 1; i <= 5; i++) {
            service.add("https://page" + i + ".com", "Page" + i, "icon.png", CATEGORY);
        }

        // 2. 第一页（每页 2 条）返回 2 条
        List<Bookmark> page1 = service.list(CATEGORY, 1, 2);
        assertEquals(2, page1.size());

        // 3. 第二页返回接下来的 2 条，且与第一页不重复
        List<Bookmark> page2 = service.list(CATEGORY, 2, 2);
        assertEquals(2, page2.size());
        assertNotEquals(page1.get(0).getId(), page2.get(0).getId());

        // 4. 末页仅剩 1 条
        assertEquals(1, service.list(CATEGORY, 3, 2).size());
    }

    /**
     * 场景：删除存在/不存在的记录分别返回 true / false。
     */
    @Test
    void testDelete() {
        // 1. 新增后可成功删除
        Bookmark saved = service.add("https://del.com", "Del", "i", CATEGORY);
        assertTrue(service.delete(saved.getId()));
        assertFalse(service.delete(saved.getId()), "重复删除应返回 false");

        // 2. 删除不存在的 id 返回 false
        assertFalse(service.delete(999999));
    }

    /**
     * 场景：delete 的 id 为 null 时应抛出校验异常。
     */
    @Test
    void testDeleteValidation() {
        assertThrows(IllegalArgumentException.class, () -> service.delete(null));
    }

    /**
     * 场景：按关键字可在 url 与 title 上命中。
     */
    @Test
    void testSearch() {
        service.add("https://searchable.com", "Searchable Title", "i", CATEGORY);

        // 1. 关键字命中 url
        List<Bookmark> byUrl = service.search("searchable");
        assertEquals(1, byUrl.size());

        // 2. 关键字命中 title
        List<Bookmark> byTitle = service.search("Title");
        assertEquals(1, byTitle.size());

        // 3. 无匹配返回空列表
        assertTrue(service.search("no_such_keyword").isEmpty());
    }

    /**
     * 场景：search 关键为空时应抛出校验异常。
     */
    @Test
    void testSearchValidation() {
        assertThrows(IllegalArgumentException.class, () -> service.search(null));
        assertThrows(IllegalArgumentException.class, () -> service.search("  "));
    }

    /**
     * 场景：更新已存在记录字段成功，且未命中 id 返回 false。
     */
    @Test
    void testUpdate() {
        // 1. 新增记录
        Bookmark saved = service.add("https://old.com", "Old", "old.png", CATEGORY);
        int id = saved.getId();

        // 2. 更新各字段并断言返回成功
        boolean updated = service.update(id, "https://new.com", "New", "new.png", CATEGORY);
        assertTrue(updated);

        // 3. 数据库中的字段已被修改
        Bookmark reloaded = dao.queryById(id);
        assertEquals("https://new.com", reloaded.getUrl());
        assertEquals("New", reloaded.getTitle());
        assertEquals("new.png", reloaded.getIcon());

        // 4. 更新不存在的 id 返回 false
        assertFalse(service.update(999999, "u", "t", "i", CATEGORY));
    }

    /**
     * 场景：update 的各入参非法时应抛出校验异常。
     */
    @Test
    void testUpdateValidation() {
        assertThrows(IllegalArgumentException.class, () -> service.update(null, "u", "t", "i", CATEGORY));
        assertThrows(IllegalArgumentException.class, () -> service.update(1, null, "t", "i", CATEGORY));
        assertThrows(IllegalArgumentException.class, () -> service.update(1, "u", "", "i", CATEGORY));
        assertThrows(IllegalArgumentException.class, () -> service.update(1, "u", "t", null, CATEGORY));
        assertThrows(IllegalArgumentException.class, () -> service.update(1, "u", "t", "i", " "));
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
