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
    private static final String URL = "jdbc:sqlite:bookmarks.db";
    private static volatile DatabaseMgr INSTANCE;
    private static volatile Connection CONNECTION;

    private DatabaseMgr() {
    }

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

    public static Connection getConnection() {
        if (CONNECTION == null) {
            throw new IllegalStateException("DatabaseMgr not initialized. Call initialize() first.");
        }
        return CONNECTION;
    }

    @SneakyThrows
    private void openConnection() {
        if (CONNECTION == null || CONNECTION.isClosed()) {
            CONNECTION = DriverManager.getConnection(URL);
            CONNECTION.setAutoCommit(true);
        }
    }

    private void executeSchema() {
        String sql = readSchema();
        if (sql == null || sql.isBlank()) {
            return;
        }
        try (Statement stmt = CONNECTION.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to execute schema.sql", e);
        }
    }

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
