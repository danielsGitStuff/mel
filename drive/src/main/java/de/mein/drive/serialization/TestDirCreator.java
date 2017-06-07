package de.mein.drive.serialization;


import de.mein.auth.TestFileCreator;

import java.io.File;
import java.io.IOException;

/**
 * Created by xor on 7/10/16.
 */
public class TestDirCreator {
    public static void createTestDir(File testDir) throws IOException {

        //CertificateManager.deleteDirectory(testDir);
        testDir.mkdirs();
        File sub1 = new File(testDir.getPath() + File.separator + "sub1");
        File sub2 = new File(testDir.getPath() + File.separator + "sub2");
        sub1.mkdirs();
        sub2.mkdirs();

        File sub2file = new File(sub2.getPath() + File.separator + "sub2.txt");

        File sub1txt = new File(sub1.getPath() + File.separator + "sub1.txt");
        TestFileCreator.saveFile(TestFileCreator.genBigFile(), sub1txt);

        File sub2txt = new File(sub1.getPath() + File.separator + "sub2.txt");
        TestFileCreator.saveFile("sub2.txt text".getBytes(), sub2txt);

        File sub22 = new File(sub2.getPath() + File.separator + "sub22");
        sub22.mkdirs();

        File sub22txt = new File(sub22.getPath() + File.separator + "sub22.txt");
        TestFileCreator.saveFile("sub22.txt text".getBytes(), sub22txt);
        File sameDir = new File(testDir.getPath() + File.separator + "samedir");
        sameDir.mkdirs();
        TestFileCreator.saveFile("same".getBytes(), new File(sameDir.getPath() + File.separator + "same1.txt"));
        TestFileCreator.saveFile("same".getBytes(), new File(sameDir.getPath() + File.separator + "same2.txt"));
    }

    /*
    public void unZipIt(){

        byte[] buffer = new byte[1024];

        try{

            //create output directory is not exists
            File folder = new File(OUTPUT_FOLDER);
            if(!folder.exists()){
                folder.mkdir();
            }

            //get the zip file content
            ZipInputStream zis =
                    new ZipInputStream(new FileInputStream(zipFile));
            //get the zipped file list entry
            ZipEntry ze = zis.getNextEntry();

            while(ze!=null){

                String fileName = ze.getSource();
                File newFile = new File(outputFolder + File.separator + fileName);

                System.out.println("file unzip : "+ newFile.getAbsoluteFile());

                //create all non exists folders
                //else you will hit FileNotFoundException for compressed folder
                new File(newFile.getParent()).mkdirs();

                FileOutputStream fos = new FileOutputStream(newFile);

                int len;
                while ((len = zis.read(buffer)) > 0) {
                    fos.write(buffer, 0, len);
                }

                fos.close();
                ze = zis.getNextEntry();
            }

            zis.closeEntry();
            zis.close();

            System.out.println("Done");

        }catch(IOException ex){
            ex.printStackTrace();
        }
    }*/
}
