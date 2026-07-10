# Test Plan: Bookmark Manager CLI Integration Tests

## 1. Overview
This test plan covers comprehensive integration testing of the Bookmark Manager CLI tool located in `src\main\java\com\bookmark\cli`. The plan ensures maximum coverage of all CRUD operations for bookmarks and folders, plus import/export functionality.

## 2. Test Strategy
- **Approach**: Black-box integration testing via the CLI interface
- **Verification Protocol**: After every mutation command (Create, Update, Delete), a Read (query/list/search) command is executed to confirm the state change was successful
- **Test Isolation**: Database cleanup between independent test groups
- **Execution Model**: Each CLI invocation runs in a separate JVM process; SQLite database state persists across invocations

## 3. Available CLI Commands

### 3.1 Bookmark Commands
| Command | Syntax | Description |
|---------|--------|-------------|
| `add` | `add -u <url> -t <title> [-i <icon>] [-c <category>]` | Add a new bookmark |
| `delete` | `delete -i <id>` | Delete bookmark by ID |
| `list` | `list [-c <category>] [-p <page>] [-s <size>]` | List bookmarks (JSON) |
| `search` | `search -k <keyword>` | Search bookmarks by keyword |
| `update` | `update -i <id> [-u <url>] [-t <title>] [--icon <icon>] [-c <category>]` | Update bookmark fields |
| `import` | `import -f <file>` | Import from Edge HTML file |
| `export` | `export [-o <output>]` | Export to HTML file |

### 3.2 Folder Commands
| Command | Syntax | Description |
|---------|--------|-------------|
| `folder add` | `folder add -n <name> [-p <parent-id>] [-r]` | Create a new folder |
| `folder delete` | `folder delete -i <id>` | Delete an empty folder |
| `folder rename` | `folder rename -i <id> -n <name>` | Rename a folder |
| `folder move` | `folder move -i <id> -p <parent-id>` | Move folder to new parent |
| `folder list` | `folder list [-p <parent-id>]` | List subfolders in table |
| `folder tree` | `folder tree` | Display folder hierarchy as ASCII tree |
| `folder info` | `folder info -i <id>` | Show folder detailed metadata |

## 4. Test Cases

### 4.1 Bookmark CRUD Tests

| TC ID | Test Case Name | Type | Steps | Verification |
|-------|----------------|------|-------|-------------|
| TC-BM-01 | AddBookmarkSuccess | Positive | `add -u "https://example.com" -t "Example" -c "default"` | `list` contains `"Example"` |
| TC-BM-02 | AddBookmarkWithIcon | Positive | `add -u "https://test.com" -t "Test" -i "icon.png" -c "default"` | `list` contains `"Test"` |
| TC-BM-03 | AddBookmarkMissingUrl | Negative | `add -t "NoURL" -c "default"` | Exit code != 0, error about URL |
| TC-BM-04 | AddBookmarkMissingTitle | Negative | `add -u "https://notitle.com" -c "default"` | Exit code != 0, error about title |
| TC-BM-05 | AddBookmarkDuplicateCategory | Positive | `add -u "https://dup.com" -t "Dup" -c "cat1"` | `list -c cat1` shows 2 items |
| TC-BM-06 | ListAllBookmarks | Read | `list` | JSON output with id, url, title |
| TC-BM-07 | ListByCategory | Read | `list -c "default"` | Only default category bookmarks |
| TC-BM-08 | ListPagination | Read | `list -p 1 -s 5` | At most 5 items returned |
| TC-BM-09 | SearchByUrl | Read | `search -k "example"` | Contains bookmark with matching URL |
| TC-BM-10 | SearchByTitle | Read | `search -k "Test"` | Contains bookmark with matching title |
| TC-BM-11 | SearchNoResults | Read | `search -k "nonexistent12345"` | "No bookmarks found" message |
| TC-BM-12 | UpdateBookmarkUrl | Positive | `update -i <id> -u "https://updated.com"` | `list` shows updated URL |
| TC-BM-13 | UpdateBookmarkTitle | Positive | `update -i <id> -t "Updated Title"` | `list` shows updated title |
| TC-BM-14 | UpdateBookmarkCategory | Positive | `update -i <id> -c "newcat"` | `list -c newcat` shows bookmark |
| TC-BM-15 | UpdatePartialFields | Positive | `update -i <id> -t "OnlyTitle"` | Unchanged fields preserved |
| TC-BM-16 | UpdateNonExistentId | Negative | `update -i 99999 -u "x" -t "y"` | Exit code != 0, not found error |
| TC-BM-17 | DeleteBookmark | Positive | `delete -i <id>` | `list` no longer contains bookmark |
| TC-BM-18 | DeleteNonExistentId | Negative | `delete -i 99999` | "no bookmark found" message, exit code 1 |

