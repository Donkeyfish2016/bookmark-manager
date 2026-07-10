#!/usr/bin/env bash
#
# command_test.sh —— 通过 CLI 对书签管理工具进行端到端测试。
# 运行方式：
#   D:\Git\bin\bash.exe src/test/scripts/command_test.sh
#

JAR="bookmark-manager-1.0-SNAPSHOT.jar"
DB="bookmarkmgr.db"

# ---------- 工具函数 ----------

clean_db() {
    rm -f "$DB"
}

# 运行 JAR 并合并 stdout+stderr
run() {
    java -jar "$JAR" "$@" 2>&1
}

# 从 add 输出提取书签 ID
extract_bookmark_id() {
    grep -oP 'id=\K[0-9]+'
}

# 从 folder add 输出提取文件夹 ID
extract_folder_id() {
    grep -oP 'ID: \K[0-9]+'
}

# 将 GBK 输出转换为 UTF-8
gbk_to_utf8() {
    iconv -f GBK -t UTF-8 2>/dev/null
}

# 过滤 SQLite 警告行
filter_warnings() {
    grep -v '^WARNING:'
}

# 在 GBK 输出中查找字符串（转换后查找）
assert_contains() {
    local output="$1"
    local expected="$2"
    local name="$3"
    local converted
    converted=$(echo "$output" | gbk_to_utf8 | filter_warnings)
    if echo "$converted" | grep -q "$expected"; then
        echo "[PASS] $name"
        PASS=$((PASS + 1))
    else
        echo "[FAIL] $name: expected '$expected' in output"
        echo "  Output: $output"
        FAIL=$((FAIL + 1))
    fi
}

# 在纯 ASCII 输出中查找字符串（无需转换）
assert_contains_ascii() {
    local output="$1"
    local expected="$2"
    local name="$3"
    local filtered
    filtered=$(echo "$output" | filter_warnings)
    if echo "$filtered" | grep -q "$expected"; then
        echo "[PASS] $name"
        PASS=$((PASS + 1))
    else
        echo "[FAIL] $name: expected '$expected' in output"
        echo "  Output: $output"
        FAIL=$((FAIL + 1))
    fi
}

assert_not_contains() {
    local output="$1"
    local unexpected="$2"
    local name="$3"
    local converted
    converted=$(echo "$output" | gbk_to_utf8 | filter_warnings)
    if echo "$converted" | grep -q "$unexpected"; then
        echo "[FAIL] $name: did not expect '$unexpected' in output"
        echo "  Output: $output"
        FAIL=$((FAIL + 1))
    else
        echo "[PASS] $name"
        PASS=$((PASS + 1))
    fi
}

assert_exit_code() {
    local expected="$1"
    local actual="$2"
    local name="$3"
    if [ "$actual" -eq "$expected" ]; then
        echo "[PASS] $name"
        PASS=$((PASS + 1))
    else
        echo "[FAIL] $name: expected exit code $expected, got $actual"
        FAIL=$((FAIL + 1))
    fi
}

assert_exit_not_zero() {
    local actual="$1"
    local name="$2"
    if [ "$actual" -ne 0 ]; then
        echo "[PASS] $name"
        PASS=$((PASS + 1))
    else
        echo "[FAIL] $name: expected non-zero exit code, got 0"
        FAIL=$((FAIL + 1))
    fi
}

PASS=0
FAIL=0

# ============================================================
# 4.1 Bookmark CRUD Tests
# ============================================================
echo "=========================================="
echo "  Bookmark CRUD Tests"
echo "=========================================="
clean_db

# [1/42] TC-BM-01 AddBookmarkSuccess test: add a new bookmark and verify it appears in list
OUTPUT=$(run add -u "https://example.com" -t "Example" -c "default")
assert_exit_code 0 $? "TC-BM-01 exit code"
BM_ID=$(echo "$OUTPUT" | extract_bookmark_id)
LIST_OUT=$(run list)
assert_contains "$LIST_OUT" "Example" "TC-BM-01 list contains title"

