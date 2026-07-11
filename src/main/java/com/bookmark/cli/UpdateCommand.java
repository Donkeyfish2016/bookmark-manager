package com.bookmark.cli;

import com.bookmark.model.Bookmark;
import com.bookmark.service.BookmarkService;
import picocli.CommandLine;

import java.util.concurrent.Callable;

/**
 * <p>
 * 子命令：update —— 修改已有书签的字段（仅更新提供的字段）。
 * </p>
 * <p>
 * 通过构造器注入 {@link BookmarkService}，先读取原记录以保留未提供的字段，
 * 再合并命令行参数并委托服务层持久化。
 * </p>
 *
 * @author DonkeyFish
 * @since 2026-7-7
 */
@CommandLine.Command(name = "update", description = "Update an existing bookmark.", mixinStandardHelpOptions = true)
public class UpdateCommand implements Callable<Integer> {

    private final BookmarkService bookmarkService;

    // 1. 通过构造器注入 BookmarkService
    public UpdateCommand(BookmarkService bookmarkService) {
        this.bookmarkService = bookmarkService;
    }

    // 2. 声明选项：id 必填，其余字段可选
    @CommandLine.Option(names = {"-i", "--id"}, description = "Bookmark id to update.", required = true)
    private Integer id;

    @CommandLine.Option(names = {"-u", "--url"}, description = "New URL.")
    private String url;

    @CommandLine.Option(names = {"-t", "--title"}, description = "New title.")
    private String title;

    @CommandLine.Option(names = {"--icon"}, description = "New icon URL or path.")
    private String icon;

    @CommandLine.Option(names = {"-c", "--category"}, description = "New category or folder name.")
    private String category;

    /**
     * 命令执行入口：合并字段后调用服务层更新并输出结果。
     *
     * @return 进程退出码（成功 0，失败 1）
     */
    @Override
    public Integer call() {
        // TODO: 从逻辑上看这些可能放业务层更合适一些
        // 1. 读取现有记录；不存在则报错退出
        Bookmark existing = bookmarkService.getById(id);
        if (existing == null) {
            System.out.println("更新失败：未找到 ID=" + id + " 的书签");
            return 1;
        }

        // 2. 仅用命令行提供的字段覆盖，其余字段保持原值
        String newUrl = (url != null) ? url : existing.getUrl();
        String newTitle = (title != null) ? title : existing.getTitle();
        String newIcon = (icon != null) ? icon : existing.getIcon();
        String newCategory = (category != null) ? category : existing.getCategory();

        // 3. 调用服务层更新
        boolean updated = bookmarkService.update(id, newUrl, newTitle, newIcon, newCategory);
        if (updated) {
            // 4. 输出更新成功信息及更新后的数据
            System.out.println("书签(ID=" + id + ")已更新：[url=" + newUrl
                    + ", title=" + newTitle + ", icon=" + newIcon + ", category=" + newCategory + "]");
            return 0;
        }

        // 5. 未命中记录时输出失败信息
        System.out.println("更新失败：ID=" + id + " 的书签不存在");
        return 1;
    }
}
