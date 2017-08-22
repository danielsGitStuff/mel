package de.mein.drive.sql;

import de.mein.drive.data.fs.RootDirectory;
import de.mein.sql.Pair;

import java.util.List;

/**
 * Created by xor on 10/27/16.
 */
public class GenericFSEntry extends FsEntry  {
    public static Stage generic2Stage(GenericFSEntry genericFSEntry, Long stageSetId) {
        return new Stage()
                .setFsId(genericFSEntry.getId().v())
                .setFsParentId(genericFSEntry.getParentId().v())
                .setName(genericFSEntry.getName().v())
                .setIsDirectory(genericFSEntry.getIsDirectory().v())
                .setContentHash(genericFSEntry.getContentHash().v())
                .setStageSet(stageSetId)
                .setSize(genericFSEntry.getSize().v())
                .setVersion(genericFSEntry.getVersion().v())
                .setDeleted(false);
    }

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