# [2/42] TC-BM-02 AddBookmarkWithIcon test: add bookmark with icon and verify list
OUTPUT=$(run add -u "https://test.com" -t "Test" -i "icon.png" -c "default")
assert_exit_code 0 $? "TC-BM-02 exit code"
LIST_OUT=$(run list)
assert_contains "$LIST_OUT" "Test" "TC-BM-02 list contains title"

# [3/42] TC-BM-03 AddBookmarkMissingUrl test: add without URL should fail
OUTPUT=$(run add -t "NoURL" -c "default")
assert_exit_not_zero $? "TC-BM-03 exit code"
assert_contains_ascii "$OUTPUT" "Missing required option" "TC-BM-03 error message"

# [4/42] TC-BM-04 AddBookmarkMissingTitle test: add without title should fail
OUTPUT=$(run add -u "https://notitle.com" -c "default")
assert_exit_not_zero $? "TC-BM-04 exit code"
assert_contains_ascii "$OUTPUT" "Missing required option" "TC-BM-04 error message"

# [5/42] TC-BM-05 AddBookmarkDuplicateCategory test: add two bookmarks to same category
OUTPUT1=$(run add -u "https://dup1.com" -t "Dup1" -c "cat1")
BM_ID1=$(echo "$OUTPUT1" | extract_bookmark_id)
OUTPUT2=$(run add -u "https://dup2.com" -t "Dup2" -c "cat1")
assert_exit_code 0 $? "TC-BM-05 second add exit code"
LIST_OUT=$(run list -c "cat1")
COUNT=$(echo "$LIST_OUT" | filter_warnings | grep -c '^{')
if [ "$COUNT" -ge 2 ]; then
    echo "[PASS] TC-BM-05 category shows 2+ items"
    PASS=$((PASS + 1))
else
    echo "[FAIL] TC-BM-05: expected at least 2 items in category, got $COUNT"
    FAIL=$((FAIL + 1))
fi

# [6/42] TC-BM-06 ListAllBookmarks test: list all bookmarks shows JSON with id/url/title
LIST_OUT=$(run list)
assert_contains "$LIST_OUT" '"id"' "TC-BM-06 JSON id field"
assert_contains "$LIST_OUT" '"url"' "TC-BM-06 JSON url field"
assert_contains "$LIST_OUT" '"title"' "TC-BM-06 JSON title field"

# [7/42] TC-BM-07 ListByCategory test: list filtered by category
LIST_OUT=$(run list -c "default")
assert_contains "$LIST_OUT" '"category":"default"' "TC-BM-07 category filter"

# [8/42] TC-BM-08 ListPagination test: pagination limits results
LIST_OUT=$(run list -p 1 -s 5)
COUNT=$(echo "$LIST_OUT" | filter_warnings | grep -c '^{')
if [ "$COUNT" -le 5 ]; then
    echo "[PASS] TC-BM-08 pagination returns at most 5 items"
    PASS=$((PASS + 1))
else
    echo "[FAIL] TC-BM-08: expected at most 5 items, got $COUNT"
    FAIL=$((FAIL + 1))
fi

# [9/42] TC-BM-09 SearchByUrl test: search by URL keyword
SEARCH_OUT=$(run search -k "example")
assert_contains "$SEARCH_OUT" "example.com" "TC-BM-09 search by URL"

# [10/42] TC-BM-10 SearchByTitle test: search by title keyword
SEARCH_OUT=$(run search -k "Test")
assert_contains "$SEARCH_OUT" '"title":"Test"' "TC-BM-10 search by title"

# [11/42] TC-BM-11 SearchNoResults test: search with no matches
SEARCH_OUT=$(run search -k "nonexistent12345")
assert_contains "$SEARCH_OUT" "No bookmarks found for keyword" "TC-BM-11 no results message"

