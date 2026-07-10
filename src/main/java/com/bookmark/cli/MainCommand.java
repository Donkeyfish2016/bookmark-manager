package com.bookmark.cli;

import com.bookmark.cli.folder.FolderAddCommand;
import com.bookmark.cli.folder.FolderCommand;
import com.bookmark.cli.folder.FolderDeleteCommand;
import com.bookmark.cli.folder.FolderInfoCommand;
import com.bookmark.cli.folder.FolderListCommand;
import com.bookmark.cli.folder.FolderMoveCommand;
import com.bookmark.cli.folder.FolderRenameCommand;
import com.bookmark.cli.folder.FolderTreeCommand;
import com.bookmark.service.BookmarkService;
import com.bookmark.service.FolderService;
import picocli.CommandLine;

import java.util.concurrent.Callable;

/**
 * <p>
 * 根命令（顶层命令），负责装载各子命令并提供帮助信息。
 * </p>
 * <p>
 * 通过构造器注入 {@link BookmarkService}，并在 {@link #buildCommandLine(BookmarkService)}
 * 中复用同一服务实例组装完整命令树，保证所有子命令共享同一条 SQLite 连接。
 * </p>
 *
 * @author DonkeyFish
 * @since 2026-7-7
 */
@CommandLine.Command(name = "bookmark",
        description = "Edge browser bookmark manager.",
        mixinStandardHelpOptions = true,
        subcommands = {})
public class MainCommand implements Callable<Integer> {

    private final BookmarkService bookmarkService;

    // 1. 通过构造器注入 BookmarkService，保持单实例与共享 SQLite 连接
    public MainCommand(BookmarkService bookmarkService) {
        this.bookmarkService = bookmarkService;
    }

    /**
     * <p>集中组装完整命令树（根命令 + 全部子命令）。</p>
     * <p>入口程序与交互式 shell 均复用本方法，确保整个会话共享同一个服务实例。</p>
     *
     * @param service 已初始化的书签服务实例
     * @return 装配完成的 picocli 命令树
     */
    public static CommandLine buildCommandLine(BookmarkService service) {
        // 1. 复用同一 BookmarkService 派生的 FolderService，保证数据库连接有且仅有一份
        FolderService folderService = service.getFolderService();

        // 2. 以 MainCommand 为根，挂载书签相关子命令（均注入 service）
        return new CommandLine(new MainCommand(service))
                .addSubcommand("add", new AddCommand(service))
                .addSubcommand("delete", new DeleteCommand(service))
                .addSubcommand("list", new ListCommand(service))
                .addSubcommand("search", new SearchCommand(service))
                .addSubcommand("update", new UpdateCommand(service))
                .addSubcommand("import", new ImportCommand(service))
                .addSubcommand("export", new ExportCommand(service))
                .addSubcommand("shell", new ShellCommand(service))
                // 3. folder 命令组：以 FolderCommand 为节点，挂载其只读/写子命令
                .addSubcommand("folder", new CommandLine(new FolderCommand(folderService))
                        .addSubcommand("tree", new FolderTreeCommand(folderService))
                        .addSubcommand("list", new FolderListCommand(folderService))
                        .addSubcommand("info", new FolderInfoCommand(folderService))
                        .addSubcommand("add", new FolderAddCommand(folderService))
                        .addSubcommand("delete", new FolderDeleteCommand(folderService))
                        .addSubcommand("rename", new FolderRenameCommand(folderService))
                        .addSubcommand("move", new FolderMoveCommand(folderService)));
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
