package com.bookmark.cli;

import com.bookmark.model.ImportResult;
import com.bookmark.service.BookmarkService;
import picocli.CommandLine;

import java.util.concurrent.Callable;

/**
 * <p>
 * 子命令：import —— 从 Edge 导出的书签 HTML 文件导入书签。
 * </p>
 * <p>
 * 通过构造器注入 {@link BookmarkService}，导入逻辑委托给服务层。
 * </p>
 *
 * @author DonkeyFish
 * @since 2026-7-8
 */
@CommandLine.Command(name = "import", description = "Import bookmarks from an Edge bookmark HTML file.", mixinStandardHelpOptions = true)
public class ImportCommand implements Callable<Integer> {

    private final BookmarkService bookmarkService;

    // 1. 通过构造器注入 BookmarkService
    public ImportCommand(BookmarkService bookmarkService) {
        this.bookmarkService = bookmarkService;
    }

    // 2. 声明必填的文件路径参数
    @CommandLine.Option(names = {"-f", "--file"}, description = "Edge bookmark HTML file to import.", required = true)
    private String file;

    /**
     * 命令执行入口：调用服务层导入并打印结果。
     *
     * @return 进程退出码（0）
     */
    @Override
    public Integer call() {
        // 1. 委托服务层导入，获取导入统计
        ImportResult result = bookmarkService.importFromHtml(file);

        // 2. 打印导入完成信息
        System.out.println("导入完成：书签 " + result.getBookmarks() + " 条，文件夹 " + result.getFolders() + " 个");

        // 3. 提示用户可用 list 命令查看导入结果
        System.out.println("可运行 'list' 命令查看已导入的书签。");
        return 0;
    }
}
