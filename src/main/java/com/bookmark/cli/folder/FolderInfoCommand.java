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
 * 子命令：folder info —— 展示指定文件夹的详细元数据。
 * </p>
 * <p>
 * 通过 {@code --id} 指定目标文件夹，检索文件夹本身、子文件夹名称列表
 * 以及该文件夹直接包含的书签数量后格式化输出。
 * </p>
 *
 * @author DonkeyFish
 * @since 2026-7-9
 */
@CommandLine.Command(name = "info", description = "Show detailed metadata for a folder.", mixinStandardHelpOptions = true)
public class FolderInfoCommand implements Callable<Integer> {

    /** 时间字段显示格式，与 DAO 中存储格式保持一致。 */
    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final FolderService folderService;

    // 1. 通过构造器注入 FolderService
    public FolderInfoCommand(FolderService folderService) {
        this.folderService = folderService;
    }

    // 2. 声明必填的文件夹 id
    @CommandLine.Option(names = {"-i", "--id"},
            description = "Target folder id.", required = true)
    private int id;

    /**
     * 命令执行入口：检索元数据并格式化输出。
     *
     * @return 进程退出码（成功 0，未找到 1）
     */
    @Override
    public Integer call() {
        // 1. 按 id 查询文件夹；不存在则报错退出
        Folder folder;
        try {
            folder = folderService.getFolderById(id);
        } catch (IllegalArgumentException e) {
            System.out.println("查询失败：" + e.getMessage());
            return 1;
        }
        if (folder == null) {
            System.out.println("未找到 ID=" + id + " 的文件夹");
            return 1;
        }

        // 2. 查询子文件夹名称列表与直接书签数量
        List<Folder> children = folderService.getSubFolders(id);
        List<String> childNames = children.stream()
                .map(Folder::getName)
                .collect(Collectors.toList());
        int bookmarkCount = folderService.getBookmarksByFolder(id).size();

        // 3. 解析父文件夹标签（root 或父文件夹名称）
        String parentLabel;
        if (folder.getParentId() == null) {
            parentLabel = "root";
        } else {
            Folder parent = folderService.getFolderById(folder.getParentId());
            parentLabel = parent != null ? parent.getName() : String.valueOf(folder.getParentId());
        }

        // 4. 格式化输出各字段
        System.out.printf("%-14s %s%n", "ID:", folder.getId());
        System.out.printf("%-14s %s%n", "Name:", folder.getName());
        System.out.printf("%-14s %s (%s)%n",
                "Parent ID:", folder.getParentId() == null ? "null" : folder.getParentId(), parentLabel);
        System.out.printf("%-14s %s%n", "Is Root:", folder.isRoot());
        System.out.printf("%-14s %d (%s)%n",
                "Child Folders:", childNames.size(), String.join(", ", childNames));
        System.out.printf("%-14s %d%n", "Bookmarks:", bookmarkCount);
        System.out.printf("%-14s %s%n", "Created:", formatTime(folder.getCreateTime()));
        System.out.printf("%-14s %s%n", "Last Modified:", formatTime(folder.getLastModified()));
        return 0;
    }

    /** 将时间格式化为文本，null 时返回占位符。 */
    private String formatTime(LocalDateTime time) {
        return time != null ? time.format(FORMATTER) : "-";
    }
}