# 获取一个用于 update/delete 测试的书签 ID
LIST_OUT=$(run list)
BM_ID=$(echo "$LIST_OUT" | grep -oP '"id":\K[0-9]+' | head -n1)

# [12/42] TC-BM-12 UpdateBookmarkUrl test: update URL and verify
OUTPUT=$(run update -i "$BM_ID" -u "https://updated.com" --icon "icon.png")
assert_exit_code 0 $? "TC-BM-12 exit code"
LIST_OUT=$(run list)
assert_contains "$LIST_OUT" "https://updated.com" "TC-BM-12 updated URL"

# [13/42] TC-BM-13 UpdateBookmarkTitle test: update title and verify
OUTPUT=$(run update -i "$BM_ID" -t "Updated Title" --icon "icon.png")
assert_exit_code 0 $? "TC-BM-13 exit code"
LIST_OUT=$(run list)
assert_contains "$LIST_OUT" "Updated Title" "TC-BM-13 updated title"

# [14/42] TC-BM-14 UpdateBookmarkCategory test: update category and verify
OUTPUT=$(run update -i "$BM_ID" -c "newcat" --icon "icon.png")
assert_exit_code 0 $? "TC-BM-14 exit code"
LIST_OUT=$(run list -c "newcat")
assert_contains "$LIST_OUT" "Updated Title" "TC-BM-14 in new category"

# [15/42] TC-BM-15 UpdatePartialFields test: update title only, url unchanged
# 先添加一个有 icon 的书签用于部分更新测试
OUTPUT=$(run add -u "https://partial.com" -t "Partial" -i "partial.png" -c "default")
BM_PARTIAL_ID=$(echo "$OUTPUT" | extract_bookmark_id)
OUTPUT=$(run update -i "$BM_PARTIAL_ID" -t "OnlyTitle" --icon "partial.png")
assert_exit_code 0 $? "TC-BM-15 exit code"
LIST_OUT=$(run list)
assert_contains "$LIST_OUT" "https://partial.com" "TC-BM-15 original URL preserved"
assert_contains "$LIST_OUT" "OnlyTitle" "TC-BM-15 title updated"

# [16/42] TC-BM-16 UpdateNonExistentId test: update non-existent ID should fail
OUTPUT=$(run update -i 99999 -u "x" -t "y" --icon "z")
assert_exit_not_zero $? "TC-BM-16 exit code"
assert_contains "$OUTPUT" "未找到 ID=99999" "TC-BM-16 error message"

# [17/42] TC-BM-17 DeleteBookmark test: delete bookmark and verify removal
LIST_OUT=$(run list)
BM_DELETE_ID=$(echo "$LIST_OUT" | grep -oP '"id":\K[0-9]+' | head -n1)
OUTPUT=$(run delete -i "$BM_DELETE_ID")
assert_exit_code 0 $? "TC-BM-17 exit code"
assert_contains_ascii "$OUTPUT" "success" "TC-BM-17 success message"
LIST_OUT=$(run list)
assert_not_contains "$LIST_OUT" "\"id\":$BM_DELETE_ID" "TC-BM-17 removed from list"

# [18/42] TC-BM-18 DeleteNonExistentId test: delete non-existent ID should fail
OUTPUT=$(run delete -i 99999)
assert_exit_code 1 $? "TC-BM-18 exit code"
assert_contains "$OUTPUT" "no bookmark found" "TC-BM-18 error message"

echo ""
echo "--- Bookmark CRUD tests completed ---"
echo ""

# ============================================================
# 4.2 Folder Management Tests
# ============================================================
echo "=========================================="
echo "  Folder Management Tests"
echo "=========================================="
clean_db

# 先添加一个书签以便测试文件夹关联
run add -u "https://folder-test.com" -t "FolderTest" -c "default" >/dev/null

