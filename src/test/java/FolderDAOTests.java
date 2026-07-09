import com.bookmark.db.DatabaseMgr;
import com.bookmark.db.FolderDAO;
import com.bookmark.model.Folder;

import org.junit.jupiter.api.*;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FolderDAO 场景化集成测试。
 * <p>
 * 通过 {@link DatabaseMgr} 连接共享的 SQLite 数据库，
 * 覆盖插入、按 id 查询、更新、删除、按名称模糊搜索、按 parent_id / root 查询、
 * 分页排序、全表查询、存在性校验、子节点计数、批量插入以及 parent_id / is_root 同步约束。
 * </p>
 */
class FolderDAOTests {

    private FolderDAO dao;

    @BeforeAll
    static void beforeAll() {
        DatabaseMgr.initialize();
    }

    @BeforeEach
    void setUp() {
        dao = new FolderDAO();
        clearTestData();
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
    void testInsertQueryById() {
        Folder f = createFolder("TEST_ROOT", null, true);
        int id = dao.insert(f);
        assertTrue(id > 0, "插入后应返回有效的自增 id");

        Folder loaded = dao.queryById(id);
        assertNotNull(loaded, "应能按 id 查询到刚插入的文件夹");
        assertEquals("TEST_ROOT", loaded.getName());
        assertNull(loaded.getParentId());
        assertTrue(loaded.isRoot());
        assertNotNull(loaded.getCreateTime(), "create_time 应由数据库默认值填充");
        assertNotNull(loaded.getUpdateTime(), "update_time 应由数据库默认值填充");
    }

    @Test
    void testUpdate() {
        Folder root = createFolder("TEST_UPDATE_ROOT", null, true);
        int rootId = dao.insert(root);

        Folder child = createFolder("TEST_UPDATE_CHILD", rootId, false);
        int childId = dao.insert(child);

        Folder updated = createFolder("TEST_UPDATE_CHILD_NEW", null, false);
        updated.setId(childId);
        int affected = dao.update(updated);
        assertEquals(1, affected, "更新存在的记录应返回 1");

        Folder loaded = dao.queryById(childId);
        assertEquals("TEST_UPDATE_CHILD_NEW", loaded.getName());
        assertNull(loaded.getParentId());
        assertTrue(loaded.isRoot(), "parent_id 为 null 时 is_root 应为 true");

        Folder ghost = createFolder("ghost", null, true);
        ghost.setId(999999);
        assertEquals(0, dao.update(ghost), "更新不存在的 id 应返回 0");
    }

    @Test
    void testDelete() {
        Folder f = createFolder("TEST_DELETE", null, true);
        int id = dao.insert(f);

        assertEquals(1, dao.deleteById(id), "删除存在的记录应返回 1");
        assertNull(dao.queryById(id), "删除后查询应为空");
    }

    @Test
    void testDeleteWithChildren() {
        Folder parent = createFolder("TEST_PARENT", null, true);
        int parentId = dao.insert(parent);
        dao.insert(createFolder("TEST_CHILD", parentId, false));

        assertThrows(IllegalStateException.class, () -> dao.deleteById(parentId),
                "包含子元素的文件夹删除时应抛出异常");
    }

    @Test
    void testQueryByNameFuzzy() {
        dao.insert(createFolder("TEST_ALPHA", null, true));
        dao.insert(createFolder("TEST_BETA", null, true));
        dao.insert(createFolder("GAMMA", null, true));

        List<Folder> byTest = dao.query(createCriteria("TEST"), 1, 10, "id");
        assertEquals(2, byTest.size(), "模糊搜索 TEST 应命中 2 条");

        List<Folder> byAlpha = dao.query(createCriteria("ALPHA"), 1, 10, "id");
        assertEquals(1, byAlpha.size());
        assertEquals("TEST_ALPHA", byAlpha.get(0).getName());

        List<Folder> none = dao.query(createCriteria("NONEXISTENT"), 1, 10, "id");
        assertTrue(none.isEmpty(), "无匹配时应返回空列表");
    }

    @Test
    void testQueryRootFolders() {
        Folder root = createFolder("TEST_ROOT", null, true);
        int rootId = dao.insert(root);
        dao.insert(createFolder("TEST_CHILD", rootId, false));

        List<Folder> roots = dao.query(createRootCriteria(true), 1, 10, "id");
        assertEquals(1, roots.size(), "应仅返回根文件夹");
        assertEquals("TEST_ROOT", roots.get(0).getName());
    }

    @Test
    void testQueryByParentId() {
        Folder parent = createFolder("TEST_PARENT", null, true);
        int parentId = dao.insert(parent);
        dao.insert(createFolder("TEST_CHILD_1", parentId, false));
        dao.insert(createFolder("TEST_CHILD_2", parentId, false));

        List<Folder> children = dao.query(createParentCriteria(parentId), 1, 10, "id");
        assertEquals(2, children.size(), "应返回指定父文件夹下的直接子文件夹");
        assertTrue(children.stream().allMatch(c -> parentId == c.getParentId()));
    }

    @Test
    void testQueryPaginationAndSort() {
        Folder parent = createFolder("TEST_PAGE_PARENT", null, true);
        int parentId = dao.insert(parent);
        for (int i = 1; i <= 5; i++) {
            dao.insert(createFolder("TEST_PAGE_" + i, parentId, false));
        }

        List<Folder> page1 = dao.query(createParentCriteria(parentId), 1, 2, "id");
        assertEquals(2, page1.size());

        List<Folder> page2 = dao.query(createParentCriteria(parentId), 2, 2, "id");
        assertEquals(2, page2.size());
        assertNotEquals(page1.get(0).getId(), page2.get(0).getId());

        List<Folder> page3 = dao.query(createParentCriteria(parentId), 3, 2, "id");
        assertEquals(1, page3.size());

        List<Folder> page4 = dao.query(createParentCriteria(parentId), 10, 2, "id");
        assertTrue(page4.isEmpty(), "越界分页应返回空列表");
    }

    @Test
    void testQuerySortByName() {
        Folder parent = createFolder("TEST_SORT_PARENT", null, true);
        int parentId = dao.insert(parent);
        dao.insert(createFolder("TEST_Z", parentId, false));
        dao.insert(createFolder("TEST_A", parentId, false));

        List<Folder> sorted = dao.query(createParentCriteria(parentId), 1, 10, "name");
        assertEquals("TEST_A", sorted.get(0).getName(), "按 name 排序应升序");
        assertEquals("TEST_Z", sorted.get(1).getName());
    }

    @Test
    void testQueryAll() {
        Folder root = createFolder("TEST_ALL_ROOT", null, true);
        int rootId = dao.insert(root);
        dao.insert(createFolder("TEST_ALL_CHILD", rootId, false));
        dao.insert(createFolder("TEST_ANOTHER_ROOT", null, true));

        List<Folder> all = dao.queryAll();
        assertEquals(3, all.size(), "queryAll 应返回全部文件夹");

        assertEquals("TEST_ALL_ROOT", all.get(0).getName(), "parent_id 为 null 的应排在前面");
        assertEquals("TEST_ANOTHER_ROOT", all.get(1).getName());
        assertEquals("TEST_ALL_CHILD", all.get(2).getName(), "同 parent_id 内应按 id 排序");
    }

    @Test
    void testExistsByNameAndParent() {
        Folder parent = createFolder("TEST_EXISTS_PARENT", null, true);
        int parentId = dao.insert(parent);
        dao.insert(createFolder("TEST_DUPLICATE", parentId, false));

        assertTrue(dao.existsByNameAndParent("TEST_DUPLICATE", parentId),
                "同名同 parent_id 应返回 true");
        assertFalse(dao.existsByNameAndParent("TEST_DUPLICATE", null),
                "同名不同 parent_id 应返回 false");
        assertFalse(dao.existsByNameAndParent("TEST_NONEXISTENT", parentId),
                "不同名应返回 false");

        dao.insert(createFolder("TEST_ROOT_DUP", null, true));
        assertTrue(dao.existsByNameAndParent("TEST_ROOT_DUP", null),
                "根文件夹同名应返回 true");
    }

    @Test
    void testCountByParentId() {
        Folder parent = createFolder("TEST_COUNT_PARENT", null, true);
        int parentId = dao.insert(parent);
        dao.insert(createFolder("TEST_COUNT_1", parentId, false));
        dao.insert(createFolder("TEST_COUNT_2", parentId, false));

        assertEquals(2, dao.countByParentId(parentId), "应返回直接子文件夹数量");
        assertEquals(0, dao.countByParentId(999999), "不存在 parent_id 应返回 0");
    }

    @Test
    void testBatchInsert() {
        List<Folder> list = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            list.add(createFolder("TEST_BATCH_" + i, null, true));
        }

        dao.batchInsert(list);

        long count = dao.queryAll().stream().filter(f -> f.getName().startsWith("TEST_BATCH_")).count();
        assertEquals(3, count, "批量插入应全部写入");
    }

