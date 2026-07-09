import com.bookmark.html.HtmlBookmarkParser;
import com.bookmark.model.Bookmark;
import com.bookmark.model.Folder;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * HtmlBookmarkParser 解析测试：覆盖嵌套结构、深层嵌套、游离书签、编码与错误场景。
 */
class HtmlParserTests {

    private HtmlBookmarkParser parser;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        parser = new HtmlBookmarkParser();
    }

    /** 将 HTML 字符串以 UTF-8 写入临时文件，供解析器读取。 */
    private File writeHtml(String name, String html) throws Exception {
        File file = tempDir.resolve(name).toFile();
        Files.writeString(file.toPath(), html, StandardCharsets.UTF_8);
        return file;
    }

    /** 与解析器一致的 Unix 秒 -> UTC LocalDateTime 转换。 */
    private LocalDateTime expectedDate(long epochSecond) {
        return LocalDateTime.ofInstant(Instant.ofEpochSecond(epochSecond), ZoneOffset.UTC);
    }

    // ===== 1. 基准文件：验证层级树结构 =====

    @Test
    void testParseExampleFileBuildsHierarchy() throws Exception {
        File file = new File("src/main/java/com/bookmark/html/example.html");
        assertTrue(file.exists(), "基准文件应存在");

        Folder root = parser.parse(file);
        assertNotNull(root);
        assertTrue(root.isRoot());

        // 根目录下应有 3 个顶层文件夹
        Map<String, Folder> children = root.getChildren();
        assertEquals(3, children.size(), "根目录应包含 3 个顶层文件夹");
        assertTrue(children.containsKey("收藏夹栏"));
        assertTrue(children.containsKey("学习资源"));
        assertTrue(children.containsKey("编程开发"));

        // 单层文件夹“收藏夹栏”含 4 条书签
        assertEquals(4, children.get("收藏夹栏").getBookmarks().size());

        // 嵌套文件夹“编程开发”含“前端”“后端”两个子文件夹
        Folder dev = children.get("编程开发");
        assertEquals(2, dev.getChildren().size());
        assertTrue(dev.getChildren().containsKey("前端"));
        assertTrue(dev.getChildren().containsKey("后端"));
        assertEquals(3, dev.getChildren().get("前端").getBookmarks().size());
        assertEquals(2, dev.getChildren().get("后端").getBookmarks().size());

        // 整棵树共 12 条书签（4 + 3 + 3 + 2）
        assertEquals(12, countBookmarks(root));
    }

    @Test
    void testExampleBookmarkFieldMapping() throws Exception {
        File file = new File("src/main/java/com/bookmark/html/example.html");
        Folder root = parser.parse(file);

        Map<String, Bookmark> byUrl = collectByUrl(root);
        Bookmark bing = byUrl.get("https://www.bing.com/");
        assertNotNull(bing);
        assertEquals("必应", bing.getTitle());
        assertEquals("收藏夹栏", bing.getCategory());
        assertEquals(expectedDate(1700000001L), bing.getAddDate());
        assertNotNull(bing.getIcon());
        assertTrue(bing.getIcon().startsWith("data:image/png;base64,"), "icon 应为 data URI");
        assertNull(bing.getFolderId(), "解析阶段不应赋值 folderId");

        Bookmark vue = byUrl.get("https://vuejs.org/");
        assertEquals("编程开发/前端", vue.getCategory());
        assertNull(vue.getFolderId());
    }

    // ===== 2. 嵌套文件夹结构 =====

    @Test
    void testNestedFolderStructure() throws Exception {
        String html = "<!DOCTYPE NETSCAPE-Bookmark-file-1>\n<DL><p>\n"
                + "    <DT><H3>FolderA</H3>\n    <DL><p>\n"
                + "        <DT><H3>FolderB</H3>\n        <DL><p>\n"
                + "            <DT><A HREF=\"https://x.com/\" ADD_DATE=\"100\">X</A>\n"
                + "        </DL><p>\n"
                + "    </DL><p>\n"
                + "</DL><p>\n";
        Folder root = parser.parse(writeHtml("nested.html", html));

        Folder a = root.getChildren().get("FolderA");
        assertNotNull(a);
        Folder b = a.getChildren().get("FolderB");
        assertNotNull(b);
        assertEquals(1, b.getBookmarks().size());
        assertEquals("FolderA/FolderB", b.getBookmarks().get(0).getCategory());
    }

    // ===== 3. 深层嵌套书签 =====

    @Test
    void testDeeplyNestedBookmark() throws Exception {
        // Level1 > Level2 > Level3 > Level4 > Level5，最底层一条书签
        String html = "<!DOCTYPE NETSCAPE-Bookmark-file-1>\n<DL><p>\n"
                + "  <DT><H3>L1</H3><DL><p>\n"
                + "    <DT><H3>L2</H3><DL><p>\n"
                + "      <DT><H3>L3</H3><DL><p>\n"
                + "        <DT><H3>L4</H3><DL><p>\n"
                + "          <DT><H3>L5</H3><DL><p>\n"
                + "            <DT><A HREF=\"https://deep.com/\" ADD_DATE=\"999\">Deep</A>\n"
                + "          </DL><p>\n"
                + "        </DL><p>\n"
                + "      </DL><p>\n"
                + "    </DL><p>\n"
                + "  </DL><p>\n"
                + "</DL><p>\n";
        Folder root = parser.parse(writeHtml("deep.html", html));

        Folder current = root.getChildren().get("L1");
        for (String name : new String[]{"L2", "L3", "L4", "L5"}) {
            assertNotNull(current, "文件夹 " + name + " 应存在");
            current = current.getChildren().get(name);
        }
        assertNotNull(current);
        assertEquals(1, current.getBookmarks().size());
        assertEquals("L1/L2/L3/L4/L5", current.getBookmarks().get(0).getCategory());
    }

    // ===== 4. 游离书签归入 “游离书签”文件夹 =====

    @Test
    void testOrphanBookmarksAssignedToToolbar() throws Exception {
        String html = "<!DOCTYPE NETSCAPE-Bookmark-file-1>\n<DL><p>\n"
                + "    <DT><A HREF=\"https://orphan.com/\" ADD_DATE=\"111\">Orphan</A>\n"
                + "    <DT><H3>RealFolder</H3>\n    <DL><p>\n"
                + "        <DT><A HREF=\"https://inner.com/\" ADD_DATE=\"222\">Inner</A>\n"
                + "    </DL><p>\n"
                + "</DL><p>\n";
        Folder root = parser.parse(writeHtml("orphan.html", html));

        // 游离书签应进入默认 Toolbar 文件夹
        Folder toolbar = root.getChildren().get("游离书签");
        assertNotNull(toolbar, "应创建默认 游离书签 文件夹");
        assertEquals(1, toolbar.getBookmarks().size());
        assertEquals("https://orphan.com/", toolbar.getBookmarks().get(0).getUrl());
        assertEquals("游离书签", toolbar.getBookmarks().get(0).getCategory());

        // 普通文件夹及其书签不受影响
        assertEquals(1, root.getChildren().get("RealFolder").getBookmarks().size());
        assertEquals("RealFolder", root.getChildren().get("RealFolder").getBookmarks().get(0).getCategory());
    }

    @Test
    void testNoOrphansDoesNotCreateToolbar() throws Exception {
        String html = "<!DOCTYPE NETSCAPE-Bookmark-file-1>\n<DL><p>\n"
                + "    <DT><H3>F</H3>\n    <DL><p>\n"
                + "        <DT><A HREF=\"https://a.com/\" ADD_DATE=\"1\">A</A>\n"
                + "    </DL><p>\n"
                + "</DL><p>\n";
        Folder root = parser.parse(writeHtml("noorphan.html", html));
        assertFalse(root.getChildren().containsKey("游离书签"), "无游离书签时不应创建 游离书签 文件夹");
    }

    // ===== 5. 编码场景 =====

    @Test
    void testUtf8EncodingPreserved() throws Exception {
        String html = "<!DOCTYPE NETSCAPE-Bookmark-file-1>\n<DL><p>\n"
                + "    <DT><H3>技术文章 🚀</H3>\n    <DL><p>\n"
                + "        <DT><A HREF=\"https://例子.中国/\" ADD_DATE=\"5\">中文标题 Æøå</A>\n"
                + "    </DL><p>\n"
                + "</DL><p>\n";
        Folder root = parser.parse(writeHtml("utf8.html", html));

        Folder folder = root.getChildren().get("技术文章 🚀");
        assertNotNull(folder, "UTF-8 文件夹名应被原样保留");
        Bookmark b = folder.getBookmarks().get(0);
        assertEquals("中文标题 Æøå", b.getTitle());
        assertEquals("https://例子.中国/", b.getUrl());
    }

    // ===== 6. 元数据节点忽略 =====

    @Test
    void testMetadataNodesAreIgnored() throws Exception {
        // 真实书签文件中 META/TITLE 位于头部，解析器只遍历 <DL> 子树，天然忽略这些元数据节点
        String html = "<!DOCTYPE NETSCAPE-Bookmark-file-1>\n"
                + "<META HTTP-EQUIV=\"Content-Type\" CONTENT=\"text/html; charset=UTF-8\">\n"
                + "<TITLE>Bookmarks</TITLE>\n"
                + "<H1>Bookmarks</H1>\n"
                + "<DL><p>\n"
                + "    <DT><H3>F</H3>\n    <DL><p>\n"
                + "        <DT><A HREF=\"https://a.com/\" ADD_DATE=\"1\">A</A>\n"
                + "    </DL><p>\n"
                + "</DL><p>\n";
        Folder root = parser.parse(writeHtml("meta.html", html));

        Folder f = root.getChildren().get("F");
        assertNotNull(f);
        assertEquals(1, f.getBookmarks().size(), "META/TITLE 节点不应产生书签");
        assertEquals("https://a.com/", f.getBookmarks().get(0).getUrl());

        // 整棵树只应产出 1 条书签，证实 <META>/<TITLE> 被忽略
        assertEquals(1, countBookmarks(root));
    }

    @Test
    void testMetadataOnlyDtIsIgnored() throws Exception {
        // 仅含 <META>/<TITLE> 的 <DT> 不应被当作书签或文件夹
        String html = "<!DOCTYPE NETSCAPE-Bookmark-file-1>\n<DL><p>\n"
                + "    <DT><META NAME=\"foo\" CONTENT=\"bar\">\n"
                + "    <DT><TITLE>ignore</TITLE>\n"
                + "    <DT><H3>Real</H3>\n    <DL><p>\n"
                + "        <DT><A HREF=\"https://a.com/\" ADD_DATE=\"1\">A</A>\n"
                + "    </DL><p>\n"
                + "</DL><p>\n";
        Folder root = parser.parse(writeHtml("metaonly.html", html));

        assertEquals(1, root.getChildren().size(), "只有 Real 文件夹，META/TITLE 不应生成节点");
        assertTrue(root.getChildren().containsKey("Real"));
        assertEquals(1, countBookmarks(root));
    }

    // ===== 7. 错误场景 =====

    @Test
    void testMissingFileThrowsRuntimeException() {
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> parser.parse(new File(tempDir.resolve("does-not-exist.html").toUri())));
        assertTrue(ex.getMessage().contains("not found"), "缺失文件应抛出 RuntimeException");
    }

    @Test
    void testNullFileThrowsRuntimeException() {
        assertThrows(RuntimeException.class, () -> parser.parse(null));
    }

    @Test
    void testCorruptHtmlWithoutDlThrowsRuntimeException() throws Exception {
        File file = writeHtml("corrupt.html", "<html><body><p>not a bookmark file</p></body></html>");
        RuntimeException ex = assertThrows(RuntimeException.class, () -> parser.parse(file));
        assertTrue(ex.getMessage().contains("<DL>"), "无 <DL> 的文档应抛出 RuntimeException");
    }

    @Test
    void testEmptyFileThrowsRuntimeException() throws Exception {
        File file = writeHtml("empty.html", "");
        assertThrows(RuntimeException.class, () -> parser.parse(file));
    }

    // ===== 辅助方法 =====

    /** 递归统计整棵树中的书签总数。 */
    private int countBookmarks(Folder folder) {
        int total = folder.getBookmarks().size();
        for (Folder child : folder.getChildren().values()) {
            total += countBookmarks(child);
        }
        return total;
    }

    /** 收集整棵树中所有书签，以 url 为键建立索引。 */
    private Map<String, Bookmark> collectByUrl(Folder root) {
        List<Bookmark> all = new java.util.ArrayList<>();
        collectAll(root, all);
        return all.stream().collect(Collectors.toMap(Bookmark::getUrl, b -> b));
    }

    private void collectAll(Folder folder, List<Bookmark> out) {
        out.addAll(folder.getBookmarks());
        for (Folder child : folder.getChildren().values()) {
            collectAll(child, out);
        }
    }
}
