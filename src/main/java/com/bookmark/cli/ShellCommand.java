package com.bookmark.cli;

import com.bookmark.service.BookmarkService;
import picocli.CommandLine;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

/**
 * <p>
 * 子命令：shell —— 进入交互式书签管理 REPL。
 * </p>
 * <p>
 * 通过构造器注入 {@link BookmarkService}，复用与主命令相同的服务实例与数据库连接。
 * 每行输入交由 picocli 解析为子命令并执行，循环直至用户输入 {@code exit}/{@code quit} 或遇到 EOF。
 * </p>
 *
 * @author DonkeyFish
 * @since 2026-7-10
 */
@CommandLine.Command(name = "shell", description = "Enter interactive bookmark management shell")
public class ShellCommand implements Runnable {

    private final BookmarkService bookmarkService;

    // 1. 通过构造器注入 BookmarkService，复用同一数据库与服务实例
    public ShellCommand(BookmarkService bookmarkService) {
        this.bookmarkService = bookmarkService;
    }

    /**
     * REPL 入口：逐行读取命令并委托给 picocli 解析执行。
     */
    @Override
    public void run() {
        // 1. 以 MainCommand 为根重建命令树，复用同一服务实例
        CommandLine cmd = MainCommand.buildCommandLine(bookmarkService);

        // 2. 使用 RunLast 执行策略，并屏蔽 System.exit 以免退出整个 JVM
        cmd.setExecutionStrategy(new CommandLine.RunLast());
        cmd.setExecutionExceptionHandler((ex, commandLine, parseResult) -> {
            commandLine.getErr().println(ex.getMessage());
            return commandLine.getCommandSpec().exitCodeOnExecutionException();
        });

        // 3. 启动 REPL 循环，逐行读取并分发到对应子命令
        System.out.print("bookmark> ");
        System.out.flush();
        try (Scanner scanner = new Scanner(System.in)) {
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine().trim();

                // 4. 跳过空行
                if (line.isEmpty()) {
                    continue;
                }

                // 5. 退出指令：打印提示并结束循环
                if (line.equals("exit") || line.equals("quit")) {
                    System.out.println("bye!");
                    break;
                }

                // 特殊处理 help 命令
                if (line.equalsIgnoreCase("help") || line.equals("?")) {
                    cmd.usage(System.out);  // 直接使用已有的 cmd 对象打印帮助
                    System.out.print("bookmark> ");
                    System.out.flush();
                    continue;
                }

                // 6. 解析为参数数组后执行；异常仅提示，不中断 shell 会话
                String[] args = splitArgs(line);
                try {
                    cmd.execute(args);
                } catch (Exception e) {
                    System.out.println("Error: " + e.getMessage());
                }
                System.out.print("bookmark> ");
            }
        }
    }

    /**
     * 将命令行拆分为参数数组，支持双引号包裹的含空格参数（如 {@code --title "Hello World"}）。
     *
     * @param line 已 trim 的原始输入行
     * @return 拆分后的参数数组
     */
    private String[] splitArgs(String line) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                // 1. 切换引号状态，引号本身不计入参数
                inQuotes = !inQuotes;
            } else if (Character.isWhitespace(c) && !inQuotes) {
                // 2. 引号外遇到空白：结束当前 token
                if (current.length() > 0) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }
        // 3. 收尾：追加最后一个 token
        if (current.length() > 0) {
            tokens.add(current.toString());
        }
        return tokens.toArray(new String[0]);
    }
}
