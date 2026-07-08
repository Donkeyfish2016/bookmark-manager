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
 * 
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Folder {
    private int id;
    private String name;
    private Integer parentId;
    private boolean root;
    private Map<String, Folder> children = new LinkedHashMap<>();
    private List<Bookmark> bookmarks = new ArrayList<>();
    private LocalDateTime addDate;
    private LocalDateTime lastModified;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
