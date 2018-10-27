package de.mein.auth;

/**
 * Created by xor on 08.08.2017.
 */

public class MeinStrings {
    public static final String SERVICE_NAME = "meinauth";

    public static class update {
        public static final String QUERY_VERSION = "v?";
        public static final String QUERY_FILE = "f=";
        public static final String VARIANT_FX = "fx";
        public static final String VARIANT_JAR = "konsole";
        public static final String VARIANT_APK = "apk";
        public static final String INFO_APPENDIX = ".properties";
    }

    public static class msg {
        public static final String INTENT_REGISTER = "reg";
        public static final String INTENT_AUTH = "auth";
        public static final String INTENT_GET_SERVICES = "getservices";
        public static final String MODE_ISOLATE = "isolate";
        public static final String GET_CERT_ANSWER = "getCert.answer";
        public static final String GET_CERT = "getCert";
        public static final String STATE_OK = "ok";
        public static final String STATE_ERR = "err";
    }

    public static class Notifications {

        public static final String SERVICE_UUID = "n.uuid";
        public static final String INTENTION = "n.intention";
        public static final String EXTRA = "n.extra/";
        public static final String REQUEST_CODE = "mein.notification.id";
    }
}
