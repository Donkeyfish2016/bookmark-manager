package com.bookmark.model;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 导入结果：汇总一次 HTML 导入所写入的书签与文件夹数量。
 */
@Data
@AllArgsConstructor
public class ImportResult {
    /** 成功导入的书签总数 */
    public final int bookmarks;
    /** 成功导入的文件夹总数 */
    public final int folders;
}
