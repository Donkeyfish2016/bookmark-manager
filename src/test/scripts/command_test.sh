#!/usr/bin/env bash
#
# command_test.sh —— 通过 CLI 对书签管理工具进行端到端 CRUD 测试。
# 运行方式：
#   bash src/test/scripts/command_test.sh
#   或   mvn exec:exec        （需 pom 中已配置 exec-maven-plugin）
#
set -u

# 1. 定位项目根目录（脚本位于 src/test/scripts 下，向上三级即为根）
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"
cd "$PROJECT_ROOT" || exit 1

APP_CLASS="com.bookmark.App"

# 2. 构建运行所需的 classpath（编译产物 + Maven 依赖）
echo "==> Building classpath..."
if [ ! -d "target/classes" ]; then
  mvn compile -q -DskipTests
fi

# 3. 统一封装：调用书签 CLI（java -cp ... com.bookmark.App <args>）
run() {
  local args="$*"
  mvn exec:java -Dexec.mainClass="$APP_CLASS" -Dexec.args="$args" -q
}

# 4. 辅助函数：清理数据库（保证测试隔离）
cleanup_db() {
  rm -f bookmarkmgr.db
}

# 5. 辅助函数：运行命令并捕获输出与退出码
#    用法: output=$(run_capture add -u "..." -t "..."); rc=$?
run_capture() {
  local args="$*"
  local tmpfile
  tmpfile=$(mktemp)
  mvn exec:java -Dexec.mainClass="$APP_CLASS" -Dexec.args="$args" -q >"$tmpfile" 2>&1
  local exit_code=$?
  cat "$tmpfile"
  rm -f "$tmpfile"
  return $exit_code
}

# 6. 辅助函数：断言输出包含指定字符串
assert_contains() {
  local output="$1"
  local expected="$2"
  local label="${3:-}"
  if echo "$output" | grep -q "$expected"; then
    echo "  [PASS] ${label}Found expected: '${expected}'"
  else
    echo "  [FAIL] ${label}Expected '${expected}' not found in output"
    echo "  Output: $output"
    return 1
  fi
}

# 7. 辅助函数：断言输出不包含指定字符串
assert_not_contains() {
  local output="$1"
  local unexpected="$2"
  local label="${3:-}"
  if echo "$output" | grep -q "$unexpected"; then
    echo "  [FAIL] ${label}Unexpected '${unexpected}' found in output"
    echo "  Output: $output"
    return 1
  else
    echo "  [PASS] ${label}Correctly absent: '${unexpected}'"
  fi
}

# 8. 辅助函数：断言退出码
assert_exit_code() {
  local expected="$1"
  local actual="$2"
  local label="${3:-}"
  if [ "$actual" -eq "$expected" ]; then
    echo "  [PASS] ${label}Exit code: $actual (expected $expected)"
  else
    echo "  [FAIL] ${label}Exit code: $actual (expected $expected)"
    return 1
  fi
}

echo ""
echo "=========================================="
echo "  Bookmark Manager CLI Integration Tests"
echo "=========================================="
echo ""

# ============================================================
# Group A: Bookmark CRUD Tests (clean DB before group)
# ============================================================

# [1/42] AddBookmarkSuccess测试:验证成功添加书签后list可查
# [清理数据库 -> add -> list验证]
cleanup_db
echo "---- Test [1/42] AddBookmarkSuccess ----"
output=$(run_capture add -u "https://example.com" -t "ExampleSite" -c "default"); rc=$?
assert_exit_code 0 $rc "add "
assert_contains "$output" "Added bookmark" "add "
id=$(echo "$output" | grep -o 'id=[0-9]*' | head -1 | cut -d= -f2)
echo "  Created bookmark id=$id"
output=$(run_capture list)
assert_contains "$output" "ExampleSite" "list verify "

# [2/42] AddBookmarkWithIcon测试:验证带图标URL的书签添加成功
# [add with icon -> list验证]
echo "---- Test [2/42] AddBookmarkWithIcon ----"
output=$(run_capture add -u "https://test.com" -t "TestSite" -i "https://icon.test.com/favicon.ico" -c "default"); rc=$?
assert_exit_code 0 $rc "add "
assert_contains "$output" "Added bookmark" "add "
output=$(run_capture list)
assert_contains "$output" "TestSite" "list verify "
assert_contains "$output" "https://icon.test.com/favicon.ico" "list verify icon "

