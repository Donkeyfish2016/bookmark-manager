"""
git提交钩子，每次提交后运行记录目标信息
"""

import subprocess
import json
import sys
from datetime import datetime
import re

def run_git_cmd(cmd_args):
    """执行git命令，直接传递参数列表，不使用shell"""
    result = subprocess.run(
        ['git'] + cmd_args,
        capture_output=True,
        text=True,
        encoding='utf-8',
        errors='replace'
    )
    if result.returncode != 0:
        print(f"Git命令执行失败: git {' '.join(cmd_args)}\n{result.stderr}")
        sys.exit(1)
    return result.stdout.strip()

def get_latest_commit_info():
    """获取最新一次提交的hash，时间，diff，提示词即commit message"""
    # 获取最新commit hash (短hash，也可用长hash)
    commit_hash = run_git_cmd(["log", "-1", "--format=%h"])
    # 获取作者提交时间，格式 YYYY-MM-DD HH:MM:SS
    commit_time = run_git_cmd(["log", "-1", "--format=%ai"])
    # 格式化时间 (git输出如 2026-07-06 10:20:30 +0800)
    commit_time = commit_time[:19]  # 截取前19个字符
    # 获取完整的diff，排除二进制文件和可能太大的输出
    raw_diff = run_git_cmd(["show", "--no-color", "--unified=10", "--ignore-blank-lines", "--", "."])
    diff = clean_diff(raw_diff)
    # 完整 commit message (标题+正文)
    full_message = run_git_cmd(["log", "-1", "--format=%B"])
    return commit_hash, commit_time, diff, full_message

def extract_prompt(commit_message):
    """
    从 commit message 中提取 prompt_content。
    规则：将所有行合并为 prompt。
    """
    return commit_message.strip()
    
def clean_diff(raw_diff):
    """
    从完整的 git show 输出中只提取 unified diff 内容。
    即丢弃 commit 头部信息、diff 命令行和文件头部，仅保留以 @@ 开头的变更块。
    """
    # 查找第一个以 @@ 开头的行（re.MULTILINE 使 ^ 匹配行首）
    match = re.search(r'^@@.*', raw_diff, re.MULTILINE)
    if match:
        return raw_diff[match.start():].strip()
    # 如果没有 @@，说明可能是新增/删除文件（无传统 diff），原样返回
    return raw_diff.strip()

def main():
    # 1. 获取 Git 最新提交信息
    try:
        commit_hash, commit_time, diff, full_message = get_latest_commit_info()
    except Exception as e:
        print(f"获取Git信息失败: {e}")
        sys.exit(1)

    # 2. 提取 prompt
    prompt_content = extract_prompt(full_message)
    if not prompt_content:
        print("错误: prompt内容为空，请检查commit message")
        sys.exit(1)
    elif prompt_content.startswith("手动修改："):
        print("此轮修改为手动修改，不记录到json中。")
        sys.exit(0)

    # 3. 固定信息
    agent_type = "Kilo Code"
    dev_language = "Java"

    # 4. 计算轮次序号
    log_file = "D:\\winter_study\\assessment\\record.jsonl"
    round_id = 1
    try:
        with open(log_file, "r", encoding="utf-8") as f:
            round_id = sum(1 for _ in f) + 1 # 计录行数，加1得到当前轮次
    except FileNotFoundError:
        pass

    # 5. 构建记录并写入
    record = {
        "round_id": round_id,
        "prompt_content": prompt_content,
        "modify_diff": diff,
        "commit_hash": commit_hash,
        "modify_time": commit_time,
        "agent_type": agent_type,
        "dev_language": dev_language
    }

    with open(log_file, "a", encoding="utf-8") as f:
        f.write(json.dumps(record, ensure_ascii=False) + "\n")

    print(f"第 {round_id} 轮记录已追加到 {log_file} (commit {commit_hash})")

if __name__ == "__main__":
    main()