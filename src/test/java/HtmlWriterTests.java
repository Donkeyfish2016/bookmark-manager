import com.bookmark.html.HtmlBookmarkWriter;
import com.bookmark.model.Bookmark;
import com.bookmark.model.Folder;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.*;

public class HtmlWriterTests {

    @Test
    void writeShouldGenerateNetscapeBookmarkHtml() throws Exception {
        HtmlBookmarkWriter writer = new HtmlBookmarkWriter();
        File outputFile = new File("output_test.html");
        if (outputFile.exists()) {
            assertTrue(outputFile.delete(), "旧测试文件应能被清理");
        }

        Folder root = buildTestFolderTree();
        writer.write(root, outputFile);

        assertTrue(outputFile.exists(), "输出文件应被创建");
        String content = Files.readString(outputFile.toPath(), StandardCharsets.UTF_8);
        assertTrue(content.startsWith("<!DOCTYPE NETSCAPE-Bookmark-file-1>\n<!-- This is an automatically generated file.\n     It will be read and overwritten.\n     DO NOT EDIT! -->\n<META HTTP-EQUIV=\"Content-Type\" CONTENT=\"text/html; charset=UTF-8\">\n<TITLE>Bookmarks</TITLE>\n<H1>Bookmarks</H1>\n<DL><p>"), "应写入标准 Netscape 书签头部");
        assertTrue(content.contains("<DT><H3"), "应生成文件夹标题节点");
        assertTrue(content.contains("PERSONAL_TOOLBAR_FOLDER=\"true\""), "收藏夹栏应有 PERSONAL_TOOLBAR_FOLDER 属性");
        assertTrue(content.contains(">收藏夹栏</H3>"), "应生成默认收藏夹栏根目录");
        assertTrue(content.contains(">编程开发</H3>"), "应生成顶级文件夹");
        assertTrue(content.contains(">前端</H3>"), "应生成子文件夹");

        Document doc = Jsoup.parse(content, "UTF-8");
        assertTrue(doc.select("h3").size() >= 3, "应生成至少 3 个文件夹标题");
    }

    @Test
    void shouldHandleEmptyFolders() throws Exception {
        HtmlBookmarkWriter writer = new HtmlBookmarkWriter();
        File outputFile = new File("output__empty_test.html");
        if (outputFile.exists()) {
            assertTrue(outputFile.delete(), "旧测试文件应能被清理");
        }

        Folder root = new Folder();
        root.setName("ROOT");
        root.setChildren(new LinkedHashMap<>());
        root.setBookmarks(new ArrayList<>());

        Folder emptyFolder = new Folder();
        emptyFolder.setName("空文件夹");
        emptyFolder.setChildren(new LinkedHashMap<>());
        emptyFolder.setBookmarks(new ArrayList<>());
        root.getChildren().put("空文件夹", emptyFolder);

        writer.write(root, outputFile);

        String content = Files.readString(outputFile.toPath(), StandardCharsets.UTF_8);
        assertTrue(content.contains(">空文件夹</H3>"), "空文件夹应被输出");
        assertTrue(content.contains("<DL></DL>") || content.contains("<DL><p>\n</DL>"), "空文件夹应有空的 DL 标签");
    }

