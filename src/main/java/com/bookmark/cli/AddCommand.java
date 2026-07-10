package com.bookmark.cli;

import com.bookmark.model.Bookmark;
import com.bookmark.service.BookmarkService;
import picocli.CommandLine;

import java.util.concurrent.Callable;

/**
 * <p>
 * 子命令：add —— 新增一条书签。
 * </p>
 * <p>
 * 通过构造器注入 {@link BookmarkService}，所有校验与持久化逻辑委托给服务层。
 * </p>
 *
 * @author DonkeyFish
 * @since 2026-7-7
 */
@CommandLine.Command(name = "add", description = "Add a new bookmark.")
public class AddCommand implements Callable<Integer> {

    private final BookmarkService bookmarkService;

    // 1. 通过构造器注入 BookmarkService
    public AddCommand(BookmarkService bookmarkService) {
        this.bookmarkService = bookmarkService;
    }

    // 2. 声明各必填选项
    @CommandLine.Option(names = {"-u", "--url"}, description = "Bookmark URL.", required = true)
    private String url;

    @CommandLine.Option(names = {"-t", "--title"}, description = "Bookmark title.", required = true)
    private String title;

    @CommandLine.Option(names = {"-i", "--icon"}, description = "Icon URL or path.", required = false)
    private String icon;

    @CommandLine.Option(names = {"-c", "--category"}, description = "Category or folder name.", defaultValue = "收藏夹栏", required = false)
    private String category;

    /**
     * 命令执行入口：调用服务层新增书签并输出结果。
     *
     * @return 进程退出码（0 表示成功）
     */
    @Override
    public Integer call() {
        // 1. 委托服务层新增书签（服务层负责校验与持久化）
        Bookmark saved = bookmarkService.add(url, title, icon, category);

        // 2. 输出新增结果
        System.out.printf("Added bookmark: id=%d, url=%s, category=%s%n",
                saved.getId(), saved.getUrl(), saved.getCategory());
        return 0;
    }
}
