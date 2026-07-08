package com.bookmark.cli;

import com.bookmark.model.Bookmark;
import com.bookmark.service.BookmarkService;
import picocli.CommandLine;

import java.util.List;
import java.util.concurrent.Callable;

/**
 * <p>
 * 子命令：list —— 分页列出书签（可按分类过滤）。
 * </p>
 * <p>
 * 通过构造器注入 {@link BookmarkService}，查询逻辑委托给服务层。
 * </p>
 *
 * @author DonkeyFish
 * @since 2026-7-7
 */
@CommandLine.Command(name = "list", description = "List bookmarks (optionally filtered by category, paginated).")
public class ListCommand implements Callable<Integer> {

    private final BookmarkService bookmarkService;

    // 1. 通过构造器注入 BookmarkService
    public ListCommand(BookmarkService bookmarkService) {
        this.bookmarkService = bookmarkService;
    }

    // 2. 声明可选选项：分类、页码、每页大小
    @CommandLine.Option(names = {"-c", "--category"}, description = "Filter by category. Omit for all.")
    private String category;

    @CommandLine.Option(names = {"-p", "--page"}, description = "Page number (default: 1).", defaultValue = "1")
    private int page;

    @CommandLine.Option(names = {"-s", "--size"}, description = "Items per page (default: 10).", defaultValue = "10")
    private int size;

    /**
     * 命令执行入口：查询并以 JSON 形式输出结果。
     *
     * @return 进程退出码（0）
     */
    @Override
    public Integer call() {
        // 1. 委托服务层分页查询
        List<Bookmark> bookmarks = bookmarkService.list(category, page, size);

        // 2. 空结果输出提示信息
        if (bookmarks.isEmpty()) {
            System.out.println("No bookmarks found.");
            return 0;
        }

        // 3. 逐条以 JSON 对象形式输出
        for (Bookmark b : bookmarks) {
            System.out.println(toJson(b));
        }
        return 0;
    }

    /** 将书签序列化为单行 JSON 对象。 */
    private String toJson(Bookmark b) {
        return "{" +
                "\"id\":" + jsonValue(b.getId()) + "," +
                "\"url\":" + jsonValue(b.getUrl()) + "," +
                "\"title\":" + jsonValue(b.getTitle()) + "," +
                "\"icon\":" + jsonValue(b.getIcon()) + "," +
                "\"category\":" + jsonValue(b.getCategory()) + "," +
                "\"addDate\":" + jsonValue(b.getAddDate()) + "," +
                "\"createTime\":" + jsonValue(b.getCreateTime()) + "," +
                "\"updateTime\":" + jsonValue(b.getUpdateTime()) +
                "}";
    }

    /** 字符串加引号转义；其余类型直接 toString；null 输出 null。 */
    private String jsonValue(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof String s) {
            return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
        }
        return value.toString();
    }
}