    @Test
    void shouldHandleDeepNesting() throws Exception {
        HtmlBookmarkWriter writer = new HtmlBookmarkWriter();
        File outputFile = new File("output_nested_test.html");
        if (outputFile.exists()) {
            assertTrue(outputFile.delete(), "旧测试文件应能被清理");
        }

        Folder root = new Folder();
        root.setName("ROOT");
        root.setChildren(new LinkedHashMap<>());
        root.setBookmarks(new ArrayList<>());

        Folder level1 = new Folder();
        level1.setName("层级1");
        level1.setChildren(new LinkedHashMap<>());
        level1.setBookmarks(new ArrayList<>());

        Folder level2 = new Folder();
        level2.setName("层级2");
        level2.setChildren(new LinkedHashMap<>());
        level2.setBookmarks(new ArrayList<>());

        Folder level3 = new Folder();
        level3.setName("层级3");
        level3.setChildren(new LinkedHashMap<>());
        level3.setBookmarks(new ArrayList<>());

        Bookmark deepBookmark = new Bookmark();
        deepBookmark.setUrl("https://deep.example.com/");
        deepBookmark.setTitle("深层书签");
        deepBookmark.setAddDate(LocalDateTime.of(2024, 1, 1, 0, 0, 0));
        level3.getBookmarks().add(deepBookmark);

        level2.getChildren().put("层级3", level3);
        level1.getChildren().put("层级2", level2);
        root.getChildren().put("层级1", level1);

        writer.write(root, outputFile);

        String content = Files.readString(outputFile.toPath(), StandardCharsets.UTF_8);
        assertTrue(content.contains(">层级1</H3>"), "应包含第一层");
        assertTrue(content.contains(">层级2</H3>"), "应包含第二层");
        assertTrue(content.contains(">层级3</H3>"), "应包含第三层");
        assertTrue(content.contains("https://deep.example.com/"), "应包含深层书签");
    }

    @Test
    void shouldEscapeSpecialCharacters() throws Exception {
        HtmlBookmarkWriter writer = new HtmlBookmarkWriter();
        File outputFile = new File("output_escape_test.html");
        if (outputFile.exists()) {
            assertTrue(outputFile.delete(), "旧测试文件应能被清理");
        }

        Folder root = new Folder();
        root.setName("ROOT");
        root.setChildren(new LinkedHashMap<>());
        root.setBookmarks(new ArrayList<>());

        Folder specialFolder = new Folder();
        specialFolder.setName("A&B <Test>");
        specialFolder.setChildren(new LinkedHashMap<>());
        specialFolder.setBookmarks(new ArrayList<>());
        root.getChildren().put("A&B <Test>", specialFolder);

        Bookmark specialBookmark = new Bookmark();
        specialBookmark.setUrl("https://example.com/?q=1&x=\"y\"");
        specialBookmark.setTitle("A&B <Test>\"Quote\"");
        specialBookmark.setAddDate(LocalDateTime.of(2024, 1, 1, 0, 0, 0));
        specialFolder.getBookmarks().add(specialBookmark);

        writer.write(root, outputFile);

        String content = Files.readString(outputFile.toPath(), StandardCharsets.UTF_8);
        assertTrue(content.contains("A&amp;B &lt;Test&gt;"), "应转义文件夹名中的 & 和 < >");
        assertTrue(content.contains("https://example.com/?q=1&amp;x=&quot;y&quot;"), "应转义 URL 中的 & 和 \"");
        assertTrue(content.contains("A&amp;B &lt;Test&gt;&quot;Quote&quot;"), "应转义标题中的 &、<、> 和 \"");
    }

    @Test
    void shouldUseCurrentTimestampForNullDates() throws Exception {
        HtmlBookmarkWriter writer = new HtmlBookmarkWriter();
        File outputFile = new File("output_null_date_test.html");
        if (outputFile.exists()) {
            assertTrue(outputFile.delete(), "旧测试文件应能被清理");
        }

        Folder root = new Folder();
        root.setName("ROOT");
        root.setChildren(new LinkedHashMap<>());
        root.setBookmarks(new ArrayList<>());

        Folder folder = new Folder();
        folder.setName("无日期文件夹");
        folder.setAddDate(null);
        folder.setChildren(new LinkedHashMap<>());
        folder.setBookmarks(new ArrayList<>());
        root.getChildren().put("无日期文件夹", folder);

        Bookmark bookmark = new Bookmark();
        bookmark.setUrl("https://example.com/");
        bookmark.setTitle("无日期书签");
        bookmark.setAddDate(null);
        folder.getBookmarks().add(bookmark);

        writer.write(root, outputFile);

        String content = Files.readString(outputFile.toPath(), StandardCharsets.UTF_8);
        assertTrue(content.contains("ADD_DATE=\""), "应有 ADD_DATE 属性");
        assertFalse(content.contains("ADD_DATE=\"0\""), "不应使用默认时间戳 0");
    }

