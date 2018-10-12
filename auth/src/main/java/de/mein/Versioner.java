package de.mein;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class Versioner {

    private static BuildReader buildReader = new BuildReader() {
        private String buildVersion;

        @Override
        public String readBuildVersion() {
            if (buildVersion == null) {
                File versionFile = new File("auth" + File.separator + "src" + File.separator + "main" + File.separator + "resources" + File.separator + "version.txt");
                byte[] bytes;
                try {
                    bytes = Files.readAllBytes(Paths.get(versionFile.toURI()));
                    buildVersion = new String(bytes);
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
