package de.mein.drive.sql;

import de.mein.drive.data.fs.RootDirectory;
import de.mein.sql.Pair;

import java.util.List;

/**
 * Created by xor on 10/27/16.
 */
public class GenericFSEntry extends FsEntry  {
    @Override
    protected void calcContentHash(List<FsDirectory> subDirectories, List<FsFile> files) {

    }

    @Override
    public void calcContentHash() {

    }

    public FsEntry ins() {
        FsEntry ins;
        if (isDirectory.v()) {
            ins = new FsDirectory();
        } else {
            ins = new FsFile();
        }
        for (int i = 0; i < allAttributes.size(); i++) {
            ins.getAllAttributes().get(i).v((Pair) allAttributes.get(i));
        }
        return ins;
    }

}
