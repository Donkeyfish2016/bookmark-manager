package com.bookmark.cli;
import picocli.CommandLine;

import java.util.concurrent.Callable;

/**
 * <p>
 * 根命令（顶层命令），负责装载各子命令并提供帮助信息。
 * </p>
 *
 * @author DonkeyFish
 * @since 2026-7-7
 */
@CommandLine.Command(name = "bm",
        description = "Edge browser bookmark manager.",
        mixinStandardHelpOptions = true,
        subcommands = {})
public class MainCommand implements Callable<Integer> {

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