# [19/42] TC-FD-01 AddFolderSuccess test: create a new folder
OUTPUT=$(run folder add -n "TestFolder")
assert_exit_code 0 $? "TC-FD-01 exit code"
FD_TEST_ID=$(echo "$OUTPUT" | extract_folder_id)
FOLDER_LIST=$(run folder list)
assert_contains "$FOLDER_LIST" "TestFolder" "TC-FD-01 list shows folder"

# [20/42] TC-FD-02 AddFolderUnderParent test: create child folder under specific parent
OUTPUT=$(run folder add -n "ChildFolder" -p "$FD_TEST_ID")
assert_exit_code 0 $? "TC-FD-02 exit code"
FD_CHILD_ID=$(echo "$OUTPUT" | extract_folder_id)
FOLDER_LIST=$(run folder list -p "$FD_TEST_ID")
assert_contains "$FOLDER_LIST" "ChildFolder" "TC-FD-02 child in parent list"

# [21/42] TC-FD-03 AddFolderAsRoot test: create folder as root
OUTPUT=$(run folder add -n "RootFolder" -r)
assert_exit_code 0 $? "TC-FD-03 exit code"
FD_ROOT_ID=$(echo "$OUTPUT" | extract_folder_id)
# 由于 folder tree 只展示第一个根文件夹，通过 folder info 验证根文件夹属性
FOLDER_INFO=$(run folder info -i "$FD_ROOT_ID")
assert_contains "$FOLDER_INFO" "Is Root:       true" "TC-FD-03 verified as root"

# [22/42] TC-FD-04 AddFolderDuplicateName test: duplicate name under same parent fails
OUTPUT=$(run folder add -n "TestFolder")
assert_exit_not_zero $? "TC-FD-04 exit code"
assert_contains "$OUTPUT" "already exists" "TC-FD-04 duplicate error"

# [23/42] TC-FD-05 FolderListSubfolders test: list subfolders in table
FOLDER_LIST=$(run folder list -p "$FD_TEST_ID")
assert_contains "$FOLDER_LIST" "ChildFolder" "TC-FD-05 child in table"

# [24/42] TC-FD-06 FolderTree test: display folder hierarchy as ASCII tree
TREE_OUT=$(run folder tree)
assert_contains "$TREE_OUT" "default" "TC-FD-06 tree shows root"
assert_contains "$TREE_OUT" "TestFolder" "TC-FD-06 tree shows folder"

# [25/42] TC-FD-07 FolderInfo test: show detailed metadata
FOLDER_INFO=$(run folder info -i "$FD_TEST_ID")
assert_exit_code 0 $? "TC-FD-07 exit code"
assert_contains "$FOLDER_INFO" "TestFolder" "TC-FD-07 info shows name"
assert_contains "$FOLDER_INFO" "ID:" "TC-FD-07 info shows ID label"
assert_contains "$FOLDER_INFO" "Bookmarks:" "TC-FD-07 info shows bookmark count"

# [26/42] TC-FD-08 FolderInfoNonExistent test: info on non-existent ID fails
OUTPUT=$(run folder info -i 99999)
assert_exit_code 1 $? "TC-FD-08 exit code"
assert_contains "$OUTPUT" "未找到 ID=99999" "TC-FD-08 not found message"

# [27/42] TC-FD-09 RenameFolder test: rename folder and verify
OUTPUT=$(run folder rename -i "$FD_TEST_ID" -n "NewName")
assert_exit_code 0 $? "TC-FD-09 exit code"
FOLDER_INFO=$(run folder info -i "$FD_TEST_ID")
assert_contains "$FOLDER_INFO" "NewName" "TC-FD-09 renamed in info"

# [28/42] TC-FD-10 RenameToDuplicate test: rename to existing name fails
# 先创建另一个文件夹用于目标名称
OUTPUT=$(run folder add -n "TargetName")
FD_TARGET_ID=$(echo "$OUTPUT" | extract_folder_id)
OUTPUT=$(run folder rename -i "$FD_TEST_ID" -n "TargetName")
assert_exit_not_zero $? "TC-FD-10 exit code"
assert_contains "$OUTPUT" "already exists" "TC-FD-10 duplicate rename error"

