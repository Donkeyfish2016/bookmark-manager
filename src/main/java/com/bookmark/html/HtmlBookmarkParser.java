package com.bookmark.html;

import com.bookmark.model.Bookmark;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * <p>
 * 解析 Microsoft Edge 导出的书签 HTML 文件（Netscape Bookmark 格式）。
 * </p>
 *
 * @author DonkeyFish
 * @since 2026-7-8
 */
public class HtmlBookmarkParser {

    /**
     * 解析书签 HTML 文件，返回扁平化的书签列表。
     *
     * @param file Edge 导出的书签 HTML 文件
     * @return 解析得到的 {@link Bookmark} 列表
     * @throws IOException 读取文件失败时抛出
     */
    public List<Bookmark> parse(File file) throws IOException {
        // 1. 以 UTF-8 为默认编码加载文档（Jsoup 仍会参考 <meta> 中的 charset 进行自动探测）
        Document doc = Jsoup.parse(file, "UTF-8");

        // 2. 选取根级 <DL> 容器，作为遍历起点
        Element rootDl = doc.selectFirst("DL");
        List<Bookmark> result = new ArrayList<>();
        if (rootDl != null) {
            // 3. 自根目录开始递归解析（初始分类为空）
            parseDl(rootDl, "", result);
        }
        return result;
    }

    /**
     * 递归解析一个 <DL> 容器，将其中书签加入结果集，遇到文件夹则下钻。
     *
     * @param dl       当前 <DL> 容器
     * @param category 截至当前层级的分类路径（'/' 分隔）
     * @param out      结果收集列表
     */
    private void parseDl(Element dl, String category, List<Bookmark> out) {
        // 1. 遍历 <DL> 的直接子元素，仅处理 <DT> 项
        for (Element child : dl.children()) {
            if (!"dt".equalsIgnoreCase(child.tagName())) {
                continue;
            }

            // 2. 文件夹：<DT> 内含有 <H3>
            Element folder = child.selectFirst("h3");
            // 3. 书签：<DT> 内含有 <A>
            Element link = child.selectFirst("a");

            if (folder != null) {
                // 4. 拼接分类路径（首层直接使用文件夹名，其余层级以 '/' 追加）
                String folderName = folder.text();
                String nextCategory = category.isEmpty() ? folderName : category + "/" + folderName;

                // 5. 选取该 <DT> 内部嵌套的 <DL> 作为文件夹内容容器并递归
                Element subDl = child.selectFirst("dl");
                if (subDl != null) {
                    parseDl(subDl, nextCategory, out);
                }
            } else if (link != null) {
                // 6. 普通书签：解析并归入当前分类
                out.add(parseBookmark(link, category));
            }
        }
    }

    /**
     * 将单个 <A> 标签转换为 {@link Bookmark}。
     *
     * @param a        书签 <A> 元素
     * @param category 所属分类路径
     * @return 转换后的书签对象
     */
    private Bookmark parseBookmark(Element a, String category) {
        // 1. 提取 HREF -> url
        String url = a.attr("href");
        // 2. 提取标签文本 -> title
        String title = a.text();
        // 3. 提取 ICON 属性 -> icon
        String icon = a.attr("icon");
        // 4. 提取 ADD_DATE（Unix 秒）并转换为 LocalDateTime（UTC）
        LocalDateTime addDate = parseAddDate(a.attr("add_date"));

        // 5. 组装书签（id / createTime / updateTime 由数据库管理，此处置空）
        return new Bookmark(null, url, title, icon.isEmpty() ? null : icon, category, addDate, null, null);
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
