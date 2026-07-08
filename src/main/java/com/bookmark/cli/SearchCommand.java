package com.bookmark.cli;

import com.bookmark.model.Bookmark;
import com.bookmark.service.BookmarkService;
import picocli.CommandLine;

import java.util.List;
import java.util.concurrent.Callable;

/**
 * <p>
 * 子命令：search —— 按关键字模糊搜索书签（匹配 url 与 title）。
 * </p>
 * <p>
 * 通过构造器注入 {@link BookmarkService}，搜索逻辑委托给服务层。
 * </p>
 *
 * @author DonkeyFish
 * @since 2026-7-7
 */
@CommandLine.Command(name = "search", description = "Search bookmarks by keyword (matches url and title).")
public class SearchCommand implements Callable<Integer> {

    private final BookmarkService bookmarkService;

    // 1. 通过构造器注入 BookmarkService
    public SearchCommand(BookmarkService bookmarkService) {
        this.bookmarkService = bookmarkService;
    }

    // 2. 声明必填选项：关键字
    @CommandLine.Option(names = {"-k", "--keyword"}, description = "Search keyword.", required = true)
    private String keyword;

    /**
     * 命令执行入口：搜索并以 JSON 形式输出结果。
     *
     * @return 进程退出码（0）
     */
    @Override
    public Integer call() {
        // 1. 委托服务层按关键字搜索
        List<Bookmark> bookmarks = bookmarkService.search(keyword);

        // 2. 空结果输出提示信息
        if (bookmarks.isEmpty()) {
            System.out.println("No bookmarks found for keyword: " + keyword);
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