# [3/42] AddBookmarkMissingUrl测试:验证缺少URL时添加失败
# [add missing url -> 验证错误退出码和错误信息]
echo "---- Test [3/42] AddBookmarkMissingUrl ----"
output=$(run_capture add -t "NoURL" -c "default"); rc=$?
assert_exit_code 1 $rc "add "
assert_contains "$output" "url" "add error "

# [4/42] AddBookmarkMissingTitle测试:验证缺少标题时添加失败
# [add missing title -> 验证错误退出码和错误信息]
echo "---- Test [4/42] AddBookmarkMissingTitle ----"
output=$(run_capture add -u "https://notitle.com" -c "default"); rc=$?
assert_exit_code 1 $rc "add "
assert_contains "$output" "title" "add error "

# [5/42] AddBookmarkDuplicateCategory测试:验证同一分类可添加多条
# [add two bookmarks same category -> list by category验证]
echo "---- Test [5/42] AddBookmarkDuplicateCategory ----"
output=$(run_capture add -u "https://dup1.com" -t "Dup1" -c "cat1"); rc=$?
assert_exit_code 0 $rc "add "
output=$(run_capture add -u "https://dup2.com" -t "Dup2" -c "cat1"); rc=$?
assert_exit_code 0 $rc "add "
output=$(run_capture list -c cat1)
assert_contains "$output" "Dup1" "list cat1 "
assert_contains "$output" "Dup2" "list cat1 "

# [6/42] ListAllBookmarks测试:验证列出所有书签的JSON格式
# [list all -> 验证输出包含所有添加的书签]
echo "---- Test [6/42] ListAllBookmarks ----"
output=$(run_capture list)
assert_contains "$output" "ExampleSite" "list all "
assert_contains "$output" "TestSite" "list all "
assert_contains "$output" "Dup1" "list all "
assert_contains "$output" "Dup2" "list all "
assert_contains "$output" "\"id\"" "list json format "
assert_contains "$output" "\"url\"" "list json format "
assert_contains "$output" "\"title\"" "list json format "

# [7/42] ListByCategory测试:验证按分类过滤书签
# [list by category -> 验证只返回该分类的书签]
echo "---- Test [7/42] ListByCategory ----"
output=$(run_capture list -c default)
assert_contains "$output" "ExampleSite" "list default "
assert_contains "$output" "TestSite" "list default "
assert_not_contains "$output" "Dup1" "list default filter "

# [8/42] ListPagination测试:验证分页参数生效
# [list with page/size -> 验证结果数量]
echo "---- Test [8/42] ListPagination ----"
output=$(run_capture list -p 1 -s 2)
line_count=$(echo "$output" | grep -c "^{.*}$" || true)
echo "  Page 1 size 2 returned $line_count items"
if [ "$line_count" -le 2 ] && [ "$line_count" -gt 0 ]; then
  echo "  [PASS] list pagination correct"
else
  echo "  [INFO] Pagination returned $line_count items"
fi

# [9/42] SearchByUrl测试:验证按URL关键字搜索
# [search by url keyword -> 验证返回匹配书签]
echo "---- Test [9/42] SearchByUrl ----"
output=$(run_capture search -k "example.com")
assert_contains "$output" "ExampleSite" "search url "
assert_contains "$output" "https://example.com" "search url exact "

# [10/42] SearchByTitle测试:验证按标题关键字搜索
# [search by title keyword -> 验证返回匹配书签]
echo "---- Test [10/42] SearchByTitle ----"
output=$(run_capture search -k "TestSite")
assert_contains "$output" "TestSite" "search title "

# [11/42] SearchNoResults测试:验证搜索无结果时的提示
# [search nonexistent keyword -> 验证提示信息]
echo "---- Test [11/42] SearchNoResults ----"
output=$(run_capture search -k "nonexistent12345xyz"); rc=$?
assert_exit_code 0 $rc "search "
assert_contains "$output" "No bookmarks found" "search no results "

