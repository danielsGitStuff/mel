package de.mel.filesync.index;

import org.junit.jupiter.api.Test;

import de.mel.filesync.bash.FsBashDetails;
import de.mel.filesync.sql.GenericFSEntry;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Android truncates the times of files (the last digits are zero), the fs table may hold what another platform reported. The indexer must not take that for a change,
 * else the file is hashed again each time it is looked at.
 */
public class SameAsInFsTest {
    private static GenericFSEntry entry(Long created, Long modified, long inode, long size) {
        GenericFSEntry generic = new GenericFSEntry();
        generic.getCreated().v(created);
        generic.getModified().v(modified);
        generic.getiNode().v(inode);
        generic.getSize().v(size);
        generic.getIsDirectory().v(false);
        return generic;
    }

    private static FsBashDetails disk(Long created, Long modified, long inode) {
        return new FsBashDetails(created, modified, inode, false, null, "f");
    }

    @Test
    public void theSameTimesAreTheSame() {
        assertTrue(AbstractIndexer.sameAsInFs(entry(1_700_000_123_456L, 1_700_000_223_456L, 7, 10), disk(1_700_000_123_456L, 1_700_000_223_456L, 7), 10));
    }

    @Test
    public void timesThatDifferOnlyInTheLastDigitsAreTheSame() {
        assertTrue(AbstractIndexer.sameAsInFs(entry(1_700_000_123_456L, 1_700_000_223_456L, 7, 10), disk(1_700_000_123_000L, 1_700_000_223_000L, 7), 10));
        assertTrue(AbstractIndexer.sameAsInFs(entry(1_700_000_123_000L, 1_700_000_223_000L, 7, 10), disk(1_700_000_123_999L, 1_700_000_223_001L, 7), 10));
    }

    @Test
    public void anotherSecondIsAnotherTime() {
        assertFalse(AbstractIndexer.sameAsInFs(entry(1_700_000_123_456L, 1_700_000_223_456L, 7, 10), disk(1_700_000_123_456L, 1_700_000_224_456L, 7), 10));
        assertFalse(AbstractIndexer.sameAsInFs(entry(1_700_000_123_456L, 1_700_000_223_456L, 7, 10), disk(1_700_000_124_456L, 1_700_000_223_456L, 7), 10));
    }

    @Test
    public void anotherInodeOrSizeIsAChange() {
        assertFalse(AbstractIndexer.sameAsInFs(entry(1L, 2L, 7, 10), disk(1L, 2L, 8), 10));
        assertFalse(AbstractIndexer.sameAsInFs(entry(1L, 2L, 7, 10), disk(1L, 2L, 7), 11));
    }

    @Test
    public void aMissingCreationTimeIsOnlyTheSameAsAMissingOne() {
        assertTrue(AbstractIndexer.sameAsInFs(entry(null, 2_000L, 7, 10), disk(null, 2_000L, 7), 10));
        assertFalse(AbstractIndexer.sameAsInFs(entry(null, 2_000L, 7, 10), disk(1_000L, 2_000L, 7), 10));
    }
}
