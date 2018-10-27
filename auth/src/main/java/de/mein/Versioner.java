package de.mein;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class Versioner {

    private static BuildReader buildReader = new BuildReader() {


        @Override
        public void readProperties() throws IOException {
            Properties properties = new Properties();
            InputStream in = getClass().getResourceAsStream("/version.properties");
            properties.load(in);
            version = Long.valueOf(properties.getProperty("version"));
            variant = properties.getProperty("variant");
        }
    };

    public static abstract class BuildReader {
        protected String variant;
        protected Long version;

        public abstract void readProperties() throws IOException;

        public String getVariant() throws IOException {
            if (variant == null)
                readProperties();
            return variant;
        }

        public Long getVersion() throws IOException {
            if (version == null)
                readProperties();
            return version;
        }
    }

    public static Long getBuildVersion() throws IOException {
        return buildReader.getVersion();
    }

    public static String getBuildVariant() throws IOException {
        return buildReader.getVariant();
    }

    public static void configure(BuildReader buildReader) {
        Versioner.buildReader = buildReader;
    }
}
