package com.bookmark.cli.folder;

import com.bookmark.model.Folder;
import com.bookmark.service.FolderService;
import picocli.CommandLine;

import java.util.concurrent.Callable;

/**
 * <p>
 * 子命令：folder delete —— 删除一个已存在的文件夹。
 * </p>
 * <p>
 * 通过 {@code --id} 指定目标文件夹；仅当文件夹为空（无子文件夹、无书签）时方可删除。
 * </p>
 *
 * @author DonkeyFish
 * @since 2026-7-9
 */
@CommandLine.Command(name = "delete", description = "Delete an existing folder.")
public class FolderDeleteCommand implements Callable<Integer> {

    private final FolderService folderService;

    // 1. 通过构造器注入 FolderService
    public FolderDeleteCommand(FolderService folderService) {
        this.folderService = folderService;
    }

    // 2. 声明必填的文件夹 id
    @CommandLine.Option(names = {"-i", "--id"}, description = "Folder id to delete.", required = true)
    private int id;

    /**
     * 命令执行入口：先取文件夹名称，再执行删除并输出结果。
     *
     * @return 进程退出码（成功 0，失败 1）
     */
    @Override
    public Integer call() {
        // 1. 查询目标文件夹以获取名称；不存在则报错退出
        Folder folder = folderService.getFolderById(id);
        if (folder == null) {
            System.out.println("删除失败：未找到 ID=" + id + " 的文件夹");
            return 1;
        }
        String folderName = folder.getName();

        // 2. 调用服务层删除，并输出结果或错误信息
        try {
            folderService.deleteFolder(id);
            System.out.println("Folder deleted: " + folderName);
            return 0;
        } catch (IllegalStateException | IllegalArgumentException e) {
            System.out.println("删除失败：" + e.getMessage());
            return 1;
        }
    }
}
