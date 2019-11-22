package de.mel.filesync.serialization;


import de.mel.auth.TestFileCreator;
import de.mel.auth.file.AbstractFile;
import de.mel.auth.file.IFile;
import de.mel.filesync.bash.BashTools;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by xor on 7/10/16.
 */
public class TestDirCreator {
    public static List<String> createTestDir(IFile testDir) throws Exception {
        return createTestDir(testDir, "");
    }

    public static List<String> createTestDir(File testDir) throws Exception {
        return createTestDir(AbstractFile.instance(testDir), "");
    }

    public static List<String> createTestDir(IFile testDir, String appendix) throws Exception {

        List<String> paths = new ArrayList<>();
        //CertificateManager.deleteDirectory(testDir);
        testDir.mkdirs();
        IFile sub1 = AbstractFile.instance(testDir.getPath() + File.separator + "sub1");
        IFile sub2 = AbstractFile.instance(testDir.getPath() + File.separator + "sub2");
        sub1.mkdirs();
        sub2.mkdirs();

        IFile sub1txt = AbstractFile.instance(sub1.getPath() + File.separator + "sub1.txt");
        TestFileCreator.saveFile(TestFileCreator.genBigFile(), sub1txt);

        IFile sub2txt = AbstractFile.instance(sub1.getPath() + File.separator + "sub2.txt");
        TestFileCreator.saveFile(("sub2.txt text" + appendix).getBytes(), sub2txt);

        IFile sub22 = AbstractFile.instance(sub2.getPath() + File.separator + "sub22");
        sub22.mkdirs();

        IFile sub22txt = AbstractFile.instance(sub22.getPath() + File.separator + "sub22.txt");
        TestFileCreator.saveFile("sub22.txt text".getBytes(), sub22txt);
        IFile sameDir = AbstractFile.instance(testDir.getPath() + File.separator + "samedir");
        sameDir.mkdirs();
        IFile same1txt = AbstractFile.instance(sameDir.getPath() + File.separator + "same1.txt");
        IFile same2txt = AbstractFile.instance(sameDir.getPath() + File.separator + "same2.txt");
        TestFileCreator.saveFile("same".getBytes(), same1txt);
        TestFileCreator.saveFile("same".getBytes(), same2txt);

        paths.add(testDir.getAbsolutePath());
        paths.add(sameDir.getAbsolutePath());
        paths.add(sub1.getAbsolutePath());
        paths.add(sub2.getAbsolutePath());
        paths.add(same1txt.getAbsolutePath());
        paths.add(same2txt.getAbsolutePath());
        paths.add(sub1txt.getAbsolutePath());
        paths.add(sub2txt.getAbsolutePath());
        paths.add(sub22.getAbsolutePath());
        paths.add(sub22txt.getAbsolutePath());

        return paths;
    }

    public static List<String> createFilesTestDir(AbstractFile testDir, int noOfFiles) throws Exception {

        List<String> paths = new ArrayList<>();
        //CertificateManager.deleteDirectory(testDir);
        testDir.mkdirs();
        IFile sub1 = AbstractFile.instance(testDir.getPath() + File.separator + "sub1");
        IFile sub2 = AbstractFile.instance(testDir.getPath() + File.separator + "sub2");
        sub1.mkdirs();
        sub2.mkdirs();

        IFile sub1txt = AbstractFile.instance(sub1.getPath() + File.separator + "sub1.txt");
        TestFileCreator.saveFile(TestFileCreator.genBigFile(), sub1txt);

        IFile sub2txt = AbstractFile.instance(sub1.getPath() + File.separator + "sub2.txt");
        TestFileCreator.saveFile(("sub2.txt text").getBytes(), sub2txt);

        IFile sub22 = AbstractFile.instance(sub2.getPath() + File.separator + "sub22");
        sub22.mkdirs();

        IFile sub22txt = AbstractFile.instance(sub22.getPath() + File.separator + "sub22.txt");
        TestFileCreator.saveFile("sub22.txt text".getBytes(), sub22txt);
        IFile sameDir = AbstractFile.instance(testDir.getPath() + File.separator + "samedir");
        sameDir.mkdirs();
        IFile same1txt = AbstractFile.instance(sameDir.getPath() + File.separator + "same1.txt");
        IFile same2txt = AbstractFile.instance(sameDir.getPath() + File.separator + "same2.txt");
        TestFileCreator.saveFile("same".getBytes(), same1txt);
        TestFileCreator.saveFile("same".getBytes(), same2txt);
        for (Integer i = 0; i < noOfFiles; i++) {
            File f = new File(sub2.getAbsolutePath() + File.separator + "i" + i.toString());
            new FileOutputStream(f).close();
        }

        paths.add(testDir.getAbsolutePath());
        paths.add(sameDir.getAbsolutePath());
        paths.add(sub1.getAbsolutePath());
        paths.add(sub2.getAbsolutePath());
        paths.add(same1txt.getAbsolutePath());
        paths.add(same2txt.getAbsolutePath());
        paths.add(sub1txt.getAbsolutePath());
        paths.add(sub2txt.getAbsolutePath());
        paths.add(sub22.getAbsolutePath());
        paths.add(sub22txt.getAbsolutePath());

        return paths;
    }


