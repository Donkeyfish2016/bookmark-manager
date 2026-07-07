package com.bookmark.service;

import com.bookmark.db.BookmarkDAO;
import com.bookmark.model.Bookmark;

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