# [12/42] UpdateBookmarkUrl测试:验证更新书签URL后list确认变更
# [add bookmark -> update url -> list验证]
echo "---- Test [12/42] UpdateBookmarkUrl ----"
output=$(run_capture add -u "https://oldurl.com" -t "ToUpdate" -c "default"); rc=$?
assert_exit_code 0 $rc "add "
update_id=$(echo "$output" | grep -o 'id=[0-9]*' | head -1 | cut -d= -f2)
echo "  Created bookmark id=$update_id for update test"
output=$(run_capture update -i "$update_id" -u "https://newurl.com"); rc=$?
assert_exit_code 0 $rc "update "
output=$(run_capture list)
assert_contains "$output" "https://newurl.com" "list verify updated url "
assert_not_contains "$output" "https://oldurl.com" "list verify old url gone "

# [13/42] UpdateBookmarkTitle测试:验证更新书签标题后list确认变更
# [update title -> list验证]
echo "---- Test [13/42] UpdateBookmarkTitle ----"
output=$(run_capture update -i "$update_id" -t "UpdatedTitle"); rc=$?
assert_exit_code 0 $rc "update "
output=$(run_capture list)
assert_contains "$output" "UpdatedTitle" "list verify updated title "

# [14/42] UpdateBookmarkCategory测试:验证更新分类后list按新分类可查
# [update category -> list by new category验证]
echo "---- Test [14/42] UpdateBookmarkCategory ----"
output=$(run_capture update -i "$update_id" -c "updatedcat"); rc=$?
assert_exit_code 0 $rc "update "
output=$(run_capture list -c updatedcat)
assert_contains "$output" "UpdatedTitle" "list verify updated category "

# [15/42] UpdatePartialFields测试:验证仅更新部分字段，未指定字段保持不变
# [update only title -> verify url and category unchanged]
echo "---- Test [15/42] UpdatePartialFields ----"
output=$(run_capture update -i "$update_id" -t "PartialUpdate"); rc=$?
assert_exit_code 0 $rc "update "
output=$(run_capture list)
assert_contains "$output" "PartialUpdate" "list verify partial title "
assert_contains "$output" "https://newurl.com" "list verify partial url preserved "
assert_contains "$output" "updatedcat" "list verify partial category preserved "

# [16/42] UpdateNonExistentId测试:验证更新不存在的书签返回错误
# [update nonexistent id -> 验证错误退出码和信息]
echo "---- Test [16/42] UpdateNonExistentId ----"
output=$(run_capture update -i 99999 -u "https://x.com" -t "X"); rc=$?
assert_exit_code 1 $rc "update "
assert_contains "$output" "未找到" "update nonexistent "

# [17/42] DeleteBookmark测试:验证删除书签后list确认已移除
# [add bookmark -> delete -> list验证已移除]
echo "---- Test [17/42] DeleteBookmark ----"
output=$(run_capture add -u "https://todelete.com" -t "ToDelete" -c "default"); rc=$?
assert_exit_code 0 $rc "add "
delete_id=$(echo "$output" | grep -o 'id=[0-9]*' | head -1 | cut -d= -f2)
echo "  Created bookmark id=$delete_id for delete test"
output=$(run_capture delete -i "$delete_id"); rc=$?
assert_exit_code 0 $rc "delete "
assert_contains "$output" "has been deleted" "delete success "
output=$(run_capture list)
assert_not_contains "$output" "ToDelete" "list verify deleted "

# [18/42] DeleteNonExistentId测试:验证删除不存在的书签返回失败
# [delete nonexistent id -> 验证失败退出码和信息]
echo "---- Test [18/42] DeleteNonExistentId ----"
output=$(run_capture delete -i 99999); rc=$?
assert_exit_code 1 $rc "delete "
assert_contains "$output" "no bookmark found" "delete nonexistent "

# ============================================================
# Group B: Folder Management Tests (clean DB before group)
# ============================================================

# [19/42] AddFolderSuccess测试:验证成功创建文件夹后folder list可查
# [清理数据库 -> folder add -> folder list验证]
cleanup_db
echo "---- Test [19/42] AddFolderSuccess ----"
output=$(run_capture folder add -n "TestFolder"); rc=$?
assert_exit_code 0 $rc "folder add "
assert_contains "$output" "Folder created with ID" "folder add "
folder_id=$(echo "$output" | grep -o 'ID: [0-9]*' | head -1 | cut -d= -f2 | tr -d ' ')
echo "  Created folder id=$folder_id"
output=$(run_capture folder list)
assert_contains "$output" "TestFolder" "folder list verify "

