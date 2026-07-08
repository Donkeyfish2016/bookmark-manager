package com.bookmark.service;

import com.bookmark.db.BookmarkDAO;
import com.bookmark.html.HtmlBookmarkParser;
import com.bookmark.html.HtmlBookmarkWriter;
import com.bookmark.model.BatchResult;
import com.bookmark.model.Bookmark;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

/**
 * <p>
 * 书签业务逻辑层：在 DAO 之上封装校验与编排。
 * </p>
 * <p>
 * 职责说明：
 * 1. 通过构造器注入 {@link BookmarkDAO}，保持依赖不可变、非空。
 * 2. 对入参进行严格校验（非空、非空串、分页参数为正数）。
 * 3. 编排实体构造并委托 DAO 执行持久化，不直接接触 SQL。
 * </p>
 *
 * @author DonkeyFish
 * @since 2026-7-7
 */
public class BookmarkService {

    private final BookmarkDAO bookmarkDAO;
    // HTML 导入/导出工具（无状态，直接持有实例）
    private final HtmlBookmarkParser htmlParser = new HtmlBookmarkParser();
    private final HtmlBookmarkWriter htmlWriter = new HtmlBookmarkWriter();

    // 1. 通过构造器注入 DAO 依赖，保证依赖不可变且非空
    public BookmarkService(BookmarkDAO bookmarkDAO) {
        if (bookmarkDAO == null) {
            throw new IllegalArgumentException("BookmarkDAO must not be null");
        }
        this.bookmarkDAO = bookmarkDAO;
    }

    /**
     * 新增书签。
     *
     * @return 已持久化且回填主键的完整书签对象
     */
    public Bookmark add(String url, String title, String icon, String category) {
        // 1. 校验所有入参非空且非空串
        requireNonBlank(url, "url");
        requireNonBlank(title, "title");
        requireNonBlank(category, "category");

        // 2. 构造实体并写入数据库（add_date 取当前时间）
        Bookmark bookmark = new Bookmark(null, url, title, icon, category, LocalDateTime.now(), null, null);
        int id = bookmarkDAO.insert(bookmark);

        // 3. 回填自增主键后返回完整对象
        bookmark.setId(id);
        return bookmark;
    }

    /**
     * 按分类分页查询书签。
     */
    public List<Bookmark> list(String category, Integer page, Integer pageSize) {
        // 1. 校验分页参数为非空且为正数
        requirePositive(page, "page");
        requirePositive(pageSize, "pageSize");

        // 2. 委托 DAO 执行分类分页查询
        return bookmarkDAO.query(category, page, pageSize);
    }

    /**
     * 根据主键删除书签。
     *
     * @return 删除成功返回 {@code true}，未命中返回 {@code false}
     */
    public boolean delete(Integer id) {
        // 1. 校验 id 非空
        requireNonNull(id, "id");

        // 2. 委托 DAO 删除，并以是否影响行数返回结果
        return bookmarkDAO.deleteById(id) > 0;
    }

    /**
     * 按关键字在 url 与 title 上模糊搜索。
     */
    public List<Bookmark> search(String keyword) {
        // 1. 校验关键字非空
        requireNonBlank(keyword, "keyword");

        // 2. 委托 DAO 执行模糊搜索
        return bookmarkDAO.queryByKeyword(keyword);
    }

    /**
     * 根据主键查询单条书签。
     *
     * @return 命中的书签，未找到时返回 {@code null}
     */
    public Bookmark getById(Integer id) {
        // 1. 校验 id 非空
        requireNonNull(id, "id");

        // 2. 委托 DAO 按主键查询
        return bookmarkDAO.queryById(id);
    }

    /**
     * 更新指定书签的全部业务字段。
     *
     * @return 更新成功返回 {@code true}，未命中返回 {@code false}
     */
    public boolean update(Integer id, String url, String title, String icon, String category) {
        // 1. 校验 id 非空，且其余字段非空且非空串
        requireNonNull(id, "id");
        requireNonBlank(url, "url");
        requireNonBlank(title, "title");
        requireNonBlank(icon, "icon");
        requireNonBlank(category, "category");

        // 2. 以 id 定位构造实体并委托 DAO 更新
        Bookmark bookmark = new Bookmark(id, url, title, icon, category, null, null, null);
        return bookmarkDAO.update(bookmark) > 0;
    }

    // TODO: 添加count计数的业务

    /**
     * 查询全部书签（不分页、不分类过滤），用于导出等场景。
     */
    public List<Bookmark> listAll() {
        return bookmarkDAO.query(null, 1, Integer.MAX_VALUE);
    }

    /**
     * 从 Edge 书签 HTML 文件导入书签。
     * 解析后批量插入，约束冲突的记录会被跳过。
     *
     * @param filePath 书签 HTML 文件路径
     * @return 导入结果（成功数与失败数）
     */
    public BatchResult importFromHtml(String filePath) {
        // 1. 校验文件路径非空
        requireNonBlank(filePath, "filePath");

        // 2. 解析 HTML 得到书签列表
        List<Bookmark> parsed = parseHtml(new File(filePath));

        // 3. 批量插入（跳过约束冲突记录），返回成功/失败统计
        BatchResult result = bookmarkDAO.batchInsertSkipErrors(parsed);
        return result;
    }

    /**
     * 将当前所有书签导出为 Edge 书签 HTML 文件。
     *
     * @param outputPath 输出 HTML 文件路径
     * @return 导出的书签数量
     */
    public int exportToHtml(String outputPath) {
        // 1. 校验输出路径非空
        requireNonBlank(outputPath, "outputPath");

        // 2. 读取全部书签并交给 HTML 写出器，返回导出数量
        List<Bookmark> all = listAll();
        try {
            htmlWriter.write(all, new File(outputPath));
        } catch (IOException e) {
            throw new RuntimeException("Failed to export bookmarks to: " + outputPath, e);
        }
        return all.size();
    }

    /** 调用 HTML 解析器，将 IO 异常包装为运行时异常。 */
    private List<Bookmark> parseHtml(File file) {
        try {
            return htmlParser.parse(file);
        } catch (IOException e) {
            throw new RuntimeException("Failed to parse bookmark HTML: " + file, e);
        }
    }

    // ---- 校验辅助方法 ----

    /** 校验字符串非空且非空串。 */
    private void requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Field '" + field + "' must not be null or empty");
        }
    }

    /** 校验对象非空。 */
    private void requireNonNull(Integer value, String field) {
        if (value == null) {
            throw new IllegalArgumentException("Field '" + field + "' must not be null");
        }
    }

    /** 校验整数为正数。 */
    private void requirePositive(Integer value, String field) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException("Field '" + field + "' must be a positive integer");
        }
    }
}
