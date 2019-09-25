package de.mel.drive.sql;

import de.mel.sql.Pair;

import java.util.List;

/**
 * Created by xor on 10/27/16.
 */
public class GenericFSEntry extends FsEntry {
    public static Stage generic2Stage(GenericFSEntry genericFSEntry, Long stageSetId) {
        Stage stage = new Stage()
                .setFsId(genericFSEntry.getId().v())
                .setFsParentId(genericFSEntry.getParentId().v())
                .setName(genericFSEntry.getName().v())
                .setIsDirectory(genericFSEntry.getIsDirectory().v())
                .setContentHash(genericFSEntry.getContentHash().v())
                .setStageSet(stageSetId)
                .setSize(genericFSEntry.getSize().v())
                .setVersion(genericFSEntry.getVersion().v())
                .setCreated(genericFSEntry.getCreated().v())
                .setDeleted(false);
        if (!stage.getIsDirectory())
            stage.setSynced(genericFSEntry.getSynced().v());
        return stage;

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