    @Test
    void testParentIdIsRootSync() {
        Folder root = createFolder("TEST_SYNC_ROOT", null, true);
        int rootId = dao.insert(root);
        assertTrue(dao.queryById(rootId).isRoot(), "parent_id 为 null 时 is_root 应为 true");

        Folder child = createFolder("TEST_SYNC_CHILD", rootId, false);
        int childId = dao.insert(child);
        assertFalse(dao.queryById(childId).isRoot(), "parent_id 非 null 时 is_root 应为 false");

        Folder updated = createFolder("TEST_SYNC_CHILD", null, false);
        updated.setId(childId);
        dao.update(updated);
        assertTrue(dao.queryById(childId).isRoot(), "更新为 parent_id=null 后 is_root 应为 true");
    }

    // ---- 辅助方法 ----

    private Folder createFolder(String name, Integer parentId, boolean root) {
        Folder f = new Folder();
        f.setName(name);
        f.setParentId(parentId);
        f.setRoot(root);
        f.setAddDate(LocalDateTime.now());
        f.setLastModified(LocalDateTime.now());
        return f;
    }

    private Folder createCriteria(String name) {
        Folder f = new Folder();
        f.setName(name);
        return f;
    }

    private Folder createRootCriteria(boolean root) {
        Folder f = new Folder();
        f.setRoot(root);
        return f;
    }

    private Folder createParentCriteria(Integer parentId) {
        Folder f = new Folder();
        f.setParentId(parentId);
        return f;
    }

    private void clearTestData() {
        try (Connection conn = DatabaseMgr.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("DELETE FROM folders WHERE name LIKE 'TEST_%'");
        } catch (SQLException e) {
            throw new RuntimeException("Failed to clear test data", e);
        }
    }
}
