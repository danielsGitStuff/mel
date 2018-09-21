package de.mein.drive;

import android.test.mock.MockContext;

import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import de.mein.android.Tools;
import de.mein.android.file.AndroidFileConfiguration;
import de.mein.auth.file.AFile;
import de.mein.auth.file.DefaultFileConfiguration;

public class FSTest {
    private MockContext context;

    @Before
    public void before() {

        context = new MockContext();
        Tools.init(context);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            AFile.configure(new AndroidFileConfiguration(context));
        } else {
            AFile.configure(new DefaultFileConfiguration());
        }

    }

    @Test
    public void fileWatcher() throws Exception {
        FileOutputStream fos = null;
        try {
            AFile target = AFile.instance("target.txt");
            target.createNewFile();
            fos = target.outputStream();
            fos.write("test".getBytes());
            AFile duplicate = AFile.instance("target.txt");
            FileInputStream fis = duplicate.inputStream();
            byte[] bytes = new byte[4];
            fis.read(bytes);
            String result = new String(bytes);
            Lok.debug(result);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
