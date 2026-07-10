package com.bookmark;

import com.bookmark.cli.MainCommand;
import com.bookmark.cli.ShellCommand;
import com.bookmark.db.BookmarkDAO;
import com.bookmark.db.DatabaseMgr;
import com.bookmark.db.FolderDAO;
import com.bookmark.service.BookmarkService;
import com.bookmark.service.FolderService;

import picocli.CommandLine;

/**
 * <p>
 * 程序入口：组装依赖并启动 picocli 命令行界面。
 * </p>
 *
 * @author DonkeyFish
 * @since 2026-7-7
 */
public class App {
    public static void main(String[] args) {
        // 1. 初始化数据库连接与表结构
        DatabaseMgr.initialize();

        // 2. 组装服务层（DAO -> Service）
        FolderService folderService = new FolderService(new FolderDAO(), new BookmarkDAO());
        BookmarkService service = new BookmarkService(new BookmarkDAO(), folderService);

        // 3. 无参数时直接进入交互式 shell（不调用 System.exit，正常返回即可）
        if (args.length == 0) {
            new ShellCommand(service).run();
            return;
        }

        // 4. 有参数时走标准 picocli 执行流程（命令树含 shell 子命令）
        CommandLine commandLine = MainCommand.buildCommandLine(service);
        int exitCode = commandLine.execute(args);
        System.exit(exitCode);
    }
}
