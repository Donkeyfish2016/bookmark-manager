package com.bookmark.service;

import java.io.File;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeBodyPart;

/**
 * <p>
 * 提供发送邮件的业务接口.
 * </p>
 *
 * @author DonkeyFish
 * @since 2026-7-10
 */
public class EmailService {
    
    private final String host;
    private final int port;
    private final String username;
    private final String password;
    private final boolean auth;
    private final boolean starttls;

    /**
     * 构造函数接收 SMTP 连接参数
     */
    public EmailService(String host, int port, String username, String password, boolean auth, boolean starttls) {
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
        this.auth = auth;
        this.starttls = starttls;
    }

    /**
     * 使用JavaMail构建一封MIME邮件,
     * 发送邮件，带html附件,发件人地址为 username
     * 若 auth 参数为 true 则创建 Authenticator，否则用空 Authenticator
     * @param to
     * @param subject
     * @param attachment
     * @throws MessagingException
     */
    public void sendHtmlEmail(String to, String subject, File attachment) throws MessagingException {
        // 1. 主题若为 null 或空，则默认使用“书签分享”

        // 2. 正文为纯文本“来看看我的书签吧~”，编码 UTF-8

        // 3. 附件部分将传入的 attachment 作为 MimeBodyPart 附加。

        // 4. 使用 Transport.send() 发送。
    }
}