### 4.2 Folder Management Tests

| TC ID | Test Case Name | Type | Steps | Verification |
|-------|----------------|------|-------|-------------|
| TC-FD-01 | AddFolderSuccess | Positive | `folder add -n "TestFolder"` | `folder list` contains `TestFolder` |
| TC-FD-02 | AddFolderUnderParent | Positive | `folder add -n "ChildFolder" -p <parent-id>` | `folder list -p <parent-id>` shows it |
| TC-FD-03 | AddFolderAsRoot | Positive | `folder add -n "RootFolder" -r` | `folder tree` shows it at root level |
| TC-FD-04 | AddFolderDuplicateName | Negative | `folder add -n "TestFolder"` (duplicate) | "already exists" error, exit code 1 |
| TC-FD-05 | FolderListSubfolders | Read | `folder list -p <root-id>` | Table shows child folders |
| TC-FD-06 | FolderTree | Read | `folder tree` | ASCII tree structure displayed |
| TC-FD-07 | FolderInfo | Read | `folder info -i <id>` | Metadata fields displayed |
| TC-FD-08 | FolderInfoNonExistent | Negative | `folder info -i 99999` | "not found" message, exit code 1 |
| TC-FD-09 | RenameFolder | Positive | `folder rename -i <id> -n "NewName"` | `folder info` shows `NewName` |
| TC-FD-10 | RenameToDuplicate | Negative | `folder rename -i <id> -n "ExistingName"` | "already exists" error |
| TC-FD-11 | RenameNonExistent | Negative | `folder rename -i 99999 -n "Name"` | Exit code != 0, not found |
| TC-FD-12 | MoveFolder | Positive | `folder move -i <id> -p <new-parent>` | `folder tree` shows new position |
| TC-FD-13 | MoveIntoSelf | Negative | `folder move -i <id> -p <id>` | "cannot be moved into itself" error |
| TC-FD-14 | MoveIntoDescendant | Negative | `folder move -i <parent-id> -p <child-id>` | "circular move" error |
| TC-FD-15 | MoveToNonExistentParent | Negative | `folder move -i <id> -p 99999` | "parent does not exist" error |
| TC-FD-16 | DeleteEmptyFolder | Positive | `folder delete -i <id>` | `folder list` no longer shows it |
| TC-FD-17 | DeleteNonEmptyFolder | Negative | `folder delete -i <id-with-children>` | "contains child folders" error |
| TC-FD-18 | DeleteNonExistentFolder | Negative | `folder delete -i 99999` | Exit code != 0, not found |

### 4.3 Import/Export Tests

| TC ID | Test Case Name | Type | Steps | Verification |
|-------|----------------|------|-------|-------------|
| TC-IE-01 | ExportBookmarks | Positive | `export -o test_export.html` | File created with valid HTML |
| TC-IE-02 | ImportBookmarks | Positive | `import -f cli_export_test.html` | `list` shows imported bookmarks |

### 4.4 Edge Case Tests

| TC ID | Test Case Name | Type | Steps | Verification |
|-------|----------------|------|-------|-------------|
| TC-EC-01 | BookmarkSpecialChars | Positive | `add` with special chars in URL/title | `list` shows exact values |
| TC-EC-02 | BookmarkChineseChars | Positive | `add` with Chinese title | `list` shows correct Chinese |
| TC-EC-03 | FolderChineseChars | Positive | `folder add -n "中文文件夹"` | `folder list` shows `中文文件夹` |
| TC-EC-04 | EmptyListResult | Read | `list` with no bookmarks | "No bookmarks found" message |

## 5. Test Data Strategy
- Use deterministic, unique test data for each test case
- Clean database (`bookmarkmgr.db`) before each test group to ensure isolation
- Use specific category/folder names to avoid collisions with existing data

## 6. Pass/Fail Criteria
- **Pass**: Command executes with expected exit code AND subsequent Read command confirms the state change
- **Fail**: Command returns unexpected exit code OR Read command does not confirm expected state

## 7. Test Execution Order
1. Bookmark CRUD tests (independent, clean DB first)
2. Folder management tests (independent, clean DB first)
3. Import/Export tests (clean DB first, then import)
4. Edge case tests (clean DB first)
