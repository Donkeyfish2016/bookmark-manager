CREATE TABLE IF NOT EXISTS folders (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    name          TEXT    NOT NULL,
    parent_id     INTEGER,
    is_root       BOOLEAN NOT NULL DEFAULT 0,
    add_date      TEXT    NOT NULL,
    last_modified TEXT    NOT NULL,
    create_time   TEXT    NOT NULL DEFAULT (datetime('now','localtime')),
    update_time   TEXT    NOT NULL DEFAULT (datetime('now','localtime'))
);

CREATE TABLE IF NOT EXISTS bookmarks (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    url         TEXT    NOT NULL,
    title       TEXT    NOT NULL,
    icon        TEXT,
    category    TEXT    NOT NULL,
    add_date    TEXT    NOT NULL,
    create_time TEXT    NOT NULL DEFAULT (datetime('now','localtime')),
    update_time TEXT    NOT NULL DEFAULT (datetime('now','localtime'))
    update_time TEXT    NOT NULL DEFAULT (datetime('now','localtime')),
    folder_id   INTEGER
);

CREATE INDEX IF NOT EXISTS idx_folders_parent_id ON folders (parent_id);
CREATE INDEX IF NOT EXISTS idx_bookmarks_folder_id ON bookmarks (folder_id);
CREATE INDEX IF NOT EXISTS idx_bookmarks_url ON bookmarks (url);
CREATE INDEX IF NOT EXISTS idx_bookmarks_category ON bookmarks (category);