package com.bookmark.model;

import lombok.AllArgsConstructor;
import lombok.Data;

/** 
 * 批量插入结果：成功数与因约束冲突被跳过的数量。 
 * 
*/
@Data
@AllArgsConstructor
public class BatchResult {
    public final int success;
    public final int failures;
}
