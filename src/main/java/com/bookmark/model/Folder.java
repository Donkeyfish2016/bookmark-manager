package com.bookmark.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文件夹实体类
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Folder {
    /** 文件夹唯一标识，数据库自动生成 */
    private int id;
    /** 文件夹名称，非空 */
    private String name;
    /** 父文件夹 id；为 {@code null} 时表示根文件夹 */
    private Integer parentId;
    /** 是否为根文件夹（{@code true} 时 parent_id 为 {@code null}） */
    private boolean root;
    /** 子文件夹映射（仅用于内存树构建） */
    private Map<String, Folder> children = new LinkedHashMap<>();
    /** 下属书签列表（仅用于内存树构建） */
    private List<Bookmark> bookmarks = new ArrayList<>();
    /** 从浏览器书签 HTML 解析出的添加时间 */
    private LocalDateTime addDate;
    /** 从浏览器书签 HTML 解析出的最后修改时间 */
    private LocalDateTime lastModified;
    /** 记录创建时间（数据库自动填充） */
    private LocalDateTime createTime;
    /** 记录最后更新时间（数据库自动填充） */
    private LocalDateTime updateTime;
}
