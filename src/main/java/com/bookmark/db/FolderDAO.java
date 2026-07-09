package com.bookmark.db;

import com.bookmark.model.Folder;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * <p>
 * 文件夹数据访问对象 (CRUD + 搜索)
 * </p>
 * <p>
 * 约定：
 * 1. 通过 {@link DatabaseMgr} 获取共享的 SQLite 连接，所有 SQL 操作均在此类内封装。
 * 2. 数据库以 {@code TEXT} 形式（{@code yyyy-MM-dd HH:mm:ss}）存储时间字段，
 *    与实体类 {@link Folder} 的 {@link LocalDateTime} 字段互转。
 * 3. 分页查询的 {@code page} 从 1 开始计数。
 * </p>
 */
public class FolderDAO {

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public int insert(Folder folder) {
        String sql = "INSERT INTO folders (name, parent_id, is_root, add_date, last_modified) " +
                "VALUES (?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseMgr.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, folder.getName());
            ps.setObject(2, folder.getParentId());
            ps.setBoolean(3, folder.getParentId() == null);
            ps.setString(4, toText(folder.getAddDate() != null ? folder.getAddDate() : LocalDateTime.now()));
            ps.setString(5, toText(folder.getLastModified() != null ? folder.getLastModified() : LocalDateTime.now()));

            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to insert folder: " + folder, e);
        }
        return -1;
    }

    public int update(Folder folder) {
        String sql = "UPDATE folders SET name = ?, parent_id = ?, is_root = ?, " +
                "last_modified = ?, update_time = ? WHERE id = ?";

        try (Connection conn = DatabaseMgr.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, folder.getName());
            Integer parentId = folder.getParentId();
            ps.setObject(2, parentId);
            ps.setBoolean(3, parentId == null);
            ps.setString(4, toText(folder.getLastModified() != null ? folder.getLastModified() : LocalDateTime.now()));
            ps.setString(5, LocalDateTime.now().format(FORMATTER));
            ps.setInt(6, folder.getId());

            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update folder: " + folder, e);
        }
    }

    public int deleteById(int folderId) {
        if (countByParentId(folderId) > 0) {
            throw new IllegalStateException("Cannot delete folder with id " + folderId + ": it contains child elements");
        }

        String sql = "DELETE FROM folders WHERE id = ?";

        try (Connection conn = DatabaseMgr.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, folderId);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete folder by id: " + folderId, e);
        }
    }

    public Folder queryById(int folderId) {
        String sql = "SELECT id, name, parent_id, is_root, add_date, last_modified, create_time, update_time " +
                "FROM folders WHERE id = ?";

        try (Connection conn = DatabaseMgr.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, folderId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to query folder by id: " + folderId, e);
        }
        return null;
    }

    public List<Folder> query(Folder criteria, int page, int pageSize, String sortBy) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT id, name, parent_id, is_root, add_date, last_modified, create_time, update_time FROM folders WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (criteria != null) {
            String name = criteria.getName();
            if (name != null && !name.isBlank()) {
                sql.append(" AND name LIKE ?");
                params.add("%" + name + "%");
            }
            if (criteria.isRoot()) {
                sql.append(" AND parent_id IS NULL");
            }
            if (criteria.getParentId() != null) {
                sql.append(" AND parent_id = ?");
                params.add(criteria.getParentId());
            }
        }

        String sortColumn = "name".equalsIgnoreCase(sortBy) ? "name" : "id";
        sql.append(" ORDER BY ").append(sortColumn);

        int offset = (Math.max(page, 1) - 1) * pageSize;
        sql.append(" LIMIT ? OFFSET ?");

        List<Folder> result = new ArrayList<>();
        try (Connection conn = DatabaseMgr.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {

            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            ps.setInt(params.size() + 1, pageSize);
            ps.setInt(params.size() + 2, offset);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to query folders", e);
        }
        return result;
    }

    public List<Folder> queryAll() {
        String sql = "SELECT id, name, parent_id, is_root, add_date, last_modified, create_time, update_time " +
                "FROM folders ORDER BY parent_id, id";

        List<Folder> result = new ArrayList<>();
        try (Connection conn = DatabaseMgr.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                result.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to query all folders", e);
        }
        return result;
    }

    public boolean existsByNameAndParent(String name, Integer parentId) {
        String sql = parentId == null
                ? "SELECT COUNT(*) FROM folders WHERE name = ? AND parent_id IS NULL"
                : "SELECT COUNT(*) FROM folders WHERE name = ? AND parent_id = ?";

        try (Connection conn = DatabaseMgr.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, name);
            if (parentId != null) {
                ps.setInt(2, parentId);
            }

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to check folder existence: " + name, e);
        }
        return false;
    }

    public int countByParentId(int parentId) {
        String sql = "SELECT COUNT(*) FROM folders WHERE parent_id = ?";

        try (Connection conn = DatabaseMgr.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, parentId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to count folders by parent id: " + parentId, e);
        }
        return 0;
    }

    public void batchInsert(List<Folder> folders) {
        if (folders == null || folders.isEmpty()) {
            return;
        }

        String sql = "INSERT INTO folders (name, parent_id, is_root, add_date, last_modified) VALUES (?, ?, ?, ?, ?)";
        Connection conn = DatabaseMgr.getConnection();
        boolean originalAutoCommit = true;

        try {
            originalAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to begin batch transaction", e);
        }

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (Folder folder : folders) {
                ps.setString(1, folder.getName());
                ps.setObject(2, folder.getParentId());
                ps.setBoolean(3, folder.getParentId() == null);
                ps.setString(4, toText(folder.getAddDate() != null ? folder.getAddDate() : LocalDateTime.now()));
                ps.setString(5, toText(folder.getLastModified() != null ? folder.getLastModified() : LocalDateTime.now()));
                ps.executeUpdate();
            }
            conn.commit();
        } catch (SQLException e) {
            try {
                conn.rollback();
            } catch (SQLException rollbackEx) {
                throw new RuntimeException("Failed to rollback batch insert", rollbackEx);
            }
            throw new RuntimeException("Failed to batch insert folders", e);
        } finally {
            try {
                conn.setAutoCommit(originalAutoCommit);
            } catch (SQLException e) {
                throw new RuntimeException("Failed to restore auto-commit mode", e);
            }
        }
    }

    private Folder mapRow(ResultSet rs) throws SQLException {
        Folder folder = new Folder();
        folder.setId(rs.getInt("id"));
        folder.setName(rs.getString("name"));
        folder.setParentId((Integer) rs.getObject("parent_id"));
        folder.setRoot(rs.getBoolean("is_root"));
        folder.setAddDate(toLocalDateTime(rs.getString("add_date")));
        folder.setLastModified(toLocalDateTime(rs.getString("last_modified")));
        folder.setCreateTime(toLocalDateTime(rs.getString("create_time")));
        folder.setUpdateTime(toLocalDateTime(rs.getString("update_time")));
        return folder;
    }

    private String toText(LocalDateTime time) {
        return time == null ? null : time.format(FORMATTER);
    }

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