    public static List<String> createTestDir(AbstractFile testDir, int noOfSubdirs) throws Exception {

        List<String> paths = new ArrayList<>();
        //CertificateManager.deleteDirectory(testDir);
        testDir.mkdirs();
        IFile sub1 = AbstractFile.instance(testDir.getPath() + File.separator + "sub1");
        IFile sub2 = AbstractFile.instance(testDir.getPath() + File.separator + "sub2");
        sub1.mkdirs();
        sub2.mkdirs();

        IFile sub1txt = AbstractFile.instance(sub1.getPath() + File.separator + "sub1.txt");
        TestFileCreator.saveFile(TestFileCreator.genBigFile(), sub1txt);

        IFile sub2txt = AbstractFile.instance(sub1.getPath() + File.separator + "sub2.txt");
        TestFileCreator.saveFile(("sub2.txt text").getBytes(), sub2txt);

        IFile sub22 = AbstractFile.instance(sub2.getPath() + File.separator + "sub22");
        sub22.mkdirs();

        IFile sub22txt = AbstractFile.instance(sub22.getPath() + File.separator + "sub22.txt");
        TestFileCreator.saveFile("sub22.txt text".getBytes(), sub22txt);
        IFile sameDir = AbstractFile.instance(testDir.getPath() + File.separator + "samedir");
        sameDir.mkdirs();
        IFile same1txt = AbstractFile.instance(sameDir.getPath() + File.separator + "same1.txt");
        IFile same2txt = AbstractFile.instance(sameDir.getPath() + File.separator + "same2.txt");
        TestFileCreator.saveFile("same".getBytes(), same1txt);
        TestFileCreator.saveFile("same".getBytes(), same2txt);
        for (Integer i = 0; i < noOfSubdirs; i++) {
            new File(sub2.getAbsolutePath() + File.separator + "i" + i.toString()).mkdirs();
        }

        paths.add(testDir.getAbsolutePath());
        paths.add(sameDir.getAbsolutePath());
        paths.add(sub1.getAbsolutePath());
        paths.add(sub2.getAbsolutePath());
        paths.add(same1txt.getAbsolutePath());
        paths.add(same2txt.getAbsolutePath());
        paths.add(sub1txt.getAbsolutePath());
        paths.add(sub2txt.getAbsolutePath());
        paths.add(sub22.getAbsolutePath());
        paths.add(sub22txt.getAbsolutePath());

        return paths;
    }

    public static void createTestDirSimple(AbstractFile rootDir) throws IOException {
        rootDir.mkdirs();
        IFile sub = AbstractFile.instance(rootDir, "sub");
        sub.mkdirs();

        IFile synced1 = AbstractFile.instance(sub, "synced1.txt");
        IFile synced2 = AbstractFile.instance(sub, "synced2.txt");
        IFile unsynced3 = AbstractFile.instance(sub, "unsynced3.txt");
        IFile synced4 = AbstractFile.instance(sub, "synced4.txt");


        synced1.writer().append(new byte[]{1}, 0);
        synced2.writer().append(new byte[]{2}, 0);
        unsynced3.writer().append(new byte[]{3}, 0);
        synced4.writer().append(new byte[]{4}, 0);
    }
}