# [29/42] TC-FD-11 RenameNonExistent test: rename non-existent folder fails
OUTPUT=$(run folder rename -i 99999 -n "Name")
assert_exit_not_zero $? "TC-FD-11 exit code"

# [30/42] TC-FD-12 MoveFolder test: move folder to new parent and verify
OUTPUT=$(run folder add -n "NewParentFolder")
FD_NEW_PARENT_ID=$(echo "$OUTPUT" | extract_folder_id)
OUTPUT=$(run folder move -i "$FD_TEST_ID" -p "$FD_NEW_PARENT_ID")
assert_exit_code 0 $? "TC-FD-12 exit code"
FOLDER_LIST=$(run folder list -p "$FD_NEW_PARENT_ID")
assert_contains "$FOLDER_LIST" "NewName" "TC-FD-12 in new parent"

# [31/42] TC-FD-13 MoveIntoSelf test: move folder into itself fails
OUTPUT=$(run folder move -i "$FD_TEST_ID" -p "$FD_TEST_ID")
assert_exit_not_zero $? "TC-FD-13 exit code"
assert_contains "$OUTPUT" "cannot be moved into itself" "TC-FD-13 self move error"

# [32/42] TC-FD-14 MoveIntoDescendant test: move parent into child fails
# 创建 Parent -> Child 层级，然后把 Parent 移到 Child 下
OUTPUT=$(run folder add -n "MoveParent")
FD_MOVE_PARENT=$(echo "$OUTPUT" | extract_folder_id)
OUTPUT=$(run folder add -n "MoveChild" -p "$FD_MOVE_PARENT")
FD_MOVE_CHILD=$(echo "$OUTPUT" | extract_folder_id)
OUTPUT=$(run folder move -i "$FD_MOVE_PARENT" -p "$FD_MOVE_CHILD")
assert_exit_not_zero $? "TC-FD-14 exit code"
assert_contains "$OUTPUT" "circular" "TC-FD-14 circular move error"

# [33/42] TC-FD-15 MoveToNonExistentParent test: move to non-existent parent fails
OUTPUT=$(run folder move -i "$FD_TEST_ID" -p 99999)
assert_exit_not_zero $? "TC-FD-15 exit code"
assert_contains "$OUTPUT" "does not exist" "TC-FD-15 parent not found"

# [34/42] TC-FD-16 DeleteEmptyFolder test: delete empty folder succeeds
OUTPUT=$(run folder add -n "EmptyFolder")
FD_EMPTY_ID=$(echo "$OUTPUT" | extract_folder_id)
OUTPUT=$(run folder delete -i "$FD_EMPTY_ID")
assert_exit_code 0 $? "TC-FD-16 exit code"
assert_contains "$OUTPUT" "Folder deleted:" "TC-FD-16 delete success"
FOLDER_LIST=$(run folder list)
assert_not_contains "$FOLDER_LIST" "EmptyFolder" "TC-FD-16 removed from list"

# [35/42] TC-FD-17 DeleteNonEmptyFolder test: delete folder with children fails
OUTPUT=$(run folder add -n "ParentFolder")
FD_PARENT_FOR_DEL=$(echo "$OUTPUT" | extract_folder_id)
run folder add -n "ChildFolderDel" -p "$FD_PARENT_FOR_DEL" >/dev/null
OUTPUT=$(run folder delete -i "$FD_PARENT_FOR_DEL")
assert_exit_not_zero $? "TC-FD-17 exit code"
assert_contains "$OUTPUT" "child" "TC-FD-17 non-empty error"

# [36/42] TC-FD-18 DeleteNonExistentFolder test: delete non-existent folder fails
OUTPUT=$(run folder delete -i 99999)
assert_exit_not_zero $? "TC-FD-18 exit code"

echo ""
echo "--- Folder Management tests completed ---"
echo ""

