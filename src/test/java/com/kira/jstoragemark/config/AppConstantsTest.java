package com.kira.jstoragemark.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;

class AppConstantsTest {

    @Test
    @DisplayName("resolveTestDirectory should return default when no env/sysprop set")
    void resolveTestDirectoryShouldReturnDefault() {
        assertThat(AppConstants.resolveTestDirectory())
                .isEqualTo(Path.of(AppConstants.DEFAULT_TEST_DIR));
    }

    @Test
    @DisplayName("resolveTestDirectory should use system property when set")
    void resolveTestDirectoryShouldUseSystemProperty() {
        String customPath = "C:/custom/test/dir";
        String previous = System.setProperty(AppConstants.PROP_HOME_VAR, customPath);
        try {
            assertThat(AppConstants.resolveTestDirectory())
                    .isEqualTo(Path.of(customPath));
        } finally {
            if (previous != null) {
                System.setProperty(AppConstants.PROP_HOME_VAR, previous);
            } else {
                System.clearProperty(AppConstants.PROP_HOME_VAR);
            }
        }
    }

    @Test
    @DisplayName("resolveTestDirectory should fall back to default when system property is blank")
    void resolveTestDirectoryShouldFallbackForBlankProperty() {
        String previous = System.setProperty(AppConstants.PROP_HOME_VAR, "");
        try {
            assertThat(AppConstants.resolveTestDirectory())
                    .isEqualTo(Path.of(AppConstants.DEFAULT_TEST_DIR));
        } finally {
            if (previous != null) {
                System.setProperty(AppConstants.PROP_HOME_VAR, previous);
            } else {
                System.clearProperty(AppConstants.PROP_HOME_VAR);
            }
        }
    }

    @Test
    @DisplayName("Constants should be defined")
    void constantsShouldBeDefined() {
        assertThat(AppConstants.VERSION).isNotEmpty();
        assertThat(AppConstants.DEFAULT_TEST_DIR).isNotEmpty();
        assertThat(AppConstants.ENV_HOME_VAR).isEqualTo("JSTORAGEMARK_HOME");
        assertThat(AppConstants.PROP_HOME_VAR).isEqualTo("jstoragemark.dir");
        assertThat(AppConstants.SESSION_PREFIX).isEqualTo("jsm-");
    }
}
