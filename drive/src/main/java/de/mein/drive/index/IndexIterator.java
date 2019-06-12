package de.mein.drive.index;

import de.mein.auth.file.AFile;
import de.mein.drive.sql.dao.FsDao;
import de.mein.drive.sql.dao.StageDao;

import java.util.Iterator;

public class IndexIterator {
    private final Iterator<AFile> iterator;
    private final FsDao fsDao;
    private final StageDao stageDao;

    public IndexIterator(Iterator<AFile> iterator, FsDao fsDao, StageDao stageDao) {
        this.iterator = iterator;
        this.fsDao = fsDao;
        this.stageDao = stageDao;
    }

    public boolean hasNext() {
        return iterator.hasNext();
    }

    public AFile next() {
        AFile aFile = iterator.next();
        return aFile;
    }
}
