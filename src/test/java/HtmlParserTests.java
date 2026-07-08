import com.bookmark.html.HtmlBookmarkParser;
import com.bookmark.model.Bookmark;

import org.junit.jupiter.api.*;

import java.io.File;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * HtmlBookmarkParser 解析测试：以 example.html 为基准校验解析结果。
 */
class HtmlParserTests {

    private HtmlBookmarkParser parser;

    @BeforeEach
    void setUp() {
        parser = new HtmlBookmarkParser();
    }

    // 1. 解析基准文件
    // 2. 断言总数、分类层级、字段映射与 ADD_DATE 的 ISO 转换均正确
    @Test
    void testParseExampleFile() throws Exception {
        // 1. 加载位于源码目录下的基准 HTML 文件
        File file = new File("src/main/java/com/bookmark/html/example.html");
        assertTrue(file.exists(), "基准文件应存在");

        // 2. 执行解析
        List<Bookmark> bookmarks = parser.parse(file);

        // 3. 断言共解析出 12 条书签（4 + 3 + 3 + 2）
        assertEquals(12, bookmarks.size(), "应解析出 12 条书签");

        // 4. 以 url 为键建立索引，便于按字段断言
        Map<String, Bookmark> byUrl = bookmarks.stream()
                .collect(Collectors.toMap(Bookmark::getUrl, b -> b));

        // 5. 单层文件夹（收藏夹栏）下的书签
        Bookmark bing = byUrl.get("https://www.bing.com/");
        assertNotNull(bing);
        assertEquals("必应", bing.getTitle());
        assertEquals("收藏夹栏", bing.getCategory());
        assertEquals(expectedDate(1700000001L), bing.getAddDate());

        Bookmark github = byUrl.get("https://github.com/");
        assertNotNull(github);
        assertEquals("GitHub", github.getTitle());
        assertEquals("收藏夹栏", github.getCategory());

        // 6. 嵌套文件夹（编程开发/前端）下的书签
        Bookmark vue = byUrl.get("https://vuejs.org/");
        assertNotNull(vue);
        assertEquals("Vue.js", vue.getTitle());
        assertEquals("编程开发/前端", vue.getCategory());

        Bookmark typescript = byUrl.get("https://www.typescriptlang.org/");
        assertNotNull(typescript);
        assertEquals("编程开发/前端", typescript.getCategory());

        // 7. 嵌套文件夹（编程开发/后端）下的书签
        Bookmark spring = byUrl.get("https://spring.io/");
        assertNotNull(spring);
        assertEquals("Spring", spring.getTitle());
        assertEquals("编程开发/后端", spring.getCategory());

        Bookmark django = byUrl.get("https://www.djangoproject.com/");
        assertNotNull(django);
        assertEquals("编程开发/后端", django.getCategory());

        // 8. 另一单层文件夹（学习资源）下的书签
        Bookmark mdn = byUrl.get("https://developer.mozilla.org/zh-CN/");
        assertNotNull(mdn);
        assertEquals("MDN Web 文档", mdn.getTitle());
        assertEquals("学习资源", mdn.getCategory());

        // 9. icon 字段应被正确提取（data URI）
        assertNotNull(bing.getIcon());
        assertTrue(bing.getIcon().startsWith("data:image/png;base64,"), "icon 应为 data URI");

        // 10. 全部书签的 addDate 均已转换为非空的 LocalDateTime
        assertTrue(bookmarks.stream().allMatch(b -> b.getAddDate() != null));
    }

    /** 与解析器一致的 Unix 秒 -> UTC LocalDateTime 转换，避免重复硬编码。 */
    private LocalDateTime expectedDate(long epochSecond) {
        return LocalDateTime.ofInstant(Instant.ofEpochSecond(epochSecond), ZoneOffset.UTC);
    }
}
