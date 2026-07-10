package com.bookmark.html;

import com.bookmark.model.Bookmark;
import com.bookmark.model.Folder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * <p>
 * 生成标准 Netscape Bookmark HTML 文件，兼容 Chrome 和 Firefox
 * </p>
 *
 * @author DonkeyFish
 * @since 2026-7-8
 */
public class HtmlBookmarkWriter {

    private static final String DEFAULT_ROOT_FOLDER = "收藏夹栏";
    private static final String HEADER = "<!DOCTYPE NETSCAPE-Bookmark-file-1>\n"
            + "<!-- This is an automatically generated file.\n"
            + "     It will be read and overwritten.\n"
            + "     DO NOT EDIT! -->\n"
            + "<META HTTP-EQUIV=\"Content-Type\" CONTENT=\"text/html; charset=UTF-8\">\n"
            + "<TITLE>Bookmarks</TITLE>\n"
            + "<H1>Bookmarks</H1>\n"
            + "<DL><p>\n";

    /**
     * 将文件夹树写入 Netscape Bookmark HTML 文件。
     *
     * @param rootFolder 根文件夹树（包含所有顶级文件夹和它们的子文件夹/书签）
     * @param outputFile 输出文件路径
     * @throws IOException 如果写入文件失败
     */
    public void write(Folder rootFolder, File outputFile) throws IOException {
        if (outputFile == null) {
            throw new IllegalArgumentException("outputFile must not be null");
        }
        File parent = outputFile.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }

        StringBuilder builder = new StringBuilder();
        builder.append(HEADER);

        for (Folder childFolder : rootFolder.getChildren().values()) {
            boolean isToolbar = DEFAULT_ROOT_FOLDER.equals(childFolder.getName());
            appendFolder(builder, childFolder, 0, isToolbar);
        }

        builder.append("</DL><p>\n");
        Files.writeString(outputFile.toPath(), builder.toString(), StandardCharsets.UTF_8);
    }

    /**
     * 递归将文件夹树转成 Netscape Bookmark HTML 片段。
     */
    private void appendFolder(StringBuilder builder, Folder folder, int depth, boolean isToolbar) {
        String indent = "    ".repeat(depth);
        long timestamp = getTimestamp(folder.getAddDate());

        builder.append(indent).append("<DT><H3")
                .append(" ADD_DATE=\"").append(timestamp)
                .append("\" LAST_MODIFIED=\"").append(getTimestamp(folder.getLastModified()))
                .append("\"");
        if (isToolbar) {
            builder.append(" PERSONAL_TOOLBAR_FOLDER=\"true\"");
        }
        builder.append(">")
                .append(escapeHtml(folder.getName()))
                .append("</H3>\n")
                .append(indent)
                .append("<DL><p>\n");

        for (Folder childFolder : folder.getChildren().values()) {
            appendFolder(builder, childFolder, depth + 1, false);
        }
        for (Bookmark bookmark : folder.getBookmarks()) {
            appendBookmark(builder, bookmark, depth + 1);
        }
        builder.append(indent).append("</DL><p>\n");
    }

    /**
     * 将单个书签输出为标准链接节点。
     */
    private void appendBookmark(StringBuilder builder, Bookmark bookmark, int depth) {
        String indent = "    ".repeat(depth);
        builder.append(indent).append("<DT><A HREF=\"").append(escapeHtml(bookmark.getUrl()))
                .append("\" ADD_DATE=\"").append(getTimestamp(bookmark.getAddDate()))
                .append("\"");
        if (bookmark.getIcon() != null && !bookmark.getIcon().isBlank()) {
            builder.append(" ICON=\"").append(escapeHtml(bookmark.getIcon())).append("\"");
        }
        builder.append(">")
                .append(escapeHtml(bookmark.getTitle()))
                .append("</A>\n");
    }

    /**
     * 将 LocalDateTime 转成 Unix 时间戳秒，缺省时返回当前时间戳。
     */
    private long getTimestamp(LocalDateTime dateTime) {
        if (dateTime == null) {
            return System.currentTimeMillis() / 1000L;
        }
        return dateTime.toInstant(ZoneOffset.UTC).getEpochSecond();
    }

    /**
     * 对 HTML 文本做标准转义，避免破坏结构。
     */
    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}