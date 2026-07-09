package com.bookmark.html;

import com.bookmark.model.Bookmark;
import com.bookmark.model.Folder;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * <p>
 * 解析 Microsoft Edge 导出的书签 HTML 文件（Netscape Bookmark 格式），
 * 并将其转换为以 {@link Folder} 为节点的内存层级树。
 * </p>
 *
 * @author DonkeyFish
 * @since 2026-7-8
 */
public class HtmlBookmarkParser {

    /** 默认“工具栏”文件夹名：用于收纳游离（无显式文件夹）的书签。 */
    private static final String TOOLBAR = "游离书签";

    /**
     * 解析书签 HTML 文件，返回表示整棵书签层级树的根文件夹。
     *
     * @param file Edge 导出的书签 HTML 文件
     * @return 根 {@link Folder}，其 {@code children} 与 {@code bookmarks} 承载全部层级
     * @throws RuntimeException 当文件缺失、读取失败或 HTML 无法解析（无 {@code <DL>}）时抛出
     */
    public Folder parse(File file) {
        // 1. 文件不存在直接抛出 RuntimeException（避免后续 IO 异常被吞掉）
        if (file == null || !file.exists()) {
            throw new RuntimeException("Bookmark HTML file not found: " + file);
        }

        // 2. 以 UTF-8 为强制编码加载文档，Jsoup 不会再根据 <meta charset> 探测编码
        Document doc;
        try {
            doc = Jsoup.parse(file, "UTF-8");
        } catch (IOException e) {
            throw new RuntimeException("Failed to read bookmark HTML file: " + file, e);
        }

        // 3. 选取根级 <DL> 容器；若缺失则说明文档不可解析，抛出 RuntimeException
        Element rootDl = doc.selectFirst("DL");
        if (rootDl == null) {
            throw new RuntimeException("Unparseable bookmark HTML, no <DL> container found: " + file);
        }

        // 4. 构建虚拟根文件夹，作为整棵树的起点
        Folder root = new Folder();
        root.setName("__root__");
        root.setRoot(true);

        // 5. 从根 <DL> 开始递归构建子树（isRoot=true 用于识别游离书签）
        parseDl(rootDl, root, "", true);
        return root;
    }

    /**
     * 递归解析一个 {@code <DL>} 容器，把其中的文件夹挂到父文件夹的 {@code children} 映射，
     * 把其中的书签挂到对应文件夹的 {@code bookmarks} 列表。
     *
     * @param dl     当前 {@code <DL>} 容器
     * @param folder 当前文件夹（解析结果写入其中）
     * @param path   截至当前层级的分类路径（'/' 分隔，根层级为空串）
     * @param isRoot 是否为根 <DL>：仅根层级的游离书签才归入“Toolbar”
     */
    private void parseDl(Element dl, Folder folder, String path, boolean isRoot) {
        // 1. 仅遍历 <DL> 的直接子元素中的 <DT> 项
        for (Element child : dl.children()) {
            if (!"dt".equalsIgnoreCase(child.tagName())) {
                continue;
            }

            // 2. 显式忽略 <META>、<TITLE> 等非书签元数据节点
            Element meta = child.selectFirst("meta");
            Element title = child.selectFirst("title");
            if (meta != null || title != null) {
                continue;
            }

            // 3. 文件夹：<DT> 内含有 <H3>
            Element h3 = child.selectFirst("h3");
            if (h3 != null) {
                // 4. 创建文件夹实例，并从 <H3> 中提取名称与时间属性
                Folder sub = new Folder();
                sub.setName(h3.text());
                sub.setRoot(false);
                sub.setAddDate(parseAddDate(h3.attr("add_date")));
                sub.setLastModified(parseAddDate(h3.attr("last_modified")));

                // 5. 以文件夹名为键挂到父文件夹的 children 映射
                folder.getChildren().put(sub.getName(), sub);

                // 6. 定位该 <DT> 内部嵌套的 <DL> 作为子内容容器（兼容同级 <DL> 写法），递归解析
                Element subDl = resolveSubDl(child);
                if (subDl != null) {
                    String nextPath = path.isEmpty() ? sub.getName() : path + "/" + sub.getName();
                    parseDl(subDl, sub, nextPath, false);
                }
                continue;
            }

            // 7. 书签：<DT> 内含有 <A>
            Element a = child.selectFirst("a");
            if (a != null) {
                if (isRoot) {
                    // 8. 根层级游离书签：归入默认文件夹（按需创建）
                    Folder toolbar = folder.getChildren().computeIfAbsent(TOOLBAR, key -> {
                        Folder t = new Folder();
                        t.setName(TOOLBAR);
                        t.setRoot(false);
                        return t;
                    });
                    toolbar.getBookmarks().add(parseBookmark(a, TOOLBAR));
                } else {
                    // 9. 普通书签：直接挂到当前文件夹
                    folder.getBookmarks().add(parseBookmark(a, path));
                }
            }
        }
    }

    /**
     * 解析文件夹 <DT> 下的内容 <DL>：优先取嵌套 <DL>，否则回退到紧随其后的同级 <DL>。
     */
    private Element resolveSubDl(Element dt) {
        Element nested = dt.selectFirst("dl");
        if (nested != null) {
            return nested;
        }
        Element sibling = dt.nextElementSibling();
        if (sibling != null && "dl".equalsIgnoreCase(sibling.tagName())) {
            return sibling;
        }
        return null;
    }

    /**
     * 将单个 {@code <A>} 标签转换为 {@link Bookmark}。
     *
     * @param a    书签 <A> 元素
     * @param path 所属分类路径（来自层级树，与旧版 category 字段保持一致）
     * @return 转换后的书签对象（folderId 留空，由服务层赋值）
     */
    private Bookmark parseBookmark(Element a, String path) {
        // 1. 提取 HREF -> url
        String url = a.attr("href");
        // 2. 提取标签文本 -> title
        String title = a.text();
        // 3. 提取 ICON 属性 -> icon
        String icon = a.attr("icon");
        // 4. 提取 ADD_DATE（Unix 秒）并转换为 LocalDateTime（UTC）
        LocalDateTime addDate = parseAddDate(a.attr("add_date"));

        // 5. 组装书签：id / folderId / 时间戳由数据库管理，此处 folderId 置空
        return new Bookmark(null, url, title, icon.isEmpty() ? null : icon, path, addDate, null, null, null);
    }

    /**
     * 将 Unix 时间戳（秒）解析为 UTC 的 {@link LocalDateTime}。
     */
    private LocalDateTime parseAddDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            long epochSecond = Long.parseLong(value.trim());
            return LocalDateTime.ofInstant(Instant.ofEpochSecond(epochSecond), ZoneOffset.UTC);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
