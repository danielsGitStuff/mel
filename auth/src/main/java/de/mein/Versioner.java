package de.mein;

import de.mein.auth.tools.F;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Scanner;

public class Versioner {

    private static BuildReader buildReader = new BuildReader() {
        private String buildVersion;

        @Override
        public String readBuildVersion() {
            if (buildVersion == null) {
                try {
                    buildVersion = F.readResourceToString("/version.txt");
                } catch (IOException e) {
                    e.printStackTrace();
                    return "could not read";
                }
            }
            return buildVersion;
        }
    };

    public interface BuildReader {
        String readBuildVersion();
    }

    public static String getBuildVersion() {
        return buildReader.readBuildVersion();
    }

    public static void configure(BuildReader buildReader) {
        Versioner.buildReader = buildReader;
    }
}
