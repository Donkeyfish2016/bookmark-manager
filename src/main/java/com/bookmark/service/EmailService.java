package com.bookmark.service;

import java.io.File;
import java.util.Properties;

import jakarta.activation.DataHandler;
import jakarta.activation.DataSource;
import jakarta.activation.FileDataSource;
import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;

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
        if (subject == null || subject.isBlank()) {
            subject = "书签分享";
        }

        Properties props = new Properties();
        props.put("mail.smtp.host", host);
        props.put("mail.smtp.port", String.valueOf(port));
        props.put("mail.smtp.auth", String.valueOf(auth));
        // props.put("mail.smtp.starttls.enable", String.valueOf(starttls));
        if (port == 465) {
            // 使用 SSL 直接加密（QQ 邮箱 465 端口要求）
            props.put("mail.smtp.ssl.enable", "true");
            // 下面两行是确保使用 SSL socket 的经典写法，兼容性好
            props.put("mail.smtp.socketFactory.port", "465");
            props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
            // 绝不能设置 starttls
            props.remove("mail.smtp.starttls.enable");
        } else {
            // 587 或其他端口使用 STARTTLS
            props.put("mail.smtp.starttls.enable", String.valueOf(starttls));
            // 确保关闭 SSL 直连
            props.put("mail.smtp.ssl.enable", "false");
        }

        Authenticator authenticator = auth ? new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(username, password);
            }
        } : null;

        Session session = Session.getInstance(props, authenticator);

        // 2. 正文为纯文本“来看看我的书签吧~”，编码 UTF-8
        MimeMessage message = new MimeMessage(session);
        message.setFrom(new InternetAddress(username));
        message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
        message.setSubject(subject, "UTF-8");

        MimeBodyPart textPart = new MimeBodyPart();
        textPart.setText("来看看我的书签吧~", "UTF-8");

        // 3. 附件部分将传入的 attachment 作为 MimeBodyPart 附加。
        MimeBodyPart attachmentPart = new MimeBodyPart();
        DataSource source = new FileDataSource(attachment);
        attachmentPart.setDataHandler(new DataHandler(source));
        attachmentPart.setFileName(attachment.getName());

        MimeMultipart multipart = new MimeMultipart();
        multipart.addBodyPart(textPart);
        multipart.addBodyPart(attachmentPart);

        // 4. 使用 Transport.send() 发送。
        message.setContent(multipart);
        Transport.send(message);
    }
}