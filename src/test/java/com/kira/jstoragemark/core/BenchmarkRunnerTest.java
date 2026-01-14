package com.kira.jstoragemark.core;

import com.kira.jstoragemark.config.BenchmarkConfig;
import com.kira.jstoragemark.fs.BenchmarkPaths;
import com.kira.jstoragemark.result.BenchmarkResult;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class BenchmarkRunnerTest {

    @Test
    void runAllShouldProduceResults() throws Exception {
        BenchmarkConfig config = new BenchmarkConfig.Builder()
                .testDirectory(Path.of("./testdir"))
                .addTestType(BenchmarkConfig.TestType.SEQ_WRITE)
                .fileSizeBytes(1024L * 1024 * 1024) // 1 GB
                .blockSizeBytes(4 * 1024)           // 4 KB
                .threads(1)
                .queueDepth(1)
                .iterations(3)
                .build();

        BenchmarkPaths paths = new BenchmarkPaths(config.getTestDirectory(), config.getSessionId());
        BenchmarkRunner runner = new BenchmarkRunner(config, paths);

        List<BenchmarkResult> results = runner.runAll();

        assertThat(results).isNotEmpty();
        assertThat(results.get(0).getThroughputMBps()).isGreaterThan(0);
        assertThat(results.get(0).getElapsed().toMillis()).isGreaterThan(0);
    }
}
