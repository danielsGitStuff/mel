package de.mein.drive;

import android.test.mock.MockContext;

import org.junit.Before;
import org.junit.Test;

import java.io.File;

import de.mein.android.file.AndroidFileConfiguration;
import de.mein.auth.file.AFile;

public class FSTest {
    private MockContext context;

    @Before
    public void before() {
        context = new MockContext();
        AFile.configure(new AndroidFileConfiguration(context));
    }

    @Test
    public void hierarchy() throws Exception {
        File dir = context.getDataDir();
        System.out.println("FSTest.hierarchy: "+dir.getAbsolutePath());
    }
}
