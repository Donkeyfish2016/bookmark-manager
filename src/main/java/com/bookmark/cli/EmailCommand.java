package com.bookmark.cli;

import com.bookmark.service.BookmarkService;
import picocli.CommandLine;

import java.util.concurrent.Callable;

/**
 * <p>
 * 子命令：email —— 将全部书签导出为 HTML 并发送邮件。
 * </p>
 * <p>
 * 通过构造器注入 {@link BookmarkService}，邮件发送逻辑委托给服务层。
 * </p>
 *
 * @author DonkeyFish
 * @since 2026-7-10
 */
@CommandLine.Command(name = "email", description = "Send all bookmarks as HTML via email.")
public class EmailCommand implements Callable<Integer> {

    private final BookmarkService bookmarkService;

    public EmailCommand(BookmarkService bookmarkService) {
        this.bookmarkService = bookmarkService;
    }

    @CommandLine.Option(names = {"--to"}, description = "Recipient email address.", required = true)
    private String to;

    @CommandLine.Option(names = {"--subject"}, description = "Email subject.", defaultValue = "书签分享", required = false)
    private String subject;

    @CommandLine.Option(names = {"--smtp-host"}, description = "SMTP server host.", required = true)
    private String smtpHost;

    @CommandLine.Option(names = {"--smtp-port"}, description = "SMTP server port.", required = true)
    private int smtpPort;

    @CommandLine.Option(names = {"--smtp-user"}, description = "SMTP username.", required = true)
    private String smtpUser;

    @CommandLine.Option(names = {"--smtp-pass"}, description = "SMTP password.", required = true)
    private String smtpPass;

    @CommandLine.Option(names = {"--starttls"}, description = "Enable STARTTLS.", defaultValue = "false", required = false)
    private boolean starttls;

    @Override
    public Integer call() {
        bookmarkService.emailBookmarks(to, subject, smtpHost, smtpPort, smtpUser, smtpPass, starttls);
        System.out.println("邮件已发送至：" + to);
        return 0;
    }
}
