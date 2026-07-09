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

# [1/6] 新增多条书签，并捕获自动生成的主键 id
# echo ""
# echo "[1/6] Adding bookmarks"
# ID1=$(run add --url "https://python.org"     --title \"Python Official Site\" --icon "py.png"  --category "Dev"    | sed -n 's/.*id=\([0-9]*\).*/\1/p')
# ID2=$(run add --url "https://docs.python.org" --title \"Python Docs\"          --icon "doc.png" --category "Dev"    | sed -n 's/.*id=\([0-9]*\).*/\1/p')
# ID3=$(run add --url "https://github.com"      --title \"GitHub\"               --icon "gh.png"  --category "Social" | sed -n 's/.*id=\([0-9]*\).*/\1/p')
# echo "Captured ids: ID1=$ID1 ID2=$ID2 ID3=$ID3"

# # [2/6] 列出全部书签
# echo ""
# echo "[2/6] Listing all bookmarks"
# run list

# # [3/6] 按指定分类列出书签
# echo ""
# echo "[3/6] Listing bookmarks in category 'Dev'"
# run list --category Dev

# # [4/6] 关键字搜索（'python' 同时命中 title 与 url 字段）
# echo ""
# echo "[4/6] Searching by keyword 'python' (matches title and url)"
# run search --keyword python

# # [5/6] 更新多条记录（分别更新 title 与 category，未提供字段保持不变）
# echo ""
# echo "[5/6] Updating entries (id=$ID1 title, id=$ID2 category)"
# run update --id "$ID1" --title \"Python Official updated\"
# run update --id "$ID2" --category \"Dev/Python\"

# # [6/6] 删除指定记录
# echo ""
# echo "[6/6] Deleting entry id=$ID3"
# run delete --id "$ID3"

# # [1/2] 导入命令测试：从示例 HTML 文件导入书签
# echo ""
# echo "[1/2] 导入命令测试"
# run import --file src/main/java/com/bookmark/html/example.html

# # [2/2] 导出命令测试：将全部书签导出到指定 HTML 文件
# echo ""
# echo "[2/2] 导出命令测试"
# run export --output cli_export_test.html

# [1/4] 文件夹新增命令测试：创建与 in.sql 一致的文件夹结构（a 根 -> b -> c，a -> d）
echo ""
echo "[1/4] 文件夹新增命令测试"
A_ID=$(run folder add --name a --root | sed -n 's/.*ID: \([0-9]*\).*/\1/p')
B_ID=$(run folder add --name b --parent-id "$A_ID" | sed -n 's/.*ID: \([0-9]*\).*/\1/p')
C_ID=$(run folder add --name c --parent-id "$B_ID" | sed -n 's/.*ID: \([0-9]*\).*/\1/p')
D_ID=$(run folder add --name d --parent-id "$A_ID" | sed -n 's/.*ID: \([0-9]*\).*/\1/p')
echo "Created folder ids: A=$A_ID B=$B_ID C=$C_ID D=$D_ID"

# [1/3] 文件夹树命令测试：以 ASCII 树状结构展示文件夹层级
echo ""
echo "[1/3] 文件夹树命令测试"
run folder tree

# [2/3] 文件夹列表命令测试：表格展示根文件夹（收藏夹栏）的直接子文件夹
echo ""
echo "[2/3] 文件夹列表命令测试"
run folder list

# [3/3] 文件夹详情命令测试：展示指定 ID 文件夹的详细元数据
echo ""
echo "[3/3] 文件夹详情命令测试"
run folder info --id "$A_ID"

# [2/4] 文件夹重命名命令测试：将 c 重命名为 c2
echo ""
echo "[2/4] 文件夹重命名命令测试"
run folder rename --id "$C_ID" --name c2
# 重新运行查询命令验证重命名结果
echo "重新运行查询命令验证："
run folder tree

# [3/4] 文件夹移动命令测试：将 d 移动到 b 之下
echo ""
echo "[3/4] 文件夹移动命令测试"
run folder move --id "$D_ID" --parent-id "$B_ID"
# 重新运行查询命令验证移动结果
echo "重新运行查询命令验证："
run folder tree

# [4/4] 文件夹删除命令测试：删除 d
echo ""
echo "[4/4] 文件夹删除命令测试"
run folder delete --id "$D_ID"
# 重新运行查询命令验证删除结果
echo "重新运行查询命令验证："
run folder tree

echo ""
echo "==> All CRUD tests completed."