# ============================================================
# 4.3 Import/Export Tests
# ============================================================
echo "=========================================="
echo "  Import/Export Tests"
echo "=========================================="
clean_db

# [37/42] TC-IE-01 ExportBookmarks test: export bookmarks to HTML file
run add -u "https://export-test.com" -t "ExportTest" -c "default" >/dev/null
OUTPUT=$(run export -o test_export.html)
assert_exit_code 0 $? "TC-IE-01 exit code"
assert_contains "$OUTPUT" "test_export.html" "TC-IE-01 export filename"
if [ -f "test_export.html" ]; then
    echo "[PASS] TC-IE-01 file created"
    PASS=$((PASS + 1))
else
    echo "[FAIL] TC-IE-01: file not created"
    FAIL=$((FAIL + 1))
fi

# [38/42] TC-IE-02 ImportBookmarks test: import from HTML and verify
# 使用已导出的文件作为导入源
cp test_export.html cli_export_test.html
OUTPUT=$(run import -f cli_export_test.html)
assert_exit_code 0 $? "TC-IE-02 exit code"
assert_contains "$OUTPUT" "导入完成" "TC-IE-02 import complete message"
LIST_OUT=$(run list)
assert_contains "$LIST_OUT" "ExportTest" "TC-IE-02 imported bookmark in list"

echo ""
echo "--- Import/Export tests completed ---"
echo ""

# ============================================================
# 4.4 Edge Case Tests
# ============================================================
echo "=========================================="
echo "  Edge Case Tests"
echo "=========================================="
clean_db

# [39/42] TC-EC-01 BookmarkSpecialChars test: add bookmark with special characters
SPECIAL_URL="https://example.com/path?a=1&b=2#frag"
SPECIAL_TITLE="Title with & < > \" ' + ="
OUTPUT=$(run add -u "$SPECIAL_URL" -t "$SPECIAL_TITLE" -c "default")
assert_exit_code 0 $? "TC-EC-01 exit code"
LIST_OUT=$(run list)
assert_contains "$LIST_OUT" "path?a=1&b=2#frag" "TC-EC-01 URL preserved"
assert_contains "$LIST_OUT" "+ =" "TC-EC-01 title preserved"

# [40/42] TC-EC-02 BookmarkChineseChars test: add bookmark with Chinese title
CHINESE_TITLE="中文书签测试"
OUTPUT=$(run add -u "https://chinese-test.com" -t "$CHINESE_TITLE" -c "default")
assert_exit_code 0 $? "TC-EC-02 exit code"
LIST_OUT=$(run list)
assert_contains "$LIST_OUT" "$CHINESE_TITLE" "TC-EC-02 Chinese title in list"

# [41/42] TC-EC-03 FolderChineseChars test: create folder with Chinese name
CHINESE_FOLDER="中文文件夹"
OUTPUT=$(run folder add -n "$CHINESE_FOLDER")
assert_exit_code 0 $? "TC-EC-03 exit code"
FOLDER_LIST=$(run folder list)
assert_contains "$FOLDER_LIST" "$CHINESE_FOLDER" "TC-EC-03 Chinese folder in list"

# [42/42] TC-EC-04 EmptyListResult test: list with no bookmarks shows empty message
clean_db
LIST_OUT=$(run list)
assert_contains "$LIST_OUT" "No bookmarks found" "TC-EC-04 empty list message"

echo ""
echo "--- Edge Case tests completed ---"
echo ""

# ============================================================
# Summary
# ============================================================
echo "=========================================="
echo "  Test Summary"
echo "=========================================="
TOTAL=$((PASS + FAIL))
echo "Passed: $PASS"
echo "Failed: $FAIL"
echo "Total:  $TOTAL"
echo ""
if [ "$FAIL" -eq 0 ]; then
    echo "==> All $TOTAL tests PASSED."
else
    echo "==> $FAIL test(s) FAILED."
fi
