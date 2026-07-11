package com.bookmark.cli.folder;

import com.bookmark.model.Folder;
import com.bookmark.service.FolderService;
import picocli.CommandLine;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

/**
 * <p>
 * 子命令：folder tree —— 以 ASCII 树状结构递归展示文件夹层级。
 * </p>
 * <p>
 * 从根文件夹（parent_id 为 NULL 的文件夹）开始递归遍历，
 * 每行展示文件夹名称及其直接包含的书签数量。
 * </p>
 *
 * @author DonkeyFish
 * @since 2026-7-9
 */
@CommandLine.Command(name = "tree", description = "Display the folder hierarchy as an ASCII tree.")
public class FolderTreeCommand implements Callable<Integer> {

    private final FolderService folderService;

    // 1. 通过构造器注入 FolderService
    public FolderTreeCommand(FolderService folderService) {
        this.folderService = folderService;
    }

    /**
     * 命令执行入口：定位根文件夹并递归打印树。
     *
     * @return 进程退出码（0）
     */
    @Override
    public Integer call() {
        // 1. 加载全部文件夹并定位根文件夹（parent_id 为 NULL）
        List<Folder> allFolders = folderService.getAllFolders();
        List<Folder> roots = allFolders.stream()
                .filter(f -> f.getParentId() == null)
                .collect(Collectors.toList());

        // 2. 无根文件夹时给出提示并退出
        if (roots == null) {
            System.out.println("（暂无文件夹）");
            return 0;
        }

        // 3. 打印根节点，再递归打印其子节点
        for (Folder root : roots) {
            System.out.println(root.getName() + " (id=" + root.getId() + ", " + directBookmarkCount(root) + " bookmarks)");
            List<Folder> children = folderService.getSubFolders(root.getId());
            for (int i = 0; i < children.size(); i++) {
                printNode(children.get(i), "", i == children.size() - 1);
            }
        }
        return 0;
    }

    /**
     * 递归打印单个文件夹节点及其子树。
     *
     * @param folder 当前文件夹节点
     * @param indent 当前累计缩进前缀
     * @param isLast 是否为同级最后一个节点（影响连接符与后续缩进）
     */
    private void printNode(Folder folder, String indent, boolean isLast) {
        // 1. 使用 ├── / └── 表示当前节点与父节点的连接关系
        String branch = isLast ? "└── " : "├── ";
        System.out.println(indent + branch
                + folder.getName() + " (id=" + folder.getId() + ", " + directBookmarkCount(folder) + " bookmarks)");

        // 2. 计算子节点的缩进：末尾节点用空格，否则用竖线占位
        String childIndent = indent + (isLast ? "    " : "│   ");
        List<Folder> children = folderService.getSubFolders(folder.getId());
        for (int i = 0; i < children.size(); i++) {
            printNode(children.get(i), childIndent, i == children.size() - 1);
        }
    }

    /** 统计该文件夹直接包含的书签数量。 */
    private int directBookmarkCount(Folder folder) {
        return folderService.getBookmarksByFolder(folder.getId()).size();
    }
}
