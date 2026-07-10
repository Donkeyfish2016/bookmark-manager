package com.bookmark.service;

import com.bookmark.db.BookmarkDAO;
import com.bookmark.html.HtmlBookmarkParser;
import com.bookmark.html.HtmlBookmarkWriter;
import com.bookmark.model.BatchResult;
import com.bookmark.model.Bookmark;
import com.bookmark.model.Folder;
import com.bookmark.model.ImportResult;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
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
    private final FolderService folderService;
    // HTML 导入/导出工具（无状态，直接持有实例）
    private final HtmlBookmarkParser htmlParser = new HtmlBookmarkParser();
    private final HtmlBookmarkWriter htmlWriter = new HtmlBookmarkWriter();

    // 1. 通过构造器注入 DAO 依赖，保证依赖不可变且非空
    public BookmarkService(BookmarkDAO bookmarkDAO, FolderService folderService) {
        if (bookmarkDAO == null) {
            throw new IllegalArgumentException("BookmarkDAO must not be null");
        }
        if (folderService == null) {
            throw new IllegalArgumentException("FolderService must not be null");
        }
        this.bookmarkDAO = bookmarkDAO;
        this.folderService = folderService;
    }

    /**
     * 新增书签。
     *
     * @return 已持久化且回填主键的完整书签对象
     */
    /** 暴露 FolderService，供命令树组装复用同一服务实例与数据库连接。 */
    public FolderService getFolderService() {
        return folderService;
    }

    public Bookmark add(String url, String title, String icon, String category) {
        // 1. 校验所有入参非空且非空串
        requireNonBlank(url, "url");
        requireNonBlank(title, "title");
        requireNonBlank(category, "category");

        // 2. 解析分类获取文件夹ID
        Integer folderId = parseCategory(category);

        // 3. 构造实体并写入数据库（add_date 取当前时间）
        Bookmark bookmark = new Bookmark(null, url, title, icon, category, LocalDateTime.now(), null, null, folderId);
        int id = bookmarkDAO.insert(bookmark);

        // 4. 回填自增主键后返回完整对象
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
        // requireNonBlank(icon, "icon");
        requireNonBlank(category, "category");

        // 2. 解析分类获取文件夹ID
        Integer folderId = parseCategory(category);

        // 3. 以 id 定位构造实体并委托 DAO 更新
        Bookmark bookmark = new Bookmark(id, url, title, icon, category, null, null, null, folderId);
        return bookmarkDAO.update(bookmark) > 0;
    }

    /**
     * 统计指定分类下的书签数量。
     *
     * @return 书签数量
     */
    public long count(String category) {
        return bookmarkDAO.count(category);
    }

    /**
     * 查询全部书签（不分页、不分类过滤），用于导出等场景。
     */
    public List<Bookmark> listAll() {
        return bookmarkDAO.query(null, 1, Integer.MAX_VALUE);
    }

    /**
     * 从 Edge 书签 HTML 文件导入书签：先清空旧数据，再按层级树写入文件夹与书签。
     *
     * @param filePath 书签 HTML 文件路径
     * @return 导入结果（书签总数与文件夹总数）
     */
    public ImportResult importFromHtml(String filePath) {
        // 1. 校验文件路径非空
        requireNonBlank(filePath, "filePath");

        // 2. 解析 HTML 得到层级文件夹树
        Folder root = htmlParser.parseToTree(new File(filePath));

        // 3. 导入前清空现有数据，保证干净状态（先书签后文件夹，避免约束冲突）
        folderService.clearAllFolders();

        // 4. 递归插入文件夹树：回填数据库自增 id 并维护 parent_id 层级
        int folderCount = 0;
        for (Folder top : root.getChildren().values()) {
            folderCount += folderService.insertFolder(top, null);
        }

        // 5. 收集整棵树中的全部书签，并依据所在文件夹设置 folderId
        List<Bookmark> bookmarks = new ArrayList<>();
        collectBookmarks(root, bookmarks);
        Integer bookmarksBarId = null;
        for (Bookmark b : bookmarks) {
            Integer fid = b.getFolderId();
            if (fid == null || fid <= 0) {
                // 5.1 防御：无法解析所属文件夹时归入默认“Bookmarks Bar”
                if (bookmarksBarId == null) {
                    bookmarksBarId = folderService.createFolder(DEFAULT_FOLDER_NAME, null);
                }
                b.setFolderId(bookmarksBarId);
            }
        }

        // 6. 批量插入书签（沿用现有事务策略，跳过约束冲突记录）
        BatchResult batch = bookmarkDAO.batchInsertSkipErrors(bookmarks);

        // 7. 返回结构化结果：书签总数与文件夹总数
        return new ImportResult(batch.getSuccess(), folderCount);
    }

    /**
     * 深度优先收集整棵树中的所有书签，并将其 folderId 设为所在文件夹的数据库 id。
     */
    private void collectBookmarks(Folder folder, List<Bookmark> out) {
        for (Bookmark b : folder.getBookmarks()) {
            // 将书签挂到当前文件夹的数据库 id（解析失败时置空，由调用方兜底）
            b.setFolderId(folder.getId() > 0 ? folder.getId() : null);
            out.add(b);
        }
        for (Folder child : folder.getChildren().values()) {
            collectBookmarks(child, out);
        }
    }

    /**
     * 将当前所有书签导出为 Edge 书签 HTML 文件。
     *
     * @param outputPath 输出 HTML 文件路径
     * @return 导出的书签数量
     */
    public int exportToHtml(String outputPath) {
        // 1. 校验输出路径非空且非空串
        requireNonBlank(outputPath, "outputPath");

        // 2. 加载完整文件夹树（含嵌套文件夹与书签）；
        //    FolderService.loadFolderTree() 内部通过 FolderDAO 与 BookmarkDAO 拉取全量数据并组装层级
        Folder rootFolder = folderService.loadFolderTree();

        // 3. 边缘情况：树为空时兜底构造虚拟根，确保仍能写出可被浏览器导入的合法空骨架
        if (rootFolder == null) {
            rootFolder = new Folder();
            rootFolder.setId(0);
            rootFolder.setName("ROOT");
            rootFolder.setRoot(true);
            rootFolder.setChildren(new java.util.LinkedHashMap<>());
            rootFolder.setBookmarks(new ArrayList<>());
        }

        // 4. 委托 HtmlBookmarkWriter 写出标准 Netscape 书签 HTML（空树也会生成 <DL></DL> 骨架）
        try {
            htmlWriter.write(rootFolder, new File(outputPath));
        } catch (IOException e) {
            // 4.1 文件 I/O 异常统一包装为运行时异常并保留原始原因，便于上层定位
            throw new RuntimeException("Failed to export bookmarks to: " + outputPath, e);
        }

        // 5. 统计全部导出的书签数量并返回
        return countBookmarks(rootFolder);
    }

    private int countBookmarks(Folder folder) {
        int count = folder.getBookmarks().size();
        for (Folder child : folder.getChildren().values()) {
            count += countBookmarks(child);
        }
        return count;
    }

    /**
     * 发送书签 HTML 文件到指定邮箱。
     * @param to
     * @param subject
     * @param smtpHost
     * @param smtpPort
     * @param smtpUser
     * @param smtpPass
     * @param starttls
     */
    public void emailBookmarks(String to, String subject, 
                               String smtpHost, int smtpPort, 
                               String smtpUser, String smtpPass, 
                               boolean starttls) {
        // 1. 导出html文件到临时文件

        // 2. 调用EamilService发送邮件
    }

    /** 默认文件夹名：用于兜底收纳无法解析所属文件夹的书签。 */
    private static final String DEFAULT_FOLDER_NAME = "Unknown Folder";

    private Integer parseCategory(String category) {
        // 2. 按照category拆解文件夹名称，获取或创建文件夹ID
        String folderName = null;
        String parentFolderName = null;
        if (!category.contains("/")) {
            folderName = category;
        } else {
            String[] parts = category.split("/");
            folderName = parts[parts.length - 1];
            parentFolderName = parts[parts.length - 2];
        }
        // 获取或创建文件夹 ID
        // Integer folderId = 999;
        Integer folderId = folderService.getFolderIdByName(folderName);
        if (folderId == null) {
            Integer parentFolderId = null;
            if (parentFolderName != null) {
                parentFolderId = folderService.getFolderIdByName(parentFolderName);
            }
            folderId = folderService.createFolder(folderName, parentFolderId);
        }
        return folderId;
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