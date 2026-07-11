package com.bookmark.cli.folder;

import com.bookmark.model.Folder;
import com.bookmark.service.FolderService;
import picocli.CommandLine;

import java.util.concurrent.Callable;

/**
 * <p>
 * 子命令：folder move —— 将文件夹移动到另一个父文件夹下。
 * </p>
 * <p>
 * 通过 {@code --id} 指定待移动文件夹，{@code --parent-id} 指定目标父文件夹，
 * 输出旧/新完整路径以反映移动结果。
 * </p>
 *
 * @author DonkeyFish
 * @since 2026-7-9
 */
@CommandLine.Command(name = "move", description = "Move a folder to a different parent folder.", mixinStandardHelpOptions = true)
public class FolderMoveCommand implements Callable<Integer> {

    private final FolderService folderService;

    // 1. 通过构造器注入 FolderService
    public FolderMoveCommand(FolderService folderService) {
        this.folderService = folderService;
    }

    // 2. 声明必填的待移动文件夹 id
    @CommandLine.Option(names = {"-i", "--id"}, description = "Folder id to move.", required = true)
    private int id;

    // 3. 声明必填的目标父文件夹 id
    @CommandLine.Option(names = {"-p", "--parent-id"},
            description = "Target parent folder id.", required = true)
    private int parentId;

    /**
     * 命令执行入口：记录移动前路径，执行移动后输出新旧完整路径。
     *
     * @return 进程退出码（成功 0，失败 1）
     */
    @Override
    public Integer call() {
        // 1. 查询待移动文件夹；不存在则报错退出
        Folder folder = folderService.getFolderById(id);
        if (folder == null) {
            System.out.println("移动失败：未找到 ID=" + id + " 的文件夹");
            return 1;
        }
        // 2. 记录移动前的完整路径
        String oldPath = buildFullPath(folder);

        // 3. 调用服务层移动，并输出结果或错误信息
        try {
            folderService.moveFolder(id, parentId);
        } catch (IllegalArgumentException | IllegalStateException e) {
            System.out.println("移动失败：" + e.getMessage());
            return 1;
        }

        // 4. 重新查询以构建移动后的完整路径并输出
        Folder moved = folderService.getFolderById(id);
        String newPath = moved != null ? buildFullPath(moved) : oldPath;
        System.out.println("文件夹 " + oldPath + " 已移动至 " + newPath);
        return 0;
    }

    /**
     * 由文件夹向上回溯父链，拼接形如 {@code 父/子/孙} 的完整路径。
     */
    private String buildFullPath(Folder folder) {
        StringBuilder path = new StringBuilder(folder.getName());
        Integer current = folder.getParentId();
        while (current != null) {
            Folder parent = folderService.getFolderById(current);
            if (parent == null) {
                break;
            }
            path.insert(0, parent.getName() + "/");
            current = parent.getParentId();
        }
        return path.toString();
    }
}
