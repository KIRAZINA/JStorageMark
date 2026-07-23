package com.kira.jstoragemark.cli;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class BlockSweepTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("parseBlockSweep should parse K/M suffixes")
    void parseBlockSweepShouldParseSuffixes() {
        List<Integer> sizes = Main.parseBlockSweep("4K,64K,1M");
        assertThat(sizes).containsExactly(4096, 65536, 1048576);
    }

    @Test
    @DisplayName("parseBlockSweep should handle raw bytes")
    void parseBlockSweepShouldHandleRawBytes() {
        List<Integer> sizes = Main.parseBlockSweep("512,4096,8192");
        assertThat(sizes).containsExactly(512, 4096, 8192);
    }

    @Test
    @DisplayName("parseBlockSweep should handle single value")
    void parseBlockSweepShouldHandleSingleValue() {
        List<Integer> sizes = Main.parseBlockSweep("64K");
        assertThat(sizes).containsExactly(65536);
    }

    @Test
    @DisplayName("parseBlockSweep should reject out-of-range values")
    void parseBlockSweepShouldRejectOutOfRange() {
        assertThatThrownBy(() -> Main.parseBlockSweep("100"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Main.parseBlockSweep("128M"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Block sweep workflow should complete successfully")
    void blockSweepWorkflowShouldComplete() {
        String[] args = {
                "-d", tempDir.toString(),
                "-t", "SEQ_WRITE",
                "-s", String.valueOf(1024L * 1024 * 1024),
                "-n", "1",
                "-i", "1",
                "--block-sweep", "4K,64K"
        };

        assertThat(Main.run(args)).isEqualTo(0);
    }
}
