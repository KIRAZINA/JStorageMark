package com.kira.jstoragemark.config;

import java.nio.file.Path;

public final class AppConstants {

    public static final String VERSION = "1.1.0";
    public static final String DEFAULT_TEST_DIR = "./jstoragemark-tests";
    public static final String DEFAULT_LOG_DIR = "./jstoragemark-tests";
    public static final String ENV_HOME_VAR = "JSTORAGEMARK_HOME";
    public static final String PROP_HOME_VAR = "jstoragemark.dir";
    public static final String SESSION_PREFIX = "jsm-";

    private AppConstants() {}

    public static Path resolveTestDirectory() {
        String fromEnv = System.getenv(ENV_HOME_VAR);
        if (fromEnv != null && !fromEnv.isBlank()) {
            return Path.of(fromEnv);
        }
        String fromProp = System.getProperty(PROP_HOME_VAR);
        if (fromProp != null && !fromProp.isBlank()) {
            return Path.of(fromProp);
        }
        return Path.of(DEFAULT_TEST_DIR);
    }
}
