package com.bookmark.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 书签实体类
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Bookmark {
    /** 书签唯一标识，数据库自动生成 */
    private Integer id;
    /** 书签 URL，非空 */
    private String url;
    /** 网页标题 */
    private String title;
    /** 网站图标 URL 或路径，可为空 */
    private String icon;
    /** 分类或文件夹名称，多层文件夹以 '/' 分隔，冗余字段 */
    private String category;
    /** 从浏览器书签 HTML 解析出的添加时间 */
    private LocalDateTime addDate;
    /** 创建时间 */
    private LocalDateTime createTime;
    /** 更新时间 */
    private LocalDateTime updateTime;
    /** 所在文件夹id */
    private Integer folderId;
}
