package de.mel.filesync.serialization;


import de.mel.auth.TestFileCreator;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by xor on 7/10/16.
 */
public class TestDirCreator {
    public static List<String> createTestDir(File testDir) throws Exception {
        return createTestDir(testDir, "");
    }

    public static List<String> createTestDir(File testDir, String appendix) throws Exception {

        List<String> paths = new ArrayList<>();
        testDir.mkdirs();
        File sub1 = new File(testDir, "sub1");
        File sub2 = new File(testDir, "sub2");
        sub1.mkdirs();
        sub2.mkdirs();

        File sub1txt = new File(sub1, "sub1.txt");
        TestFileCreator.saveFile(TestFileCreator.genBigFile(), sub1txt);

        File sub2txt = new File(sub1, "sub2.txt");
        TestFileCreator.saveFile(("sub2.txt text" + appendix).getBytes(), sub2txt);

        File sub22 = new File(sub2, "sub22");
        sub22.mkdirs();

        File sub22txt = new File(sub22, "sub22.txt");
        TestFileCreator.saveFile(("sub22.txt text" + appendix).getBytes(), sub22txt);
        File sameDir = new File(testDir, "samedir");
        sameDir.mkdirs();
        File same1txt = new File(sameDir, "same1.txt");
        File same2txt = new File(sameDir, "same2.txt");
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

    public static List<String> createFilesTestDir(File testDir, int noOfFiles) throws Exception {

        List<String> paths = new ArrayList<>();
        testDir.mkdirs();
        File sub1 = new File(testDir, "sub1");
        File sub2 = new File(testDir, "sub2");
        sub1.mkdirs();
        sub2.mkdirs();

        File sub1txt = new File(sub1, "sub1.txt");
        TestFileCreator.saveFile(TestFileCreator.genBigFile(), sub1txt);

        File sub2txt = new File(sub1, "sub2.txt");
        TestFileCreator.saveFile(("sub2.txt text").getBytes(), sub2txt);

        File sub22 = new File(sub2, "sub22");
        sub22.mkdirs();

        File sub22txt = new File(sub22, "sub22.txt");
        TestFileCreator.saveFile("sub22.txt text".getBytes(), sub22txt);
        File sameDir = new File(testDir, "samedir");
        sameDir.mkdirs();
        File same1txt = new File(sameDir, "same1.txt");
        File same2txt = new File(sameDir, "same2.txt");
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


    public static List<String> createTestDir(File testDir, int noOfSubdirs) throws Exception {

        List<String> paths = new ArrayList<>();
        testDir.mkdirs();
        File sub1 = new File(testDir, "sub1");
        File sub2 = new File(testDir, "sub2");
        sub1.mkdirs();
        sub2.mkdirs();

        File sub1txt = new File(sub1, "sub1.txt");
        TestFileCreator.saveFile(TestFileCreator.genBigFile(), sub1txt);

        File sub2txt = new File(sub1, "sub2.txt");
        TestFileCreator.saveFile(("sub2.txt text").getBytes(), sub2txt);

        File sub22 = new File(sub2, "sub22");
        sub22.mkdirs();

        File sub22txt = new File(sub22, "sub22.txt");
        TestFileCreator.saveFile("sub22.txt text".getBytes(), sub22txt);
        File sameDir = new File(testDir, "samedir");
        sameDir.mkdirs();
        File same1txt = new File(sameDir, "same1.txt");
        File same2txt = new File(sameDir, "same2.txt");
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

    public static void createTestDirSimple(File rootDir) throws IOException {
        rootDir.mkdirs();
        File sub = new File(rootDir, "sub");
        sub.mkdirs();

        File synced1 = new File(sub, "synced1.txt");
        File synced2 = new File(sub, "synced2.txt");
        File unsynced3 = new File(sub, "unsynced3.txt");
        File synced4 = new File(sub, "synced4.txt");

        Files.write(synced1.toPath(), new byte[]{1});
        Files.write(synced2.toPath(), new byte[]{2});
        Files.write(unsynced3.toPath(), new byte[]{3});
        Files.write(synced4.toPath(), new byte[]{4});
    }
}
