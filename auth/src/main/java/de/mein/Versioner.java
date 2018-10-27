package de.mein;

import de.mein.auth.tools.ResourceList;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Properties;
import java.util.Scanner;
import java.util.regex.Pattern;

public class Versioner {

    private static BuildReader buildReader = new BuildReader() {


        @Override
        public void readProperties() throws IOException {
            Properties properties = new Properties();


            String ff = new File("").getAbsolutePath();

            InputStream in = getClass().getResourceAsStream("/version.properties");
            properties.load(in);
            version = properties.getProperty("builddate");
            variant = properties.getProperty("variant");
        }
    };

    public static abstract class BuildReader {
        protected String version, variant;

        public abstract void readProperties() throws IOException;

        public String getVariant() throws IOException {
            if (variant == null)
                readProperties();
            return variant;
        }

        public String getVersion() throws IOException {
            if (version == null)
                readProperties();
            return version;
        }
    }

    public static String getBuildVersion() throws IOException {
        return buildReader.getVersion();
    }

    public static String getBuildVariant() throws IOException {
        return buildReader.getVariant();
    }

    public static void configure(BuildReader buildReader) {
        Versioner.buildReader = buildReader;
    }
}
