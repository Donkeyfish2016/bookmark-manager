package com.bookmark.service;

import com.bookmark.db.BookmarkDAO;
import com.bookmark.db.FolderDAO;
import com.bookmark.model.Bookmark;
import com.bookmark.model.Folder;

import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;

/**
 * 文件夹业务逻辑层：在 DAO 之上封装树构建、校验与持久化编排。
 */
public class FolderService {

    private final FolderDAO folderDAO;
    private final BookmarkDAO bookmarkDAO;

    /** 通过构造器注入 DAO，保持依赖不可变且非空。 */
    public FolderService(FolderDAO folderDAO, BookmarkDAO bookmarkDAO) {
        // 1. 校验 FolderDAO 非空
        if (folderDAO == null) {
            throw new IllegalArgumentException("FolderDAO must not be null");
        }
        // 2. 校验 BookmarkDAO 非空
        if (bookmarkDAO == null) {
            throw new IllegalArgumentException("BookmarkDAO must not be null");
        }
        // 3. 赋值依赖
        this.folderDAO = folderDAO;
        this.bookmarkDAO = bookmarkDAO;
    }

    /** 默认构造器，便于兼容现有调用方。 */
    public FolderService() {
        // 1. 使用默认构造的 DAO 实例初始化
        this(new FolderDAO(), new BookmarkDAO());
    }

    /**
     * 构建完整的文件夹树，根节点为虚拟根节点，子节点与书签按层级挂载。
     */
    public Folder loadFolderTree() {
        // 1. 从数据库加载全部文件夹与书签
        List<Folder> folders = folderDAO.queryAll();
        List<Bookmark> bookmarks = bookmarkDAO.queryAll();

        // 2. 创建虚拟根节点，用于挂载所有顶级文件夹
        Folder root = new Folder();
        root.setId(0);
        root.setName("ROOT");
        root.setParentId(null);
        root.setRoot(true);
        root.setChildren(new LinkedHashMap<>());
        root.setBookmarks(new ArrayList<>());

        // 3. 建立文件夹 id 到内存节点的索引映射
        Map<Integer, Folder> index = new LinkedHashMap<>();
        for (Folder folder : folders) {
            // 4. 复制每个文件夹为独立内存节点，避免共享可变状态
            Folder node = copyFolder(folder);
            index.put(folder.getId(), node);
        }

        // 5. 组装层级关系：将每个节点挂载到其父节点或虚拟根节点下
        for (Folder folder : folders) {
            Folder node = index.get(folder.getId());
            if (node == null) {
                continue;
            }
            if (folder.getParentId() == null) {
                root.getChildren().put(node.getName(), node);
            } else {
                Folder parent = index.get(folder.getParentId());
                if (parent != null) {
                    parent.getChildren().put(node.getName(), node);
                } else {
                    root.getChildren().put(node.getName(), node);
                }
            }
        }

        // 6. 将每条书签挂载到对应的文件夹节点下
        for (Bookmark bookmark : bookmarks) {
            if (bookmark.getFolderId() == null) {
                continue;
            }
            Folder target = index.get(bookmark.getFolderId());
            if (target != null) {
                target.getBookmarks().add(bookmark);
            }
        }
        return root;
    }

    /**
     * 根据文件夹主键查询单个文件夹。
     */
    public Folder getFolderById(int folderId) {
        // 1. 校验 id 为正数
        requirePositive(folderId, "folderId");
        // 2. 委托 DAO 按主键查询
        return folderDAO.queryById(folderId);
    }

    public Integer getFolderIdByName(String folderName) {
        // 1. 校验名称非空
        requireNonBlank(folderName, "folderName");
        // 2. 构造查询条件，固定返回全部分页结果
        Folder criteria = new Folder();
        criteria.setName(folderName);
        List<Folder> result = folderDAO.query(criteria, 1, 1, "id");
        if (result.isEmpty()) {
            return null;
        }
        return result.get(0).getId();
    }

    /**
     * 查询全部文件夹（用于根节点发现与扁平遍历）。
     */
    public List<Folder> getAllFolders() {
        // 1. 委托 DAO 返回全部文件夹
        return folderDAO.queryAll();
    }

    /**
     * 查询指定父文件夹下的直接子文件夹。
     */
    public List<Folder> getSubFolders(int parentId) {
        // 1. 校验 id 为正数
        requirePositive(parentId, "parentId");
        // 2. 构造查询条件，固定返回全部分页结果
        Folder criteria = new Folder();
        criteria.setParentId(parentId);
        return folderDAO.query(criteria, 1, Integer.MAX_VALUE, "id");
    }

