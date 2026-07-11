package com.bookmark.cli;

import com.bookmark.service.BookmarkService;
import picocli.CommandLine;

import java.util.concurrent.Callable;

/**
 * <p>
 * 子命令：export —— 将全部书签导出为 Edge 书签 HTML 文件。
 * </p>
 * <p>
 * 通过构造器注入 {@link BookmarkService}，导出逻辑委托给服务层。
 * </p>
 *
 * @author DonkeyFish
 * @since 2026-7-8
 */
@CommandLine.Command(name = "export", description = "Export all bookmarks to an HTML file.", mixinStandardHelpOptions = true)
public class ExportCommand implements Callable<Integer> {

    private final BookmarkService bookmarkService;

    // 1. 通过构造器注入 BookmarkService
    public ExportCommand(BookmarkService bookmarkService) {
        this.bookmarkService = bookmarkService;
    }

    // 2. 声明可选的输出路径，默认 output_test.html
    @CommandLine.Option(names = {"-o", "--output"}, description = "Output HTML file path.", defaultValue = "output_test.html")
    private String output;

    /**
     * 命令执行入口：调用服务层导出并打印结果。
     *
     * @return 进程退出码（0）
     */
    @Override
    public Integer call() {
        // 1. 委托服务层导出，并获取导出数量
        int exported = bookmarkService.exportToHtml(output);

        // 2. 打印导出成功信息
        System.out.println("成功导出 " + exported + " 条书签到文件：" + output);
        return 0;
    }
}
