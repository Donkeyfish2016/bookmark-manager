package com.bookmark.cli.folder;

import com.bookmark.model.Folder;
import com.bookmark.service.FolderService;
import picocli.CommandLine;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

/**
 * <p>
 * 子命令：folder list —— 以表格形式展示某父文件夹下的直接子文件夹。
 * </p>
 * <p>
 * 表格列包含：ID、Name、Bookmark Count（直接书签数）、Last Modified。
 * 未指定 {@code --parent-id} 时，默认展示根文件夹“收藏夹栏”的直接子文件夹。
 * </p>
 *
 * @author DonkeyFish
 * @since 2026-7-9
 */
@CommandLine.Command(name = "list", description = "List immediate subfolders of a parent folder in a table.", mixinStandardHelpOptions = true)
public class FolderListCommand implements Callable<Integer> {

    /** 时间字段显示格式，与 DAO 中存储格式保持一致。 */
    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 根文件夹的默认名称（收藏夹栏）。 */
    private static final String ROOT_FOLDER_NAME = "收藏夹栏";

    private final FolderService folderService;

    // 1. 通过构造器注入 FolderService
    public FolderListCommand(FolderService folderService) {
        this.folderService = folderService;
    }

    // 2. 声明可选的父文件夹 id，未提供时回退到根文件夹
    @CommandLine.Option(names = {"-p", "--parent-id"},
            description = "Parent folder id. Defaults to the root folder '" + ROOT_FOLDER_NAME + "'.")
    private Integer parentId;

    /**
     * 命令执行入口：解析父文件夹并输出子文件夹表格。
     *
     * @return 进程退出码（0）
     */
    @Override
    public Integer call() {
        // 1. 未指定父 id 时解析根文件夹 id
        if (parentId == null) {
            parentId = resolveRootId();
        }

        // 2. 查询直接子文件夹（parentId 为 NULL 时列顶级文件夹）
        List<Folder> subFolders;
        if (parentId == null) {
            subFolders = folderService.getAllFolders().stream()
                    .filter(f -> f.getParentId() == null)
                    .collect(Collectors.toList());
        } else {
            subFolders = folderService.getSubFolders(parentId);
        }

        // 3. 打印表格
        printTable(subFolders);
        return 0;
    }

    /**
     * 将子文件夹列表渲染为对齐的表格。
     */
    private void printTable(List<Folder> subFolders) {
        // 1. 打印表头
        System.out.printf("%-5s %-20s %-14s %s%n", "ID", "Name", "Bookmark Count", "Last Modified");
        System.out.println("----- -------------------- -------------- -------------------");

        // 2. 逐行打印每个子文件夹
        for (Folder folder : subFolders) {
            int count = folderService.getBookmarksByFolder(folder.getId()).size();
            String lastModified = formatTime(folder.getLastModified());
            System.out.printf("%-5d %-20s %-14d %s%n",
                    folder.getId(), folder.getName(), count, lastModified);
        }

        // 3. 空结果时单独提示
        if (subFolders.isEmpty()) {
            System.out.println("（该文件夹下没有子文件夹）");
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

    /** 将时间格式化为文本，null 时返回占位符。 */
    private String formatTime(LocalDateTime time) {
        return time != null ? time.format(FORMATTER) : "-";
    }
}