    /**
     * 创建文件夹，校验同级名称唯一性后写入数据库。
     */
    public int createFolder(String name, Integer parentId) {
        // 1. 无显式 isRoot 标记时，parentId 为 NULL 即视为根文件夹
        return createFolder(name, parentId, parentId == null);
    }

    /**
     * 创建文件夹，支持显式指定是否为根文件夹（--root 场景）。
     * 当 {@code isRoot} 为 {@code true} 时忽略 {@code parentId}，强制成为独立根文件夹。
     */
    public int createFolder(String name, Integer parentId, boolean isRoot) {
        // 1. 校验名称非空
        requireNonBlank(name, "name");
        // 2. 根文件夹忽略父 id；否则校验父文件夹存在性
        Integer effectiveParent = isRoot ? null : parentId;
        if (effectiveParent != null) {
            requirePositive(effectiveParent, "parentId");
            if (folderDAO.queryById(effectiveParent) == null) {
                throw new IllegalArgumentException("Parent folder does not exist: " + effectiveParent);
            }
        }
        // 3. 检查同级名称冲突
        if (folderDAO.existsByNameAndParent(name, effectiveParent)) {
            throw new IllegalArgumentException("Folder '" + name + "' already exists under the same parent");
        }

        // 4. 组装实体并设置默认时间字段
        Folder folder = new Folder();
        folder.setName(name);
        folder.setParentId(effectiveParent);
        folder.setRoot(isRoot);
        LocalDateTime now = LocalDateTime.now();
        folder.setAddDate(now);
        folder.setLastModified(now);
        // 5. 持久化并返回自增主键
        int id = folderDAO.insert(folder);
        folder.setId(id);
        return id;
    }

    /**
     * 递归插入文件夹：写入数据库并回填自增 id，再按 parentId 下钻子文件夹。
     *
     * @param folder   当前文件夹节点
     * @param parentId 父文件夹数据库 id（顶层为 {@code null}）
     * @return 本次递归插入的文件夹数量（含自身）
     */
    public int insertFolder(Folder folder, Integer parentId) {
        // 1. 设置父级 id（顶层文件夹 parentId 为 null，将被标记 is_root）
        folder.setParentId(parentId);
        // 2. 持久化并取回数据库自增主键
        int id = folderDAO.insert(folder);
        folder.setId(id);
        // 3. 递归处理子文件夹，parentId 指向当前文件夹的数据库 id
        int count = 1;
        for (Folder child : folder.getChildren().values()) {
            count += insertFolder(child, id);
        }
        return count;
    }

    /**
     * 重命名文件夹，更新名字并刷新最后修改与更新时间。
     */
    public void renameFolder(int folderId, String newName) {
        // 1. 校验入参
        requirePositive(folderId, "folderId");
        requireNonBlank(newName, "newName");

        // 2. 查询目标文件夹是否存在
        Folder folder = folderDAO.queryById(folderId);
        if (folder == null) {
            throw new IllegalArgumentException("Folder does not exist: " + folderId);
        }
        // 3. 检查新名称在相同父级下是否冲突
        if (!Objects.equals(folder.getName(), newName)
                && folderDAO.existsByNameAndParent(newName, folder.getParentId())) {
            throw new IllegalArgumentException("Folder '" + newName + "' already exists under the same parent");
        }

        // 4. 更新字段并刷新修改时间
        folder.setName(newName);
        LocalDateTime now = LocalDateTime.now();
        folder.setLastModified(now);
        folder.setUpdateTime(now);
        folderDAO.update(folder);
    }

    /**
     * 移动文件夹到新的父文件夹，禁止形成环形引用。
     */
    public void moveFolder(int folderId, Integer newParentId) {
        // 1. 校验源文件夹 id 为正数并查询源文件夹
        requirePositive(folderId, "folderId");
        Folder folder = folderDAO.queryById(folderId);
        if (folder == null) {
            throw new IllegalArgumentException("Folder does not exist: " + folderId);
        }
        // 2. 若指定新父文件夹，校验其存在性
        if (newParentId != null) {
            requirePositive(newParentId, "newParentId");
            if (folderDAO.queryById(newParentId) == null) {
                throw new IllegalArgumentException("Target parent folder does not exist: " + newParentId);
            }
        }
        // 3. 禁止将文件夹移动到自身
        if (Objects.equals(newParentId, folderId)) {
            throw new IllegalArgumentException("Folder cannot be moved into itself");
        }
        // 4. 禁止移动到自己的后代节点（避免循环引用）
        if (isDescendant(folderId, newParentId)) {
            throw new IllegalArgumentException("circular folder move detected: target is one of this folder's descendants");
        }

        // 5. 更新父级关系及根标志并持久化
        folder.setParentId(newParentId);
        folder.setRoot(newParentId == null);
        folder.setLastModified(LocalDateTime.now());
        folderDAO.update(folder);
    }

