CREATE TABLE IF NOT EXISTS bookmarks (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    url         TEXT    NOT NULL,
    title       TEXT,
    icon        TEXT,
    category    TEXT,
    add_date    TEXT,
    create_time TEXT    NOT NULL DEFAULT (datetime('now','localtime')),
    update_time TEXT    NOT NULL DEFAULT (datetime('now','localtime'))
);

CREATE INDEX IF NOT EXISTS idx_bookmarks_url ON bookmarks (url);
CREATE INDEX IF NOT EXISTS idx_bookmarks_category ON bookmarks (category);
