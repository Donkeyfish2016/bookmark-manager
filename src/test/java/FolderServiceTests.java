import com.bookmark.db.BookmarkDAO;
import com.bookmark.db.DatabaseMgr;
import com.bookmark.db.FolderDAO;
import com.bookmark.model.Bookmark;
import com.bookmark.model.Folder;
import com.bookmark.service.FolderService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FolderServiceTests {

    private FolderService folderService;

    @BeforeAll
    static void beforeAll() {
        DatabaseMgr.initialize();
    }

    @BeforeEach
    void setUp() {
        clearTestData();
        folderService = new FolderService(new FolderDAO(), new BookmarkDAO());
    }

    @AfterEach
    void tearDown() {
        clearTestData();
    }

    @Test
    void loadFolderTreeShouldBuildHierarchyAndAttachBookmarks() {
        int rootId = folderService.createFolder("ROOT", null);
        int childId = folderService.createFolder("CHILD", rootId);
        folderService.createFolder("SIBLING", rootId);

        Bookmark bookmark = new Bookmark();
        bookmark.setUrl("https://example.com");
        bookmark.setTitle("Example");
        bookmark.setCategory("Example");
        bookmark.setFolderId(childId);
        bookmark.setAddDate(LocalDateTime.now());
        new BookmarkDAO().insert(bookmark);

        Folder tree = folderService.loadFolderTree();

        assertNotNull(tree);
        assertTrue(tree.getChildren().containsKey("ROOT"));
        Folder rootNode = tree.getChildren().get("ROOT");
        assertNotNull(rootNode);
        assertTrue(rootNode.getChildren().containsKey("CHILD"));
        assertEquals(1, rootNode.getChildren().get("CHILD").getBookmarks().size());
        assertEquals("https://example.com", rootNode.getChildren().get("CHILD").getBookmarks().get(0).getUrl());
    }

    @Test
    void createFolderShouldRejectDuplicateNamesWithinSameParent() {
        int parentId = folderService.createFolder("PARENT", null);
        folderService.createFolder("CHILD", parentId);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> folderService.createFolder("CHILD", parentId));
        assertTrue(exception.getMessage().contains("already exists"));
    }

    @Test
    void renameFolderShouldUpdateNameAndRefreshMetadata() {
        int folderId = folderService.createFolder("OLD_NAME", null);
        Folder before = folderService.getFolderById(folderId);

        folderService.renameFolder(folderId, "NEW_NAME");

        Folder after = folderService.getFolderById(folderId);
        assertNotNull(after);
        assertEquals("NEW_NAME", after.getName());
        assertNotNull(after.getLastModified());
        assertNotNull(after.getUpdateTime());
        assertNotNull(before.getLastModified());
    }

    @Test
    void moveFolderShouldRejectCircularReferencesAndUpdateParent() {
        int rootId = folderService.createFolder("ROOT", null);
        int childId = folderService.createFolder("CHILD", rootId);
        int grandChildId = folderService.createFolder("GRAND_CHILD", childId);

        IllegalArgumentException circular = assertThrows(IllegalArgumentException.class,
                () -> folderService.moveFolder(childId, grandChildId));
        assertTrue(circular.getMessage().contains("circular"));

        int otherRootId = folderService.createFolder("OTHER_ROOT", null);
        folderService.moveFolder(childId, otherRootId);

        Folder moved = folderService.getFolderById(childId);
        assertEquals(otherRootId, moved.getParentId());
    }

    @Test
    void deleteFolderShouldPreventDeletionWhenChildrenOrBookmarksExist() {
        int parentId = folderService.createFolder("PARENT", null);
        int childId = folderService.createFolder("CHILD", parentId);

        IllegalStateException withChild = assertThrows(IllegalStateException.class,
                () -> folderService.deleteFolder(parentId));
        assertTrue(withChild.getMessage().contains("contains"));

        Bookmark bookmark = new Bookmark();
        bookmark.setUrl("https://bookmark.example");
        bookmark.setTitle("Bookmark");
        bookmark.setCategory("Bookmark");
        bookmark.setFolderId(childId);
        bookmark.setAddDate(LocalDateTime.now());
        new BookmarkDAO().insert(bookmark);

        IllegalStateException withBookmark = assertThrows(IllegalStateException.class,
                () -> folderService.deleteFolder(childId));
        assertTrue(withBookmark.getMessage().contains("contains"));
    }

    @Test
    void getSubFoldersAndBookmarksShouldReturnDirectChildrenAndFolderBookmarks() {
        int parentId = folderService.createFolder("PARENT", null);
        folderService.createFolder("CHILD_1", parentId);
        folderService.createFolder("CHILD_2", parentId);

        Bookmark bookmark = new Bookmark();
        bookmark.setUrl("https://sample.test");
        bookmark.setTitle("Sample");
        bookmark.setCategory("Sample");
        bookmark.setFolderId(parentId);
        bookmark.setAddDate(LocalDateTime.now());
        new BookmarkDAO().insert(bookmark);

        List<Folder> subFolders = folderService.getSubFolders(parentId);
        List<Bookmark> bookmarks = folderService.getBookmarksByFolder(parentId);

        assertEquals(2, subFolders.size());
        assertEquals(1, bookmarks.size());
        assertTrue(subFolders.stream().allMatch(folder -> parentId == folder.getParentId()));
    }

    private void clearTestData() {
        try (Connection conn = DatabaseMgr.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("DELETE FROM bookmarks");
            stmt.executeUpdate("DELETE FROM folders");
        } catch (Exception e) {
            throw new RuntimeException("Failed to clear test data", e);
        }
    }
}