    /**
     * 删除空文件夹；若存在子文件夹或书签则拒绝删除。
     */
    public void deleteFolder(int folderId) {
        // 1. 校验 id 并查询目标文件夹
        requirePositive(folderId, "folderId");
        Folder folder = folderDAO.queryById(folderId);
        if (folder == null) {
            throw new IllegalArgumentException("Folder does not exist: " + folderId);
        }
        // 2. 检查是否存在子文件夹
        if (folderDAO.countByParentId(folderId) > 0) {
            throw new IllegalStateException("Cannot delete folder because it contains child folders");
        }
        // 3. 检查是否存在下属书签
        if (!bookmarkDAO.queryByFolderId(folderId).isEmpty()) {
            throw new IllegalStateException("Cannot delete folder because it contains bookmarks");
        }
        // 4. 空文件夹可直接删除
        folderDAO.deleteById(folderId);
    }

    public void clearAllFolders() {
        // 1. 删除所有书签
        bookmarkDAO.deleteAll();
        // 2. 删除所有文件夹
        folderDAO.deleteAll();
    }

    /**
     * 查询指定文件夹下的所有书签。
     */
    public List<Bookmark> getBookmarksByFolder(int folderId) {
        // 1. 校验 id 为正数
        requirePositive(folderId, "folderId");
        // 2. 委托 DAO 按文件夹查询书签
        return bookmarkDAO.queryByFolderId(folderId);
    }

    /** 复制 DAO 查询结果为内存树使用的节点对象。 */
    private Folder copyFolder(Folder source) {
        // 1. 创建新节点并复制基础字段
        Folder node = new Folder();
        node.setId(source.getId());
        node.setName(source.getName());
        node.setParentId(source.getParentId());
        node.setRoot(source.isRoot());
        // 2. 初始化集合容器
        node.setChildren(new LinkedHashMap<>());
        node.setBookmarks(new ArrayList<>());
        // 3. 复制时间字段
        node.setAddDate(source.getAddDate());
        node.setLastModified(source.getLastModified());
        node.setCreateTime(source.getCreateTime());
        node.setUpdateTime(source.getUpdateTime());
        return node;
    }

    /** 判断 targetId 是否是 ancestorId 的后代。 */
    private boolean isDescendant(int ancestorId, Integer targetId) {
        // 1. 边界条件：目标为 null 或等于祖先自身，不视为后代
        if (targetId == null || targetId == ancestorId) {
            return false;
        }

        // 2. 建立父到子列表的邻接表
        Map<Integer, List<Integer>> childrenByParent = new LinkedHashMap<>();
        for (Folder folder : folderDAO.queryAll()) {
            Integer parentId = folder.getParentId();
            if (parentId == null) {
                continue;
            }
            childrenByParent.computeIfAbsent(parentId, key -> new ArrayList<>()).add(folder.getId());
        }

        // 3. 从祖先 id 开始广度优先搜索，若找到 targetId 则为后代
        Queue<Integer> queue = new ArrayDeque<>();
        queue.offer(ancestorId);
        while (!queue.isEmpty()) {
            int current = queue.poll();
            for (int childId : childrenByParent.getOrDefault(current, List.of())) {
                if (childId == targetId) {
                    return true;
                }
                queue.offer(childId);
            }
        }
        return false;
    }

    /** 校验字符串非空且非空串。 */
    private void requireNonBlank(String value, String field) {
        // 1. 检查 null 或空白字符串
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Field '" + field + "' must not be null or empty");
        }
    }

    /** 校验整数为正数。 */
    private void requirePositive(int value, String field) {
        // 1. 检查是否大于 0
        if (value <= 0) {
            throw new IllegalArgumentException("Field '" + field + "' must be a positive integer");
        }
    }

}
