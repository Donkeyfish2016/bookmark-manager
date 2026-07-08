package com.bookmark.cli;

import com.bookmark.service.BookmarkService;
import picocli.CommandLine;

import java.util.concurrent.Callable;

/**
 * <p>
 * 子命令：delete —— 按主键删除一条书签。
 * </p>
 * <p>
 * 通过构造器注入 {@link BookmarkService}，删除逻辑委托给服务层。
 * </p>
 *
 * @author DonkeyFish
 * @since 2026-7-7
 */
@CommandLine.Command(name = "delete", description = "Delete a bookmark by id.")
public class DeleteCommand implements Callable<Integer> {

    private final BookmarkService bookmarkService;

    // 1. 通过构造器注入 BookmarkService
    public DeleteCommand(BookmarkService bookmarkService) {
        this.bookmarkService = bookmarkService;
    }

    // 2. 声明必填的整数选项 --id
    @CommandLine.Option(names = {"-i", "--id"}, description = "Bookmark id to delete.", required = true)
    private int id;

    /**
     * 命令执行入口：调用服务层删除书签并输出结果。
     *
     * @return 进程退出码（成功 0，失败 1）
     */
    @Override
    public Integer call() {
        // 1. 委托服务层删除书签（返回是否命中并删除）
        boolean success = bookmarkService.delete(id);

        // 2. 根据结果输出成功或失败信息
        if (success) {
            System.out.println("success: bookmark with id " + id + " has been deleted");
            return 0;
        }
        // 服务层未命中对应记录时返回失败及原因
        System.out.println("fail: no bookmark found with id " + id);
        return 1;
    }
}