    @Test
    void shouldNotAddToolbarAttributeToNonDefaultFolder() throws Exception {
        HtmlBookmarkWriter writer = new HtmlBookmarkWriter();
        File outputFile = new File("output_notoolbar_test.html");
        if (outputFile.exists()) {
            assertTrue(outputFile.delete(), "旧测试文件应能被清理");
        }

        Folder root = new Folder();
        root.setName("ROOT");
        root.setChildren(new LinkedHashMap<>());
        root.setBookmarks(new ArrayList<>());

        Folder normalFolder = new Folder();
        normalFolder.setName("普通文件夹");
        normalFolder.setChildren(new LinkedHashMap<>());
        normalFolder.setBookmarks(new ArrayList<>());
        root.getChildren().put("普通文件夹", normalFolder);

        writer.write(root, outputFile);

        String content = Files.readString(outputFile.toPath(), StandardCharsets.UTF_8);
        assertTrue(content.contains(">普通文件夹</H3>"), "应包含普通文件夹");
        assertFalse(content.contains("PERSONAL_TOOLBAR_FOLDER") && content.contains("普通文件夹"),
                "普通文件夹不应有 PERSONAL_TOOLBAR_FOLDER 属性");
    }

    private Folder buildTestFolderTree() {
        Folder root = new Folder();
        root.setName("ROOT");
        root.setChildren(new LinkedHashMap<>());
        root.setBookmarks(new ArrayList<>());

        Folder toolbarFolder = new Folder();
        toolbarFolder.setName("收藏夹栏");
        toolbarFolder.setAddDate(LocalDateTime.of(2024, 1, 1, 0, 0, 1));
        toolbarFolder.setChildren(new LinkedHashMap<>());
        toolbarFolder.setBookmarks(new ArrayList<>());

        Bookmark mdn = new Bookmark();
        mdn.setUrl("https://developer.mozilla.org/");
        mdn.setTitle("MDN");
        mdn.setIcon("icon-mdn.png");
        mdn.setAddDate(LocalDateTime.of(2024, 1, 1, 0, 0, 1));
        toolbarFolder.getBookmarks().add(mdn);

        Folder devFolder = new Folder();
        devFolder.setName("编程开发");
        devFolder.setAddDate(LocalDateTime.of(2024, 1, 1, 0, 0, 2));
        devFolder.setChildren(new LinkedHashMap<>());
        devFolder.setBookmarks(new ArrayList<>());

        Folder frontendFolder = new Folder();
        frontendFolder.setName("前端");
        frontendFolder.setAddDate(LocalDateTime.of(2024, 1, 1, 0, 0, 3));
        frontendFolder.setChildren(new LinkedHashMap<>());
        frontendFolder.setBookmarks(new ArrayList<>());

        Bookmark vue = new Bookmark();
        vue.setUrl("https://vuejs.org/");
        vue.setTitle("Vue.js");
        vue.setAddDate(LocalDateTime.of(2024, 1, 1, 0, 0, 4));
        frontendFolder.getBookmarks().add(vue);

        Bookmark react = new Bookmark();
        react.setUrl("https://react.dev/");
        react.setTitle("React");
        react.setAddDate(LocalDateTime.of(2024, 1, 1, 0, 0, 5));
        frontendFolder.getBookmarks().add(react);

        Bookmark ts = new Bookmark();
        ts.setUrl("https://www.typescriptlang.org/");
        ts.setTitle("TypeScript");
        ts.setAddDate(LocalDateTime.of(2024, 1, 1, 0, 0, 6));
        frontendFolder.getBookmarks().add(ts);

        devFolder.getChildren().put("前端", frontendFolder);

        Bookmark spring = new Bookmark();
        spring.setUrl("https://spring.io/");
        spring.setTitle("Spring");
        spring.setAddDate(LocalDateTime.of(2024, 1, 1, 0, 0, 7));
        devFolder.getBookmarks().add(spring);

        root.getChildren().put("收藏夹栏", toolbarFolder);
        root.getChildren().put("编程开发", devFolder);

        return root;
    }
}