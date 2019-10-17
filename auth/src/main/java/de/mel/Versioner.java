package de.mel;

import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Properties;

public class Versioner {

    private static BuildReader buildReader = new BuildReader() {

        @Override
        public void readProperties() throws IOException {
            Properties properties = new Properties();
            InputStream in = getClass().getResourceAsStream("/version.properties");
            properties.load(in);
            variant = properties.getProperty("variant");
            version = properties.getProperty("version");
            commit = properties.getProperty("commit");

        }
    };


    public static boolean isYounger(String currentVersion, String newVersion) throws ParseException {
        SimpleDateFormat formatter = new SimpleDateFormat(VERSION_DATE_FORMAT_PATTERN);
        Date currentDate = formatter.parse(currentVersion);
        Date newDate = formatter.parse(newVersion);
        return currentDate.getTime() < newDate.getTime();
    }

    public static final String VERSION_DATE_FORMAT_PATTERN = "yyyy-MM-dd-hh-mm-ss";



    public static abstract class BuildReader {
        protected String variant;
        protected String commit;
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

        public String getCommit() {
            return commit;
        }
    }

    public static String getVersion() throws IOException {
        return buildReader.getVersion();
    }

    public static String getCommit() throws IOException {
        return buildReader.getCommit();
    }

    public static String getBuildVariant() throws IOException {
        return buildReader.getVariant();
    }

    public static void configure(BuildReader buildReader) {
        Versioner.buildReader = buildReader;
    }
}
