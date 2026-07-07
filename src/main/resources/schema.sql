CREATE TABLE IF NOT EXISTS bookmarks (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    url         TEXT    NOT NULL,
    title       TEXT    NOT NULL,
    icon        TEXT,
    category    TEXT    NOT NULL,
    add_date    TEXT    NOT NULL,
    create_time TEXT    NOT NULL DEFAULT (datetime('now','localtime')),
    update_time TEXT    NOT NULL DEFAULT (datetime('now','localtime'))
);

CREATE INDEX IF NOT EXISTS idx_bookmarks_url ON bookmarks (url);
CREATE INDEX IF NOT EXISTS idx_bookmarks_category ON bookmarks (category);
