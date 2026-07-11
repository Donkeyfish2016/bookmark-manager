package com.bookmark.cli.folder;

import com.bookmark.cli.MainCommand;
import com.bookmark.service.FolderService;
import picocli.CommandLine;

import java.util.concurrent.Callable;

/**
 * <p>
 * 文件夹命令组：聚合 tree / list / info 等只读子命令。
 * </p>
 *
 * @author DonkeyFish
 * @since 2026-7-9
 */
@CommandLine.Command(name = "folder",
        description = "Folder management commands (tree, list, info, add, delete, move, rename).",
        subcommands = {}, 
        mixinStandardHelpOptions = true)
public class FolderCommand implements Callable<Integer> {

    private final FolderService folderService;

    // 1. 通过构造器注入 FolderService，供子命令复用
    public FolderCommand(FolderService folderService) {
        this.folderService = folderService;
    }

    /**
     * 未指定子命令时打印帮助信息。
     *
     * @return 进程退出码（0）
     */
    @Override
    public Integer call() {
        // 1. 未携带子命令时输出用法说明
        CommandLine.usage(this, System.out);
        return 0;
    }
}
