package com.bookmark.cli.folder;

import com.bookmark.model.Folder;
import com.bookmark.service.FolderService;
import picocli.CommandLine;

import java.util.List;
import java.util.concurrent.Callable;

/**
 * <p>
 * 子命令：folder add —— 创建一个新文件夹。
 * </p>
 * <p>
 * 通过 {@code --name} 指定名称，{@code --parent-id} 指定父文件夹（默认为“收藏夹栏”根），
 * {@code --root} 标记则忽略父 id 直接创建为独立根文件夹。
 * </p>
 *
 * @author DonkeyFish
 * @since 2026-7-9
 */
@CommandLine.Command(name = "add", description = "Create a new folder.")
public class FolderAddCommand implements Callable<Integer> {

    /** 根文件夹的默认名称（收藏夹栏）。 */
    private static final String ROOT_FOLDER_NAME = "收藏夹栏";

    private final FolderService folderService;

    // 1. 通过构造器注入 FolderService
    public FolderAddCommand(FolderService folderService) {
        this.folderService = folderService;
    }

    // 2. 声明必填的文件夹名称
    @CommandLine.Option(names = {"-n", "--name"}, description = "Folder name.", required = true)
    private String name;

    // 3. 声明可选的父文件夹 id，未提供时回退到根文件夹
    @CommandLine.Option(names = {"-p", "--parent-id"},
            description = "Parent folder id. Defaults to the Favorites Bar root.")
    private Integer parentId;

    // 4. 声明可选的“根文件夹”标记，优先于 --parent-id
    @CommandLine.Option(names = {"-r", "--root"},
            description = "Create as a root folder (ignores --parent-id).")
    private boolean root;

    /**
     * 命令执行入口：解析参数并创建文件夹。
     *
     * @return 进程退出码（成功 0，失败 1）
     */
    @Override
    public Integer call() {
        // 1. 非根模式且未指定父 id 时，解析根文件夹 id 作为默认父级
        if (!root && parentId == null) {
            parentId = resolveRootId();
        }

        // 2. 调用服务层创建，并输出结果或错误信息
        try {
            int newId = folderService.createFolder(name, parentId, root);
            System.out.println("Folder created with ID: " + newId);
            return 0;
        } catch (IllegalArgumentException e) {
            System.out.println("创建失败：" + e.getMessage());
            return 1;
        }
    }

    /**
     * 解析根文件夹 id：优先按名称“收藏夹栏”查找，否则取第一个顶级文件夹。
     */
    private Integer resolveRootId() {
        for (Folder folder : folderService.getAllFolders()) {
            if (ROOT_FOLDER_NAME.equals(folder.getName())) {
                return folder.getId();
            }
        }
        for (Folder folder : folderService.getAllFolders()) {
            if (folder.getParentId() == null) {
                return folder.getId();
            }
        }
        return null;
    }
}
