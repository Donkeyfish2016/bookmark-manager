import com.bookmark.db.BookmarkDAO;
import com.bookmark.db.DatabaseMgr;
import com.bookmark.db.FolderDAO;
import com.bookmark.html.HtmlBookmarkParser;
import com.bookmark.model.Bookmark;
import com.bookmark.model.Folder;
import com.bookmark.model.ImportResult;
import com.bookmark.service.BookmarkService;
import com.bookmark.service.FolderService;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
    private FolderDAO folderDao;
    private BookmarkService service;
    private FolderService folderService;

    @TempDir
    Path tempDir;

    // 1. 初始化数据库连接与表结构
    @BeforeAll
    static void beforeAll() {
        DatabaseMgr.initialize();
    }

    // 1. 每个用例前构造服务并清理测试数据，保证隔离
    @BeforeEach
    void setUp() {
        folderService = new FolderService();
        dao = new BookmarkDAO();
        folderDao = new FolderDAO();
        service = new BookmarkService(dao, folderDao, folderService);
        clearCategory(CATEGORY);
    }

    // 1. 每个用例后清空全量数据并清理导出文件，避免污染其他用例
    @AfterEach
    void tearDown() {
        dao.deleteAll();
        folderDao.deleteAll();
        new File("output_test.html").delete();
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

    /**
     * 场景：从示例 HTML 文件导入书签，验证全部记录成功写入且分类结构正确。
     */
    @Test
    void testImportFromHtml() {
        // 1. 导入基准示例文件
        ImportResult imported = service.importFromHtml("src/main/java/com/bookmark/html/example.html");

        // 2. 12 条有效书签应全部成功导入，5 个文件夹层级建立
        assertEquals(12, imported.getBookmarks());
        assertEquals(5, imported.getFolders());

        // 3. 各分类记录数应与示例一致
        assertEquals(4, service.list("收藏夹栏", 1, 100).size());
        assertEquals(3, service.list("学习资源", 1, 100).size());
        assertEquals(3, service.list("编程开发/前端", 1, 100).size());
        assertEquals(2, service.list("编程开发/后端", 1, 100).size());

        // 4. 文件夹层级应正确：编程开发(父) -> 前端/后端(子)
        Map<String, Folder> folders = allFoldersByName();
        assertTrue(folders.containsKey("编程开发"));
        assertTrue(folders.containsKey("前端"));
        assertEquals(folders.get("编程开发").getId(), folders.get("前端").getParentId());
    }

    /**
     * 场景：将全部书签导出为 HTML，验证文件格式正确且可回灌解析。
     */
    @Test
    void testExportToHtml() throws Exception {
        File out = new File("output_test.html");
        if (out.exists()) {
            assertTrue(out.delete());
        }

        // 1. 插入一条可识别的书签，保证导出内容可验证
        service.add("https://export-verify.com", "ExportVerify", "i.png", CATEGORY);

        // 2. 导出全部书签到项目根目录
        service.exportToHtml("output_test.html");

        // 3. 文件应生成且以标准 Netscape 头部开头
        assertTrue(out.exists());
        String content = Files.readString(out.toPath(), StandardCharsets.UTF_8);
        assertTrue(content.startsWith("<!DOCTYPE NETSCAPE-Bookmark-file-1>"));

        // 4. 回灌解析，应包含刚插入的标记书签
        Folder root = new HtmlBookmarkParser().parse(out);
        List<Bookmark> roundTrip = new ArrayList<>();
        collectAll(root, roundTrip);
        assertTrue(roundTrip.stream().anyMatch(b -> "https://export-verify.com".equals(b.getUrl())));
    }

    // ===== 导入场景：层级 / 边界 / 特殊用例 =====

    /** 将 HTML 字符串以 UTF-8 写入临时文件，供导入流程读取。 */
    private File writeHtml(String name, String html) throws Exception {
        File file = tempDir.resolve(name).toFile();
        Files.writeString(file.toPath(), html, StandardCharsets.UTF_8);
        return file;
    }

    /** 将全部文件夹按名称建立索引，便于断言层级关系。 */
    private Map<String, Folder> allFoldersByName() {
        return folderService.getAllFolders().stream()
                .collect(Collectors.toMap(Folder::getName, f -> f, (a, b) -> a));
    }

    /**
     * 场景：深度嵌套的文件夹层级应被完整还原，且 parent_id 指向上级数据库 id。
     */
    @Test
    void testImportDeeplyNestedHierarchy() throws Exception {
        String html = "<!DOCTYPE NETSCAPE-Bookmark-file-1>\n<DL><p>\n"
                + "  <DT><H3>L1</H3><DL><p>\n"
                + "    <DT><H3>L2</H3><DL><p>\n"
                + "      <DT><H3>L3</H3><DL><p>\n"
                + "        <DT><H3>L4</H3><DL><p>\n"
                + "          <DT><A HREF=\"https://deep.com/\" ADD_DATE=\"1\">Deep</A>\n"
                + "        </DL><p>\n"
                + "      </DL><p>\n"
                + "    </DL><p>\n"
                + "  </DL><p>\n"
                + "</DL><p>\n";
        ImportResult result = service.importFromHtml(writeHtml("deep.html", html).getPath());

        // 1. 共 4 个文件夹、1 条书签
        assertEquals(4, result.getFolders());
        assertEquals(1, result.getBookmarks());

        // 2. parent_id 应逐层指向上级文件夹的数据库 id
        Map<String, Folder> folders = allFoldersByName();
        assertNotNull(folders.get("L1"));
        assertNull(folders.get("L1").getParentId(), "顶级文件夹 parent_id 应为 null");
        assertEquals(folders.get("L1").getId(), folders.get("L2").getParentId());
        assertEquals(folders.get("L2").getId(), folders.get("L3").getParentId());
        assertEquals(folders.get("L3").getId(), folders.get("L4").getParentId());

        // 3. 最深书签应归属 L4 文件夹
        List<Bookmark> inL4 = dao.queryByFolderId(folders.get("L4").getId());
        assertEquals(1, inL4.size());
        assertEquals("https://deep.com/", inL4.get(0).getUrl());
    }

    /**
     * 场景：空文件夹（无书签、无子文件夹）也应被导入，且不产生孤立书签。
     */
    @Test
    void testImportEmptyFolder() throws Exception {
        String html = "<!DOCTYPE NETSCAPE-Bookmark-file-1>\n<DL><p>\n"
                + "  <DT><H3>Empty</H3><DL><p>\n"
                + "  </DL><p>\n"
                + "  <DT><H3>WithBm</H3><DL><p>\n"
                + "    <DT><A HREF=\"https://x.com/\" ADD_DATE=\"1\">X</A>\n"
                + "  </DL><p>\n"
                + "</DL><p>\n";
        ImportResult result = service.importFromHtml(writeHtml("empty.html", html).getPath());

        // 1. 两个文件夹、一条书签
        assertEquals(2, result.getFolders());
        assertEquals(1, result.getBookmarks());

        // 2. 空文件夹存在且无下属书签
        Folder empty = allFoldersByName().get("Empty");
        assertNotNull(empty);
        assertTrue(dao.queryByFolderId(empty.getId()).isEmpty());
    }

    /**
     * 场景：缺少 ICON 属性的书签，icon 字段应为 null 而非占位串。
     */
    @Test
    void testImportBookmarkMissingIcon() throws Exception {
        String html = "<!DOCTYPE NETSCAPE-Bookmark-file-1>\n<DL><p>\n"
                + "  <DT><H3>F</H3><DL><p>\n"
                + "    <DT><A HREF=\"https://noicon.com/\" ADD_DATE=\"1\">NoIcon</A>\n"
                + "  </DL><p>\n"
                + "</DL><p>\n";
        service.importFromHtml(writeHtml("noicon.html", html).getPath());

        Map<String, Folder> folders = allFoldersByName();
        List<Bookmark> bookmarks = dao.queryByFolderId(folders.get("F").getId());
        assertEquals(1, bookmarks.size());
        assertNull(bookmarks.get(0).getIcon(), "缺失 ICON 时 icon 应为 null");
    }

    /**
     * 场景：标题与 URL 包含特殊字符（& < > " 及查询参数）时应原样保留。
     */
    @Test
    void testImportSpecialCharacters() throws Exception {
        // HTML 中对特殊字符做实体转义，解析后应还原为原始字符
        String url = "https://special.com/search?q=1&x=\"y\"&z=<a>";
        String title = "A&B <C> \"quote\"";
        String html = "<!DOCTYPE NETSCAPE-Bookmark-file-1>\n<DL><p>\n"
                + "  <DT><H3>F</H3><DL><p>\n"
                + "    <DT><A HREF=\"" + escapeForAttr(url) + "\" ADD_DATE=\"1\">" + escapeForText(title) + "</A>\n"
                + "  </DL><p>\n"
                + "</DL><p>\n";
        service.importFromHtml(writeHtml("special.html", html).getPath());

        Map<String, Folder> folders = allFoldersByName();
        List<Bookmark> bookmarks = dao.queryByFolderId(folders.get("F").getId());
        assertEquals(1, bookmarks.size());
        assertEquals(url, bookmarks.get(0).getUrl(), "URL 中的特殊字符应原样保留");
        assertEquals(title, bookmarks.get(0).getTitle(), "标题中的特殊字符应原样保留");
    }

    /**
     * 场景：相同 URL 出现在不同文件夹时，应分别导入为两条记录，且归属不同文件夹。
     */
    @Test
    void testImportDuplicateUrlDifferentFolders() throws Exception {
        String html = "<!DOCTYPE NETSCAPE-Bookmark-file-1>\n<DL><p>\n"
                + "  <DT><H3>FolderA</H3><DL><p>\n"
                + "    <DT><A HREF=\"https://dup.com/\" ADD_DATE=\"1\">A</A>\n"
                + "  </DL><p>\n"
                + "  <DT><H3>FolderB</H3><DL><p>\n"
                + "    <DT><A HREF=\"https://dup.com/\" ADD_DATE=\"2\">B</A>\n"
                + "  </DL><p>\n"
                + "</DL><p>\n";
        ImportResult result = service.importFromHtml(writeHtml("dup.html", html).getPath());

        // 1. 两个文件夹、两条书签
        assertEquals(2, result.getFolders());
        assertEquals(2, result.getBookmarks());

        // 2. 两条书签 URL 相同但 folder_id 不同
        Map<String, Folder> folders = allFoldersByName();
        List<Bookmark> inA = dao.queryByFolderId(folders.get("FolderA").getId());
        List<Bookmark> inB = dao.queryByFolderId(folders.get("FolderB").getId());
        assertEquals(1, inA.size());
        assertEquals(1, inB.size());
        assertEquals("https://dup.com/", inA.get(0).getUrl());
        assertEquals("https://dup.com/", inB.get(0).getUrl());
        assertNotEquals(inA.get(0).getFolderId(), inB.get(0).getFolderId(),
                "同 URL 不同文件夹应归属不同的 folder_id");
    }

    // ---- 辅助方法 ----

    /** 对 HTML 属性值做必要转义（双引号、&、<、>）。 */
    private String escapeForAttr(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    /** 对 HTML 文本节点做必要转义（&、<、>）。 */
    private String escapeForText(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** 递归收集整棵文件夹树中的所有书签。 */
    private void collectAll(Folder folder, List<Bookmark> out) {
        out.addAll(folder.getBookmarks());
        for (Folder child : folder.getChildren().values()) {
            collectAll(child, out);
        }
    }

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