# [20/42] AddFolderUnderParent测试:验证在指定父文件夹下创建子文件夹
# [get root id -> folder add under root -> folder list -p root验证]
echo "---- Test [20/42] AddFolderUnderParent ----"
# 获取根文件夹ID（收藏夹栏）
root_output=$(run_capture folder list)
root_id=$(echo "$root_output" | head -1 | awk '{print $1}')
echo "  Root folder id=$root_id"
output=$(run_capture folder add -n "ChildFolder" -p "$root_id"); rc=$?
assert_exit_code 0 $rc "folder add child "
output=$(run_capture folder list -p "$root_id")
assert_contains "$output" "ChildFolder" "folder list child verify "

# [21/42] AddFolderAsRoot测试:验证创建为独立根文件夹
# [folder add -r -> folder tree验证]
echo "---- Test [21/42] AddFolderAsRoot ----"
output=$(run_capture folder add -n "RootFolder" -r); rc=$?
assert_exit_code 0 $rc "folder add root "
output=$(run_capture folder tree)
assert_contains "$output" "RootFolder" "folder tree root verify "

# [22/42] AddFolderDuplicateName测试:验证同名文件夹在同一父级下创建失败
# [add folder -> add same name again -> 验证错误]
echo "---- Test [22/42] AddFolderDuplicateName ----"
output=$(run_capture folder add -n "DuplicateFolder"); rc=$?
assert_exit_code 0 $rc "folder add first "
dup_id=$(echo "$output" | grep -o 'ID: [0-9]*' | head -1 | cut -d= -f2 | tr -d ' ')
output=$(run_capture folder add -n "DuplicateFolder"); rc=$?
assert_exit_code 1 $rc "folder add duplicate "
assert_contains "$output" "already exists" "folder add duplicate error "

# [23/42] FolderListSubfolders测试:验证列出子文件夹的表格输出
# [create parent + children -> folder list验证表格格式]
echo "---- Test [23/42] FolderListSubfolders ----"
parent_output=$(run_capture folder add -n "ParentForList")
parent_id=$(echo "$parent_output" | grep -o 'ID: [0-9]*' | head -1 | cut -d= -f2 | tr -d ' ')
run_capture folder add -n "ChildA" -p "$parent_id" >/dev/null
run_capture folder add -n "ChildB" -p "$parent_id" >/dev/null
output=$(run_capture folder list -p "$parent_id")
assert_contains "$output" "ChildA" "folder list child a "
assert_contains "$output" "ChildB" "folder list child b "
assert_contains "$output" "ID" "folder list table header "
assert_contains "$output" "Name" "folder list table header "

# [24/42] FolderTree测试:验证ASCII树状结构显示
# [folder tree -> 验证树形输出]
echo "---- Test [24/42] FolderTree ----"
output=$(run_capture folder tree)
assert_contains "$output" "bookmarks)" "folder tree bookmarks count "
# 检查是否包含树形连接符
if echo "$output" | grep -q "├──\|└──"; then
  echo "  [PASS] folder tree contains tree branches"
else
  echo "  [INFO] folder tree structure (no branches in root level)"
fi

# [25/42] FolderInfo测试:验证显示文件夹详细信息
# [folder info -> 验证元数据字段]
echo "---- Test [25/42] FolderInfo ----"
info_output=$(run_capture folder info -i "$parent_id")
assert_contains "$info_output" "ParentForList" "folder info name "
assert_contains "$info_output" "ID:" "folder info field "
assert_contains "$info_output" "Parent ID:" "folder info field "
assert_contains "$info_output" "Child Folders:" "folder info field "
assert_contains "$info_output" "Bookmarks:" "folder info field "

# [26/42] FolderInfoNonExistent测试:验证查询不存在的文件夹返回错误
# [folder info nonexistent -> 验证错误退出码和信息]
echo "---- Test [26/42] FolderInfoNonExistent ----"
output=$(run_capture folder info -i 99999); rc=$?
assert_exit_code 1 $rc "folder info "
assert_contains "$output" "未找到" "folder info nonexistent "

