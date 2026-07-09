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
        if (folderDAO == null) {
            throw new IllegalArgumentException("FolderDAO must not be null");
        }
        if (bookmarkDAO == null) {
            throw new IllegalArgumentException("BookmarkDAO must not be null");
        }
        this.folderDAO = folderDAO;
        this.bookmarkDAO = bookmarkDAO;
    }

    /** 默认构造器，便于兼容现有调用方。 */
    public FolderService() {
        this(new FolderDAO(), new BookmarkDAO());
    }

    /**
     * 构建完整的文件夹树，根节点为虚拟根节点，子节点与书签按层级挂载。
     */
    public Folder loadFolderTree() {
        List<Folder> folders = folderDAO.queryAll();
        List<Bookmark> bookmarks = bookmarkDAO.queryAll();

        Folder root = new Folder();
        root.setId(0);
        root.setName("ROOT");
        root.setParentId(null);
        root.setRoot(true);
        root.setChildren(new LinkedHashMap<>());
        root.setBookmarks(new ArrayList<>());

        Map<Integer, Folder> index = new LinkedHashMap<>();
        for (Folder folder : folders) {
            Folder node = copyFolder(folder);
            index.put(folder.getId(), node);
        }

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
        requirePositive(folderId, "folderId");
        return folderDAO.queryById(folderId);
    }

    /**
     * 查询指定父文件夹下的直接子文件夹。
     */
    public List<Folder> getSubFolders(int parentId) {
        requirePositive(parentId, "parentId");
        Folder criteria = new Folder();
        criteria.setParentId(parentId);
        return folderDAO.query(criteria, 1, Integer.MAX_VALUE, "id");
    }

    /**
     * 创建文件夹，校验同级名称唯一性后写入数据库。
     */
    public int createFolder(String name, Integer parentId) {
        requireNonBlank(name, "name");
        if (parentId != null) {
            requirePositive(parentId, "parentId");
            if (folderDAO.queryById(parentId) == null) {
                throw new IllegalArgumentException("Parent folder does not exist: " + parentId);
            }
        }
        if (folderDAO.existsByNameAndParent(name, parentId)) {
            throw new IllegalArgumentException("Folder '" + name + "' already exists under the same parent");
        }

        Folder folder = new Folder();
        folder.setName(name);
        folder.setParentId(parentId);
        folder.setRoot(parentId == null);
        LocalDateTime now = LocalDateTime.now();
        folder.setAddDate(now);
        folder.setLastModified(now);
        int id = folderDAO.insert(folder);
        folder.setId(id);
        return id;
    }

    /**
     * 重命名文件夹，更新名字并刷新最后修改与更新时间。
     */
    public void renameFolder(int folderId, String newName) {
        requirePositive(folderId, "folderId");
        requireNonBlank(newName, "newName");

        Folder folder = folderDAO.queryById(folderId);
        if (folder == null) {
            throw new IllegalArgumentException("Folder does not exist: " + folderId);
        }
        if (!Objects.equals(folder.getName(), newName)
                && folderDAO.existsByNameAndParent(newName, folder.getParentId())) {
            throw new IllegalArgumentException("Folder '" + newName + "' already exists under the same parent");
        }

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
        requirePositive(folderId, "folderId");
        Folder folder = folderDAO.queryById(folderId);
        if (folder == null) {
            throw new IllegalArgumentException("Folder does not exist: " + folderId);
        }
        if (newParentId != null) {
            requirePositive(newParentId, "newParentId");
            if (folderDAO.queryById(newParentId) == null) {
                throw new IllegalArgumentException("Target parent folder does not exist: " + newParentId);
            }
        }
        if (Objects.equals(newParentId, folderId)) {
            throw new IllegalArgumentException("Folder cannot be moved into itself");
        }
        if (isDescendant(folderId, newParentId)) {
            throw new IllegalArgumentException("circular folder move detected: target is one of this folder's descendants");
        }

        folder.setParentId(newParentId);
        folder.setRoot(newParentId == null);
        folder.setLastModified(LocalDateTime.now());
        folderDAO.update(folder);
    }

    /**
     * 删除空文件夹；若存在子文件夹或书签则拒绝删除。
     */
    public void deleteFolder(int folderId) {
        requirePositive(folderId, "folderId");
        Folder folder = folderDAO.queryById(folderId);
        if (folder == null) {
            throw new IllegalArgumentException("Folder does not exist: " + folderId);
        }
        if (folderDAO.countByParentId(folderId) > 0) {
            throw new IllegalStateException("Cannot delete folder because it contains child folders");
        }
        if (!bookmarkDAO.queryByFolderId(folderId).isEmpty()) {
            throw new IllegalStateException("Cannot delete folder because it contains bookmarks");
        }
        folderDAO.deleteById(folderId);
    }

    /**
     * 查询指定文件夹下的所有书签。
     */
    public List<Bookmark> getBookmarksByFolder(int folderId) {
        requirePositive(folderId, "folderId");
        return bookmarkDAO.queryByFolderId(folderId);
    }

    /** 复制 DAO 查询结果为内存树使用的节点对象。 */
    private Folder copyFolder(Folder source) {
        Folder node = new Folder();
        node.setId(source.getId());
        node.setName(source.getName());
        node.setParentId(source.getParentId());
        node.setRoot(source.isRoot());
        node.setChildren(new LinkedHashMap<>());
        node.setBookmarks(new ArrayList<>());
        node.setAddDate(source.getAddDate());
        node.setLastModified(source.getLastModified());
        node.setCreateTime(source.getCreateTime());
        node.setUpdateTime(source.getUpdateTime());
        return node;
    }

    /** 判断 targetId 是否是 ancestorId 的后代。 */
    private boolean isDescendant(int ancestorId, Integer targetId) {
        if (targetId == null || targetId == ancestorId) {
            return false;
        }

        Map<Integer, List<Integer>> childrenByParent = new LinkedHashMap<>();
        for (Folder folder : folderDAO.queryAll()) {
            Integer parentId = folder.getParentId();
            if (parentId == null) {
                continue;
            }
            childrenByParent.computeIfAbsent(parentId, key -> new ArrayList<>()).add(folder.getId());
        }

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
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Field '" + field + "' must not be null or empty");
        }
    }

    /** 校验整数为正数。 */
    private void requirePositive(int value, String field) {
        if (value <= 0) {
            throw new IllegalArgumentException("Field '" + field + "' must be a positive integer");
        }
    }
}
