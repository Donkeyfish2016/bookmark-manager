package com.bookmark.cli.folder;

import com.bookmark.model.Folder;
import com.bookmark.service.FolderService;
import picocli.CommandLine;

import java.util.concurrent.Callable;

/**
 * <p>
 * 子命令：folder rename —— 重命名一个已存在的文件夹。
 * </p>
 *
 * @author DonkeyFish
 * @since 2026-7-9
 */
@CommandLine.Command(name = "rename", description = "Rename an existing folder.")
public class FolderRenameCommand implements Callable<Integer> {

    private final FolderService folderService;

    // 1. 通过构造器注入 FolderService
    public FolderRenameCommand(FolderService folderService) {
        this.folderService = folderService;
    }

    // 2. 声明必填的文件夹 id
    @CommandLine.Option(names = {"-i", "--id"}, description = "Folder id to rename.", required = true)
    private int id;

    // 3. 声明必填的新名称
    @CommandLine.Option(names = {"-n", "--name"}, description = "New folder name.", required = true)
    private String name;

    /**
     * 命令执行入口：先取旧名称，再执行重命名并输出结果。
     *
     * @return 进程退出码（成功 0，失败 1）
     */
    @Override
    public Integer call() {
        // 1. 查询目标文件夹以获取旧名称；不存在则报错退出
        Folder folder = folderService.getFolderById(id);
        if (folder == null) {
            System.out.println("重命名失败：未找到 ID=" + id + " 的文件夹");
            return 1;
        }
        String oldName = folder.getName();

        // 2. 调用服务层重命名，并输出结果或错误信息
        try {
            folderService.renameFolder(id, name);
            System.out.println("文件夹 " + oldName + " 已重命名为 " + name);
            return 0;
        } catch (IllegalArgumentException e) {
            System.out.println("重命名失败：" + e.getMessage());
            return 1;
        }
    }
}
