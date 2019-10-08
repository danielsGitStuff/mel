package de.mel;

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
            timestamp = Long.valueOf(properties.getProperty("timestamp"));
            variant = properties.getProperty("variant");
            version = properties.getProperty("version");
        }
    };

    public static abstract class BuildReader {
        protected String variant;
        protected Long timestamp;
        protected String version;

        public String getVersion() {
            return version;
        }

        public abstract void readProperties() throws IOException;

        public String getVariant() throws IOException {
            if (variant == null)
                readProperties();
            return variant;
        }

        public Long getTimestamp() throws IOException {
            if (timestamp == null)
                readProperties();
            return timestamp;
        }
    }

    public static String getVersion() throws IOException {
        return buildReader.getVersion();
    }

    public static Long getTimestamp() throws IOException {
        return buildReader.getTimestamp();
    }

    public static String getBuildVariant() throws IOException {
        return buildReader.getVariant();
    }

    public static void configure(BuildReader buildReader) {
        Versioner.buildReader = buildReader;
    }
}
