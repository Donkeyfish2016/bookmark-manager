package com.bookmark.db;

import com.bookmark.model.BatchResult;
import com.bookmark.model.Bookmark;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * <p>
 * 书签数据访问对象 (CRUD + 搜索)
 * </p>
 * <p>
 * 约定：
 * 1. 通过 {@link DatabaseMgr} 获取共享的 SQLite 连接，所有 SQL 操作均在此类内封装。
 * 2. 数据库以 {@code TEXT} 形式（{@code yyyy-MM-dd HH:mm:ss}）存储时间字段，
 *    与实体类 {@link Bookmark} 的 {@link LocalDateTime} 字段互转。
 * 3. 分页查询的 {@code page} 从 1 开始计数。
 * </p>
 *
 * @author DonkeyFish
 * @since 2026-7-7
 */
public class BookmarkDAO {

    /** 时间字段在数据库中的存储格式 */
    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 插入一条书签记录，并返回数据库自动生成的主键。
     *
     * @param bookmark 待插入的书签（id 会被忽略，由数据库自增生成）
     * @return 自动生成的书签 id；插入失败时返回 -1
     */
    public int insert(Bookmark bookmark) {
        // 1. 仅写入业务字段，create_time / update_time 由表默认值自动填充
        String sql = "INSERT INTO bookmarks (url, title, icon, category, add_date, folder_id) " +
                "VALUES (?, ?, ?, ?, ?, ?)";

        // 2. 执行插入并取回自增主键
        try (Connection conn = DatabaseMgr.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, bookmark.getUrl());
            ps.setString(2, bookmark.getTitle());
            ps.setString(3, bookmark.getIcon());
            ps.setString(4, bookmark.getCategory());
            // add_date 列非空：未提供时默认取当前时间
            LocalDateTime addDate = bookmark.getAddDate() != null ? bookmark.getAddDate() : LocalDateTime.now();
            ps.setString(5, toText(addDate));
            ps.setInt(6, bookmark.getFolderId() != null ? bookmark.getFolderId() : 1);
            ps.executeUpdate();

            // 3. 读取生成的 id
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to insert bookmark: " + bookmark, e);
        }
        return -1;
    }

    /**
     * 按分类进行分页查询。
     *
     * @param category 分类名；为 {@code null} 或空字符串时表示查询全部分类
     * @param page     页码，从 1 开始
     * @param pageSize 每页记录数
     * @return 当前页的书签列表（可能为空）
     */
    public List<Bookmark> query(String category, int page, int pageSize) {
        // 1. 根据是否提供分类拼接过滤条件
        boolean hasCategory = category != null && !category.isBlank();
        String base = "SELECT id, url, title, icon, category, add_date, folder_id, create_time, update_time FROM bookmarks";
        String sql = (hasCategory ? base + " WHERE category = ?" : base)
                + " ORDER BY id"
                + " LIMIT ? OFFSET ?";

        // 2. 计算分页偏移量（page 从 1 计数）
        int offset = (Math.max(page, 1) - 1) * pageSize;

        // 3. 执行查询并映射结果
        List<Bookmark> result = new ArrayList<>();
        try (Connection conn = DatabaseMgr.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            int idx = 1;
            if (hasCategory) {
                ps.setString(idx++, category);
            }
            ps.setInt(idx++, pageSize);
            ps.setInt(idx, offset);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to query bookmarks by category: " + category, e);
        }
        return result;
    }

    /** 查询全部书签，供业务层构建树结构。 */
    public List<Bookmark> queryAll() {
        return query(null, 1, Integer.MAX_VALUE);
    }

    /**
     * 根据主键查询单条书签。
     *
     * @param id 书签主键
     * @return 命中的书签，未找到时返回 {@code null}
     */
    public Bookmark queryById(int id) {
        // 1. 按主键查询一条记录
        String sql = "SELECT id, url, title, icon, category, add_date, folder_id, create_time, update_time " +
                "FROM bookmarks WHERE id = ?";

        // 2. 映射结果（最多一条）
        try (Connection conn = DatabaseMgr.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to query bookmark by id: " + id, e);
        }
        return null;
    }

    /**
     * 根据文件夹 ID 查询书签列表。
     * 
     * @param folderId
     * @return
     */
    public List<Bookmark> queryByFolderId(Integer folderId) {
        String sql = "SELECT id, url, title, icon, category, add_date, folder_id, create_time, update_time " +
                "FROM bookmarks WHERE folder_id = ? ORDER BY id";

        List<Bookmark> result = new ArrayList<>();
        try (Connection conn = DatabaseMgr.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, folderId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to query bookmarks by folderId: " + folderId, e);
        }
        return result;
    }

    /**
     * 在 url 与 title 两个字段上做模糊搜索。
     *
     * @param keyword 关键字（自动前后加通配符）
     * @return 匹配的书签列表（可能为空）
     */
    public List<Bookmark> queryByKeyword(String keyword) {
        // 1. 对 url 与 title 同时使用 LIKE 模糊匹配
        String sql = "SELECT id, url, title, icon, category, add_date, folder_id, create_time, update_time " +
                "FROM bookmarks WHERE url LIKE ? OR title LIKE ? ORDER BY id";

        // 2. 执行查询并映射结果
        List<Bookmark> result = new ArrayList<>();
        try (Connection conn = DatabaseMgr.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            String pattern = "%" + (keyword == null ? "" : keyword) + "%";
            ps.setString(1, pattern);
            ps.setString(2, pattern);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to search bookmarks by keyword: " + keyword, e);
        }
        return result;
    }

    /**
     * 根据主键删除书签。
     *
     * @param id 书签主键
     * @return 受影响行数（1 表示成功，0 表示未找到）
     */
    public int deleteById(int id) {
        // 1. 按主键删除
        String sql = "DELETE FROM bookmarks WHERE id = ?";

        // 2. 返回受影响行数
        try (Connection conn = DatabaseMgr.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, id);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete bookmark by id: " + id, e);
        }
    }

    /**
     * 按主键更新书签的业务字段（url/title/icon/category），并刷新 update_time。
     *
     * @param bookmark 包含主键 id 及新字段值的书签对象
     * @return 受影响行数（1 表示成功，0 表示未找到）
     */
    public int update(Bookmark bookmark) {
        // 1. 更新业务字段并刷新最后修改时间
        String sql = "UPDATE bookmarks SET url = ?, title = ?, icon = ?, category = ?, update_time = ? WHERE id = ?";

        // 2. 返回受影响行数
        try (Connection conn = DatabaseMgr.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, bookmark.getUrl());
            ps.setString(2, bookmark.getTitle());
            ps.setString(3, bookmark.getIcon());
            ps.setString(4, bookmark.getCategory());
            ps.setString(5, LocalDateTime.now().format(FORMATTER));
            ps.setInt(6, bookmark.getId());
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update bookmark: " + bookmark, e);
        }
    }

    /**
     * 统计指定分类下的书签总数。
     *
     * @param category 分类名；为 {@code null} 或空字符串时统计全部分类
     * @return 书签数量
     */
    public int count(String category) {
        // 1. 根据是否提供分类拼接过滤条件
        boolean hasCategory = category != null && !category.isBlank();
        String sql = (hasCategory
                ? "SELECT COUNT(*) FROM bookmarks WHERE category = ?"
                : "SELECT COUNT(*) FROM bookmarks");

        // 2. 读取计数结果
        try (Connection conn = DatabaseMgr.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            if (hasCategory) {
                ps.setString(1, category);
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to count bookmarks by category: " + category, e);
        }
        return 0;
    }

    /**
     * 批量插入并跳过约束冲突的记录：在单个事务内逐条执行，
     * 遇到数据库约束违规时跳过该记录并继续，最终提交所有成功记录。
     *
     * @param list 待插入的书签集合
     * @return 成功插入的书签数量
     */
    public BatchResult batchInsertSkipErrors(List<Bookmark> list) {
        if (list == null || list.isEmpty()) {
            return new BatchResult(0, 0);
        }

        String sql = "INSERT INTO bookmarks (url, title, icon, category, add_date, folder_id) VALUES (?, ?, ?, ?, ?, ?)";
        Connection conn = DatabaseMgr.getConnection();
        boolean originalAutoCommit = true;

        // 1. 关闭自动提交，开启手动事务
        try {
            originalAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to begin batch transaction", e);
        }

        int success = 0;
        int failures = 0;

        // 2. 逐条执行，单条约束冲突时跳过并继续
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (Bookmark b : list) {
                try {
                    ps.setString(1, b.getUrl());
                    ps.setString(2, b.getTitle());
                    ps.setString(3, b.getIcon());
                    ps.setString(4, b.getCategory());
                    // add_date 列非空：未提供时默认取当前时间
                    LocalDateTime addDate = b.getAddDate() != null ? b.getAddDate() : LocalDateTime.now();
                    ps.setString(5, toText(addDate));
                    ps.setInt(6, b.getFolderId() != null ? b.getFolderId() : 1);

                    if (ps.executeUpdate() > 0) {
                        success++;
                    }
                } catch (SQLException e) {
                    if (isConstraintViolation(e)) {
                        // 3. 约束冲突：跳过该记录并统计失败数
                        failures++;
                    } else {
                        // 4. 非约束类错误：回滚并向上抛出
                        try {
                            conn.rollback();
                        } catch (SQLException rollbackEx) {
                            throw new RuntimeException("Failed to rollback batch insert", rollbackEx);
                        }
                        throw new RuntimeException("Failed to batch insert bookmarks", e);
                    }
                }
            }
            // 5. 提交所有成功记录
            conn.commit();
        } catch (SQLException e) {
            // 6. 事务提交阶段异常时回滚
            try {
                conn.rollback();
            } catch (SQLException rollbackEx) {
                throw new RuntimeException("Failed to rollback batch insert", rollbackEx);
            }
            throw new RuntimeException("Failed to batch insert bookmarks", e);
        } finally {
            // 7. 恢复原有的自动提交模式
            try {
                conn.setAutoCommit(originalAutoCommit);
            } catch (SQLException e) {
                throw new RuntimeException("Failed to restore auto-commit mode", e);
            }
        }
        return new BatchResult(success, failures);
    }

    /**
     * 清空 bookmarks 表全部记录，供导入前重置干净状态。
     */
    public void deleteAll() {
        // 1. 直接删除全部书签记录（无外键约束，无需关心顺序）
        String sql = "DELETE FROM bookmarks";
        try (Connection conn = DatabaseMgr.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete all bookmarks", e);
        }
    }

    /** 判断异常链中是否包含数据库约束冲突（SQLite: SQLITE_CONSTRAINT=19）。 */
    private boolean isConstraintViolation(SQLException e) {
        Throwable t = e;
        while (t != null) {
            if (t instanceof SQLIntegrityConstraintViolationException) {
                return true;
            }
            if (t instanceof SQLException && ((SQLException) t).getErrorCode() == 19) {
                return true;
            }
            String msg = t.getMessage();
            if (msg != null && msg.toLowerCase().contains("constraint")) {
                return true;
            }
            t = t.getCause();
        }
        return false;
    }

    /**
     * 将一行 {@link ResultSet} 映射为 {@link Bookmark} 实体。
     */
    private Bookmark mapRow(ResultSet rs) throws SQLException {
        return new Bookmark(
                rs.getInt("id"),
                rs.getString("url"),
                rs.getString("title"),
                rs.getString("icon"),
                rs.getString("category"),
                toLocalDateTime(rs.getString("add_date")),
                toLocalDateTime(rs.getString("create_time")),
                toLocalDateTime(rs.getString("update_time")), 
                rs.getInt("folder_id")
        );
    }

    /** 将 {@link LocalDateTime} 转为数据库存储用的文本，null 时返回 null。 */
    private String toText(LocalDateTime time) {
        return time == null ? null : time.format(FORMATTER);
    }

    /** 将数据库文本解析为 {@link LocalDateTime}，空或非法值时返回 null。 */
    private LocalDateTime toLocalDateTime(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(text, FORMATTER);
        } catch (Exception e) {
            return null;
        }
    }
}
