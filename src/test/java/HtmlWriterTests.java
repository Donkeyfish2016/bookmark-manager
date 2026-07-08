import com.bookmark.html.HtmlBookmarkWriter;
import com.bookmark.model.Bookmark;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class HtmlWriterTests {

    @Test
    void writeShouldGenerateNetscapeBookmarkHtml() throws Exception {
        HtmlBookmarkWriter writer = new HtmlBookmarkWriter();
        File outputFile = new File("output_test.html");
        if (outputFile.exists()) {
            assertTrue(outputFile.delete(), "旧测试文件应能被清理");
        }

        List<Bookmark> bookmarks = List.of(
                new Bookmark(null, "https://developer.mozilla.org/", "MDN", "icon-mdn.png", "编程开发/前端", LocalDateTime.of(2024, 1, 1, 0, 0, 1), null, null),
                new Bookmark(null, "https://spring.io/", "Spring", "icon-spring.png", "编程开发/后端/后段", LocalDateTime.of(2024, 1, 1, 0, 0, 2), null, null),
                new Bookmark(null, "https://example.com/?q=1&x=\"y\"", "A&B <Test>", null, "", LocalDateTime.of(2024, 1, 1, 0, 0, 3), null, null)
        );

        writer.write(bookmarks, outputFile);

        assertTrue(outputFile.exists(), "输出文件应被创建");
        String content = Files.readString(outputFile.toPath(), StandardCharsets.UTF_8);
        assertTrue(content.startsWith("<!DOCTYPE NETSCAPE-Bookmark-file-1>\n<!-- This is an automatically generated file.\n     It will be read and overwritten.\n     DO NOT EDIT! -->\n<META HTTP-EQUIV=\"Content-Type\" CONTENT=\"text/html; charset=UTF-8\">\n<TITLE>Bookmarks</TITLE>\n<H1>Bookmarks</H1>\n<DL><p>"), "应写入标准 Netscape 书签头部");
        assertTrue(content.contains("<DT><H3"), "应生成文件夹标题节点");
        assertTrue(content.contains(">收藏夹栏</H3>"), "应生成默认收藏夹栏根目录");
        assertTrue(content.contains(">编程开发</H3>"), "应生成按分类层级创建文件夹");
        assertTrue(content.contains(">前端</H3>"), "应生成多层分类文件夹");
        assertTrue(content.contains("ADD_DATE=\"1704067201\""), "应将 addDate 转换为 Unix 时间戳秒");
        assertTrue(content.contains("https://example.com/?q=1&amp;x=&quot;y&quot;"), "应对 URL 进行 HTML 转义");
        assertTrue(content.contains("A&amp;B &lt;Test&gt;"), "应对标题进行 HTML 转义");

        Document doc = Jsoup.parse(content, "UTF-8");
        assertEquals(3, doc.select("a").size(), "应生成 3 个书签链接");
        assertTrue(doc.select("h3").size() >= 3, "应生成至少 3 个文件夹标题");
    }
}
