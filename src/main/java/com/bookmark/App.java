package com.bookmark;

import com.bookmark.cli.AddCommand;
import com.bookmark.cli.DeleteCommand;
import com.bookmark.cli.ExportCommand;
import com.bookmark.cli.ImportCommand;
import com.bookmark.cli.ListCommand;
import com.bookmark.cli.MainCommand;
import com.bookmark.cli.SearchCommand;
import com.bookmark.cli.UpdateCommand;
import com.bookmark.db.BookmarkDAO;
import com.bookmark.db.DatabaseMgr;
import com.bookmark.service.BookmarkService;
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
        BookmarkService service = new BookmarkService(new BookmarkDAO());

        // 3. 构建命令树：根命令 + 各子命令（注入 service）
        CommandLine commandLine = new CommandLine(new MainCommand())
                .addSubcommand("add", new AddCommand(service))
                .addSubcommand("delete", new DeleteCommand(service))
                .addSubcommand("list", new ListCommand(service))
                .addSubcommand("search", new SearchCommand(service))
                .addSubcommand("update", new UpdateCommand(service))
                .addSubcommand("import", new ImportCommand(service))
                .addSubcommand("export", new ExportCommand(service));

        // 4. 解析参数并执行，返回退出码
        int exitCode = commandLine.execute(args);
        System.exit(exitCode);
    }
}