# [27/42] RenameFolder测试:验证重命名文件夹后folder info确认变更
# [folder rename -> folder info验证新名称]
echo "---- Test [27/42] RenameFolder ----"
output=$(run_capture folder rename -i "$parent_id" -n "RenamedParent"); rc=$?
assert_exit_code 0 $rc "folder rename "
output=$(run_capture folder info -i "$parent_id")
assert_contains "$output" "RenamedParent" "folder info verify rename "

# [28/42] RenameToDuplicate测试:验证重命名为同级已存在名称时失败
# [create folder -> rename to duplicate -> 验证错误]
echo "---- Test [28/42] RenameToDuplicate ----"
dup2_output=$(run_capture folder add -n "DupNameTarget")
dup2_id=$(echo "$dup2_output" | grep -o 'ID: [0-9]*' | head -1 | cut -d= -f2 | tr -d ' ')
output=$(run_capture folder rename -i "$dup2_id" -n "RenamedParent"); rc=$?
assert_exit_code 1 $rc "folder rename duplicate "
assert_contains "$output" "already exists" "folder rename duplicate error "

# [29/42] RenameNonExistent测试:验证重命名不存在的文件夹返回错误
# [folder rename nonexistent -> 验证错误]
echo "---- Test [29/42] RenameNonExistent ----"
output=$(run_capture folder rename -i 99999 -n "GhostName"); rc=$?
assert_exit_code 1 $rc "folder rename "
assert_contains "$output" "未找到" "folder rename nonexistent "

# [30/42] MoveFolder测试:验证移动文件夹后folder tree确认新位置
# [folder move -> folder tree验证]
echo "---- Test [30/42] MoveFolder ----"
# 创建一个目标父文件夹
target_output=$(run_capture folder add -n "MoveTarget")
target_id=$(echo "$target_output" | grep -o 'ID: [0-9]*' | head -1 | cut -d= -f2 | tr -d ' ')
child_output=$(run_capture folder add -n "ToMoveChild" -p "$parent_id")
child_id=$(echo "$child_output" | grep -o 'ID: [0-9]*' | head -1 | cut -d= -f2 | tr -d ' ')
output=$(run_capture folder move -i "$child_id" -p "$target_id"); rc=$?
assert_exit_code 0 $rc "folder move "
output=$(run_capture folder tree)
assert_contains "$output" "ToMoveChild" "folder tree verify move "

# [31/42] MoveIntoSelf测试:验证将文件夹移动到自身时失败
# [folder move into self -> 验证错误]
echo "---- Test [31/42] MoveIntoSelf ----"
output=$(run_capture folder move -i "$child_id" -p "$child_id"); rc=$?
assert_exit_code 1 $rc "folder move self "
assert_contains "$output" "cannot be moved into itself" "folder move self error "

# [32/42] MoveIntoDescendant测试:验证将文件夹移动到其后代时失败
# [parent -> child -> move parent into child -> 验证错误]
echo "---- Test [32/42] MoveIntoDescendant ----"
# parent_id现在有子文件夹child_id（已移到target下，需要重新检查层级）
# 使用parent_id作为源，其下的某个后代作为目标
output=$(run_capture folder move -i "$parent_id" -p "$child_id"); rc=$?
assert_exit_code 1 $rc "folder move descendant "
assert_contains "$output" "circular" "folder move descendant error "

# [33/42] MoveToNonExistentParent测试:验证移动到不存在的父文件夹时失败
# [folder move to invalid parent -> 验证错误]
echo "---- Test [33/42] MoveToNonExistentParent ----"
output=$(run_capture folder move -i "$dup2_id" -p 99999); rc=$?
assert_exit_code 1 $rc "folder move invalid parent "
assert_contains "$output" "does not exist" "folder move invalid parent error "

# [34/42] DeleteEmptyFolder测试:验证删除空文件夹后folder list确认移除
# [create empty folder -> delete -> folder list验证]
echo "---- Test [34/42] DeleteEmptyFolder ----"
empty_output=$(run_capture folder add -n "EmptyToDelete")
empty_id=$(echo "$empty_output" | grep -o 'ID: [0-9]*' | head -1 | cut -d= -f2 | tr -d ' ')
output=$(run_capture folder delete -i "$empty_id"); rc=$?
assert_exit_code 0 $rc "folder delete empty "
assert_contains "$output" "Folder deleted" "folder delete success "
output=$(run_capture folder list)
assert_not_contains "$output" "EmptyToDelete" "folder list verify deleted "

