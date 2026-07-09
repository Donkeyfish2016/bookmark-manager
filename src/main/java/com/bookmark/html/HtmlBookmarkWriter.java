package com.bookmark.html;

import com.bookmark.model.Bookmark;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * <p>
 * 生成标准Edge浏览器书签HTML
 * </p>
 *
 * @author DonkeyFish
 * @since 2026-7-8
 */
// TODO: 明天整顿导出功能
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
     * 将书签列表写入 Netscape Bookmark HTML 文件。
     */
    public void write(List<Bookmark> bookmarks, File outputFile) throws IOException {
        // 1. 校验输入并准备输出目录
        if (outputFile == null) {
            throw new IllegalArgumentException("outputFile must not be null");
        }
        File parent = outputFile.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }

        // 2. 构建文件夹树
        // FolderNode root = new FolderNode(DEFAULT_ROOT_FOLDER, true);
        // if (bookmarks != null) {
        //     for (Bookmark bookmark : bookmarks) {
        //         addBookmark(root, bookmark);
        //     }
        // }
        // 2.1 顶级文件夹映射：文件夹名 -> FolderNode
        Map<String, FolderNode> topFolders = new LinkedHashMap<>();
        FolderNode defaultFolder = new FolderNode(DEFAULT_ROOT_FOLDER, false);
        // 2.2 遍历书签列表，填充顶级文件夹映射
        for (Bookmark bookmark : bookmarks) {
            String category = bookmark.getCategory();
            if (category == null || category.isBlank()) {
                defaultFolder.getBookmarks().add(bookmark);
            } else {
                String[] parts = category.split("/");
                String topFolderName = parts[0];
                FolderNode topFolder = topFolders.computeIfAbsent(topFolderName, key -> new FolderNode(key, false));
                // 递归添加书签到对应的文件夹节点
                addBookmark(topFolder, bookmark);
            }
        }
        // 2.3 处理默认
        if (!defaultFolder.getBookmarks().isEmpty()) {
            topFolders.put(DEFAULT_ROOT_FOLDER, defaultFolder);
        }

        // 3. 生成 HTML 内容并写入文件
        StringBuilder builder = new StringBuilder();
        builder.append(HEADER);
        for (FolderNode root : topFolders.values()) {
            boolean isToolbar = root.getName().equals("收藏夹栏");
            appendFolder(builder, root, 0, isToolbar);
        }
        builder.append("</DL><p>\n");
        Files.writeString(outputFile.toPath(), builder.toString(), StandardCharsets.UTF_8);
    }

    /**
     * 根据分类路径创建或复用文件夹节点，并将书签挂到对应叶子节点。
     */
    private void addBookmark(FolderNode root, Bookmark bookmark) {
        if (bookmark == null) {
            return;
        }

        String category = bookmark.getCategory();
        if (category == null || category.isBlank()) {
            root.getBookmarks().add(bookmark);
            return;
        }

        String[] parts = category.split("/");
        FolderNode current = root;
        for (int i = 1; i < parts.length; i++) { // 从第二部分开始，因为第一部分已经是顶级文件夹
            String part = parts[i];
            if (part == null || part.isBlank()) {
                continue;
            }
            current = current.getOrCreateChild(part);
        }
        current.getBookmarks().add(bookmark);
    }

    /**
     * 递归把文件夹树转成 Netscape Bookmark HTML 片段。
     */
    private void appendFolder(StringBuilder builder, FolderNode folder, int depth, boolean isToolbar) {
        String indent = "    ".repeat(depth);
        builder.append(indent).append("<DT><H3")
                .append(" ADD_DATE=\"").append(formatTimestamp(System.currentTimeMillis() / 1000L))
                .append("\" LAST_MODIFIED=\"").append(formatTimestamp(System.currentTimeMillis() / 1000L))
                .append("\"");
        if (isToolbar) {
            builder.append(" PERSONAL_TOOLBAR_FOLDER=\"true\"");
        }
        builder.append(">")
                .append(escapeHtml(folder.getName()))
                .append("</H3>\n")
                .append(indent)
                .append("<DL><p>\n");

        for (FolderNode childFolder : folder.getChildren().values()) {
            appendFolder(builder, childFolder, depth + 1, false);
        }
        for (Bookmark bookmark : folder.getBookmarks()) {
            appendBookmark(builder, bookmark, depth + 1);
        }
        builder.append(indent).append("</DL><p>\n");
    }

    /**
     * 把单个书签输出为标准链接节点。
     */
    private void appendBookmark(StringBuilder builder, Bookmark bookmark, int depth) {
        String indent = "    ".repeat(depth);
        builder.append(indent).append("<DT><A HREF=\"").append(escapeHtml(bookmark.getUrl()))
                .append("\" ADD_DATE=\"").append(formatTimestamp(bookmark.getAddDate()))
                .append("\"");
        if (bookmark.getIcon() != null && !bookmark.getIcon().isBlank()) {
            builder.append(" ICON=\"").append(escapeHtml(bookmark.getIcon())).append("\"");
        }
        builder.append(">")
                .append(escapeHtml(bookmark.getTitle()))
                .append("</A>\n");
    }

    /**
     * 将 LocalDateTime 转成 Unix 时间戳秒，缺省时返回 0。
     */
    private String formatTimestamp(LocalDateTime addDate) {
        if (addDate == null) {
            return "0";
        }
        return String.valueOf(addDate.toInstant(ZoneOffset.UTC).getEpochSecond());
    }

    /**
     * 将 Unix 时间戳秒转成字符串，便于文件夹使用当前时间。
     */
    private String formatTimestamp(long epochSecond) {
        return String.valueOf(epochSecond);
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

    /**
     * 代表一个文件夹节点，维护子文件夹与直接书签。
     */
    // TODO: 目录节点重命名，可能需要重写设计表结构
    private static class FolderNode {
        private final String name;
        private final boolean root;
        private final Map<String, FolderNode> children = new LinkedHashMap<>();
        private final List<Bookmark> bookmarks = new ArrayList<>();

        private FolderNode(String name, boolean root) {
            this.name = name;
            this.root = root;
        }

        private FolderNode getOrCreateChild(String childName) {
            return children.computeIfAbsent(childName, key -> new FolderNode(key, false));
        }

        private String getName() {
            return name;
        }

        private boolean isRoot() {
            return root;
        }

        private Map<String, FolderNode> getChildren() {
            return children;
        }

        private List<Bookmark> getBookmarks() {
            return bookmarks;
        }
    }
}
