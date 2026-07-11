package com.bookmark.db;

import lombok.SneakyThrows;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.stream.Collectors;

/**
 * <p>
 * 单例，SQLite 数据库连接管理与初始化
 * </p>
 *
 * @author DonkeyFish
 * @since 2026-7-7
 */
public class DatabaseMgr {
    private static final String URL_PREFIX = "jdbc:sqlite:";
    private static volatile DatabaseMgr INSTANCE;
    private static volatile Connection CONNECTION;

    private DatabaseMgr() {
    }

    // 1. 若实例已存在则直接返回
    // 2. 创建新实例并打开数据库连接
    // 3. 执行 schema.sql 初始化表结构
    // 4. 赋值给 INSTANCE
    public static void initialize() {
        if (INSTANCE != null) {
            return;
        }
        synchronized (DatabaseMgr.class) {
            if (INSTANCE == null) {
                DatabaseMgr instance = new DatabaseMgr();
                instance.openConnection();
                instance.executeSchema();
                INSTANCE = instance;
            }
        }
    }

    // 1. 校验实例是否已初始化
    // 2. 若连接已关闭则自动重连，保证返回活跃连接
    // 3. 返回活跃的数据库连接
    public static Connection getConnection() {
        if (INSTANCE == null) {
            throw new IllegalStateException("DatabaseMgr not initialized. Call initialize() first.");
        }
        INSTANCE.ensureOpen();
        return CONNECTION;
    }

    // 1. 检查连接是否为空或已关闭
    // 2. 若是则重新打开连接，确保调用方始终拿到可用连接
    @SneakyThrows
    private void ensureOpen() {
        if (CONNECTION == null || CONNECTION.isClosed()) {
            openConnection();
        }
    }

    // 1. 检查连接是否为空或已关闭
    // 2. 创建新的 SQLite JDBC 连接
    // 3. 开启自动提交
    @SneakyThrows
    private void openConnection() {
        if (CONNECTION == null || CONNECTION.isClosed()) {
            String dbPath = System.getenv("BOOKMARK_DB_PATH") != null 
                ? System.getenv("BOOKMARK_DB_PATH") 
                : "bookmarkmgr.db";
            CONNECTION = DriverManager.getConnection(URL_PREFIX + dbPath);
            CONNECTION.setAutoCommit(true);
        }
    }

    // 1. 读取 schema.sql 内容
    // 2. 执行 SQL 语句（建表、索引）
    private void executeSchema() {
        String sql = readSchema();
        if (sql == null || sql.isBlank()) {
            return;
        }
        // 按分号拆分，并移除每条语句首尾空白
        String[] statements = sql.split(";");
        try (Statement stmt = CONNECTION.createStatement()) {
            for (String statement : statements) {
                String trimmed = statement.trim();
                if (!trimmed.isEmpty()) {
                    stmt.execute(trimmed);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to execute schema.sql", e);
        }
    }

    // 1. 从类路径加载 /schema.sql 资源
    // 2. 读取并拼接为完整 SQL 字符串
    private String readSchema() {
        String resourcePath = "/schema.sql";
        try (InputStream is = getClass().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IllegalStateException("schema.sql not found on classpath at " + resourcePath);
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
                return reader.lines().collect(Collectors.joining("\n"));
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to read schema.sql", e);
        }
    }
}
