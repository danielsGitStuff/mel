package de.mel.drive.serialization;


import de.mel.auth.TestFileCreator;
import de.mel.auth.file.AFile;
import de.mel.auth.tools.N;
import de.mel.drive.bash.BashTools;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by xor on 7/10/16.
 */
public class TestDirCreator {
    public static List<String> createTestDir(AFile testDir) throws IOException {
        return createTestDir(testDir, "");
    }

    public static List<String> createTestDir(AFile testDir, String appendix) throws IOException {

        List<String> paths = new ArrayList<>();
        //CertificateManager.deleteDirectory(testDir);
        BashTools.mkdir(testDir);
        AFile sub1 = AFile.instance(testDir.getPath() + File.separator + "sub1");
        AFile sub2 = AFile.instance(testDir.getPath() + File.separator + "sub2");
        BashTools.mkdir(sub1);
        BashTools.mkdir(sub2);

        AFile sub1txt = AFile.instance(sub1.getPath() + File.separator + "sub1.txt");
        TestFileCreator.saveFile(TestFileCreator.genBigFile(), sub1txt);

        AFile sub2txt = AFile.instance(sub1.getPath() + File.separator + "sub2.txt");
        TestFileCreator.saveFile(("sub2.txt text" + appendix).getBytes(), sub2txt);

        AFile sub22 = AFile.instance(sub2.getPath() + File.separator + "sub22");
        BashTools.mkdir(sub22);

        AFile sub22txt = AFile.instance(sub22.getPath() + File.separator + "sub22.txt");
        TestFileCreator.saveFile("sub22.txt text".getBytes(), sub22txt);
        AFile sameDir = AFile.instance(testDir.getPath() + File.separator + "samedir");
        BashTools.mkdir(sameDir);
        AFile same1txt = AFile.instance(sameDir.getPath() + File.separator + "same1.txt");
        AFile same2txt = AFile.instance(sameDir.getPath() + File.separator + "same2.txt");
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

    public static List<String> createFilesTestDir(AFile testDir, int noOfFiles) throws IOException {

        List<String> paths = new ArrayList<>();
        //CertificateManager.deleteDirectory(testDir);
        BashTools.mkdir(testDir);
        AFile sub1 = AFile.instance(testDir.getPath() + File.separator + "sub1");
        AFile sub2 = AFile.instance(testDir.getPath() + File.separator + "sub2");
        BashTools.mkdir(sub1);
        BashTools.mkdir(sub2);

        AFile sub1txt = AFile.instance(sub1.getPath() + File.separator + "sub1.txt");
        TestFileCreator.saveFile(TestFileCreator.genBigFile(), sub1txt);

        AFile sub2txt = AFile.instance(sub1.getPath() + File.separator + "sub2.txt");
        TestFileCreator.saveFile(("sub2.txt text").getBytes(), sub2txt);

        AFile sub22 = AFile.instance(sub2.getPath() + File.separator + "sub22");
        BashTools.mkdir(sub22);

        AFile sub22txt = AFile.instance(sub22.getPath() + File.separator + "sub22.txt");
        TestFileCreator.saveFile("sub22.txt text".getBytes(), sub22txt);
        AFile sameDir = AFile.instance(testDir.getPath() + File.separator + "samedir");
        BashTools.mkdir(sameDir);
        AFile same1txt = AFile.instance(sameDir.getPath() + File.separator + "same1.txt");
        AFile same2txt = AFile.instance(sameDir.getPath() + File.separator + "same2.txt");
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


    public static List<String> createTestDir(AFile testDir, int noOfSubdirs) throws IOException {

        List<String> paths = new ArrayList<>();
        //CertificateManager.deleteDirectory(testDir);
        BashTools.mkdir(testDir);
        AFile sub1 = AFile.instance(testDir.getPath() + File.separator + "sub1");
        AFile sub2 = AFile.instance(testDir.getPath() + File.separator + "sub2");
        BashTools.mkdir(sub1);
        BashTools.mkdir(sub2);

        AFile sub1txt = AFile.instance(sub1.getPath() + File.separator + "sub1.txt");
        TestFileCreator.saveFile(TestFileCreator.genBigFile(), sub1txt);

        AFile sub2txt = AFile.instance(sub1.getPath() + File.separator + "sub2.txt");
        TestFileCreator.saveFile(("sub2.txt text").getBytes(), sub2txt);

        AFile sub22 = AFile.instance(sub2.getPath() + File.separator + "sub22");
        BashTools.mkdir(sub22);

        AFile sub22txt = AFile.instance(sub22.getPath() + File.separator + "sub22.txt");
        TestFileCreator.saveFile("sub22.txt text".getBytes(), sub22txt);
        AFile sameDir = AFile.instance(testDir.getPath() + File.separator + "samedir");
        BashTools.mkdir(sameDir);
        AFile same1txt = AFile.instance(sameDir.getPath() + File.separator + "same1.txt");
        AFile same2txt = AFile.instance(sameDir.getPath() + File.separator + "same2.txt");
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

    public static void createTestDirSimple(AFile rootDir) throws IOException {
        rootDir.mkdirs();
        AFile sub = AFile.instance(rootDir, "sub");
        sub.mkdirs();

        AFile synced1 = AFile.instance(sub, "synced1.txt");
        AFile synced2 = AFile.instance(sub, "synced2.txt");
        AFile unsynced3 = AFile.instance(sub, "unsynced3.txt");
        AFile synced4 = AFile.instance(sub, "synced4.txt");


        synced1.outputStream().write(1);
        synced2.outputStream().write(2);
        unsynced3.outputStream().write(3);
        synced4.outputStream().write(4);
    }
}