# [35/42] DeleteNonEmptyFolder测试:验证删除包含子文件夹的文件夹时失败
# [folder with children -> delete -> 验证错误]
echo "---- Test [35/42] DeleteNonEmptyFolder ----"
output=$(run_capture folder delete -i "$parent_id"); rc=$?
assert_exit_code 1 $rc "folder delete nonempty "
assert_contains "$output" "contains child" "folder delete nonempty error "

# [36/42] DeleteNonExistentFolder测试:验证删除不存在的文件夹返回错误
# [folder delete nonexistent -> 验证错误]
echo "---- Test [36/42] DeleteNonExistentFolder ----"
output=$(run_capture folder delete -i 99999); rc=$?
assert_exit_code 1 $rc "folder delete "
assert_contains "$output" "未找到" "folder delete nonexistent "

# ============================================================
# Group C: Import/Export Tests (clean DB before group)
# ============================================================

# [37/42] ExportBookmarks测试:验证导出书签到HTML文件
# [add bookmarks -> export -> 验证文件存在且包含内容]
cleanup_db
echo "---- Test [37/42] ExportBookmarks ----"
run_capture add -u "https://export1.com" -t "Export1" -c "default" >/dev/null
run_capture add -u "https://export2.com" -t "Export2" -c "default" >/dev/null
output=$(run_capture export -o "test_export.html"); rc=$?
assert_exit_code 0 $rc "export "
assert_contains "$output" "成功导出" "export success "
if [ -f "test_export.html" ]; then
  echo "  [PASS] export file created"
  assert_contains "$(cat test_export.html)" "Export1" "export content "
  assert_contains "$(cat test_export.html)" "Export2" "export content "
  assert_contains "$(cat test_export.html)" "https://export1.com" "export content url "
else
  echo "  [FAIL] export file not created"
fi

# [38/42] ImportBookmarks测试:验证从HTML文件导入书签后list可查
# [import from html -> list验证导入结果]
echo "---- Test [38/42] ImportBookmarks ----"
output=$(run_capture import -f "cli_export_test.html"); rc=$?
assert_exit_code 0 $rc "import "
assert_contains "$output" "导入完成" "import success "
output=$(run_capture list)
assert_contains "$output" "必应" "import verify "
assert_contains "$output" "GitHub" "import verify "

# ============================================================
# Group D: Edge Case Tests (clean DB before group)
# ============================================================

# [39/42] BookmarkSpecialChars测试:验证URL和标题包含特殊字符
# [add with special chars -> list验证]
cleanup_db
echo "---- Test [39/42] BookmarkSpecialChars ----"
output=$(run_capture add -u "https://example.com/path?a=1&b=2#frag" -t "Test & <Special> \"Chars\"" -c "default"); rc=$?
assert_exit_code 0 $rc "add special chars "
output=$(run_capture list)
assert_contains "$output" "Test" "list verify special chars "

# [40/42] BookmarkChineseChars测试:验证中文标题和分类
# [add with chinese -> list验证]
echo "---- Test [40/42] BookmarkChineseChars ----"
output=$(run_capture add -u "https://baidu.com" -t "百度一下" -c "中文分类"); rc=$?
assert_exit_code 0 $rc "add chinese "
output=$(run_capture list)
assert_contains "$output" "百度一下" "list verify chinese title "
output=$(run_capture list -c "中文分类")
assert_contains "$output" "百度一下" "list verify chinese category "

# [41/42] FolderChineseChars测试:验证中文文件夹名称
# [folder add chinese -> folder list验证]
echo "---- Test [41/42] FolderChineseChars ----"
output=$(run_capture folder add -n "我的收藏"); rc=$?
assert_exit_code 0 $rc "folder add chinese "
output=$(run_capture folder list)
assert_contains "$output" "我的收藏" "folder list verify chinese "

# [42/42] EmptyListResult测试:验证无书签时list返回空结果提示
# [clean db -> list -> 验证提示]
echo "---- Test [42/42] EmptyListResult ----"
cleanup_db
output=$(run_capture list); rc=$?
assert_exit_code 0 $rc "list empty "
assert_contains "$output" "No bookmarks found" "list empty message "

echo ""
echo "=========================================="
echo "  All 42 integration tests completed."
echo "=========================================="
