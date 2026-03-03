# JStorageMark

## Overview
**JStorageMark** is a Java-based storage benchmarking tool designed to measure sequential and random read/write performance with enterprise-grade features. It provides both a **Swing GUI** and **command-line interface** for flexible usage, generates detailed reports in multiple formats (CSV, JSON, HTML), and collects real-time system metrics using OSHI.

---

## Features

### Benchmark Types
- 📊 **Sequential**: SEQ_READ, SEQ_WRITE
- 🎲 **Random**: RAND_READ, RAND_WRITE
- ✅ **Multiple test types** can run in a single session

### Configuration Parameters
- **Test directory** - Dedicated directory for benchmark files
- **File size** - 1 GB to 10 GB per test file
- **Block size** - 4 KB to 1 MB
- **Threads** - 1 to 32 concurrent threads
- **Iterations** - 3 to 10 runs per test type (for statistical averaging)
- **Queue depth** - Simulated concurrency
- **Random seed** - Optional seed for reproducible random tests

### User Interfaces
1. **GUI Mode** (`com.kira.jstoragemark.gui.BenchmarkUI`)
   - Checkboxes for multiple test type selection
   - Real-time progress bar with iteration tracking
   - Results table with averages
   - Copy results to clipboard
   - Input validation with error messages

2. **CLI Mode** (`com.kira.jstoragemark.cli.Main`)
   - Full automation support
   - Scriptable from command line
   - Exit codes for CI/CD integration

### Reports & Metrics
- 📑 **Report formats**: CSV, JSON, HTML (with embedded charts option)
- 🔧 **Real-time system metrics** (via OSHI):
  - CPU usage (%)
  - RAM usage (%)
  - Disk utilization (%)
  - Disk temperature (when available)
- 🔒 **HTML escaping** for XSS prevention
- 🌍 **Locale-independent** number formatting (Locale.ROOT)
- 📊 **UTF-8 encoding** for all reports

### Technical Improvements
- ✅ **Swing threading** - All UI updates via `SwingUtilities.invokeLater()`
- ✅ **Thread safety** - Single Random instance per session
- ✅ **Proper resource cleanup** - `ExecutorService` with timeout and `awaitTermination()`
- ✅ **Comprehensive logging** - SLF4J with Logback
- ✅ **Input validation** - All user inputs validated before processing
- ✅ **Specific exceptions** - Proper exception handling (not generic catch-all)
- ✅ **Finally block cleanup** - Guaranteed resource cleanup

---

## Requirements
- Java 17+ (tested with JDK 17 and JDK 21)
- Maven 3.6+ for building
- Optional: WiX Toolset v3.14+ (for MSI installer on Windows)

---

## Dependencies
- **Apache Commons CLI** - Command line argument parsing
- **Apache Commons Text** - HTML escaping
- **Jackson** - JSON serialization
- **OpenCSV** - CSV generation
- **OSHI** - System metrics collection (CPU, RAM, Disk)
- **SLF4J + Logback** - Logging framework
- **JUnit 5 + AssertJ** - Testing framework

---

## Build Instructions

### 1. Compile and package
```bash
mvn clean package
```
This creates the JAR file in `target/`.

### 2. Run tests
```bash
mvn test
```
Runs 147+ unit and integration tests.

### 3. Run with code quality checks
```bash
mvn clean verify
```
Includes Checkstyle and SpotBugs analysis.

---

## Usage

### GUI Mode
```bash
java -jar target/jstoragemark-1.0-SNAPSHOT.jar
# Or specify the GUI main class explicitly
java -cp target/jstoragemark-1.0-SNAPSHOT.jar com.kira.jstoragemark.gui.BenchmarkUI
```

### CLI Mode
```bash
# Minimal example
java -cp target/jstoragemark-1.0-SNAPSHOT.jar com.kira.jstoragemark.cli.Main \
  -d ./benchmark-tests \
  -t SEQ_WRITE \
  -s 1073741824 \
  -n 4 \
  -i 3

# Full example with all options
java -cp target/jstoragemark-1.0-SNAPSHOT.jar com.kira.jstoragemark.cli.Main \
  -d ./benchmark-tests \
  -t SEQ_WRITE,SEQ_READ,RAND_WRITE,RAND_READ \
  -s 5368709120 \
  -b 65536 \
  -n 4 \
  -i 5 \
  -q 8 \
  -v 2 \
  -html \
  -r
```

### CLI Arguments
| Option | Long | Description | Default |
|--------|------|-------------|---------|
| `-d` | `--directory` | Test directory path | `./jstoragemark-tests` |
| `-t` | `--test` | Test types (comma-separated) | `SEQ_READ,SEQ_WRITE` |
| `-s` | `--size` | File size in bytes | `5368709120` (5 GB) |
| `-b` | `--block` | Block size in bytes | `131072` (128 KB) |
| `-n` | `--threads` | Number of threads | `4` |
| `-i` | `--iterations` | Number of iterations | `5` |
| `-q` | `--queue` | Queue depth | `8` |
| `-v` | `--verbosity` | Verbosity (0-2) | `1` |
| `-r` | `--retain` | Retain test files after run | `false` |
| `-html` | `--htmlReport` | Generate HTML report | `false` |

---

## Create Executable (Windows)

Using `jpackage`:
```powershell
jpackage --input target `
         --name JStorageMark `
         --main-jar jstoragemark-1.0-SNAPSHOT.jar `
         --main-class com.kira.jstoragemark.gui.BenchmarkUI `
         --type app-image
```

For MSI installer:
```powershell
jpackage --input target `
         --name JStorageMark `
         --main-jar jstoragemark-1.0-SNAPSHOT.jar `
         --main-class com.kira.jstoragemark.gui.BenchmarkUI `
         --type msi `
         --win-menu `
         --win-shortcut
```

---

## Example Results

### Console Output
```
Starting JStorageMark benchmark...
Test directory: ./benchmark-tests
Test types: [SEQ_WRITE, SEQ_READ]
File size: 5.00 GB
Threads: 4
Iterations: 5

=== Benchmark Results ===
Total time: 45.23 seconds
Total runs: 10
Average throughput: 234.56 MB/s
Average latency: 4.25 ms
Reports saved in: ./benchmark-tests
```

### CSV Report
```csv
RunId,TestType,BytesProcessed,ElapsedMs,ThroughputMBps,AvgLatencyMs,IOPS,Timestamp
1,SEQ_WRITE,5368709120,2345,2183.50,0.02,111634.33,2024-01-15T10:30:00Z
2,SEQ_WRITE,5368709120,2201,2326.33,0.02,118947.67,2024-01-15T10:30:02Z
...
```

### JSON Report Structure
```json
{
  "results": [
    {
      "runId": 1,
      "testType": "SEQ_WRITE",
      "bytesProcessed": 5368709120,
      "elapsedMs": 2345,
      "throughputMBps": 2183.50,
      "avgLatencyMs": 0.02,
      "iops": 111634.33,
      "timestamp": "2024-01-15T10:30:00Z"
    }
  ],
  "metrics": [
    {
      "timestamp": "2024-01-15T10:30:00Z",
      "cpuUsagePercent": 45.5,
      "ramUsagePercent": 60.0,
      "diskUtilizationPercent": 30.0
    }
  ],
  "sessionId": "jsm-abc123def456"
}
```

---

## Test Coverage

The project includes comprehensive test coverage (147+ tests):

| Test Category | Files | Description |
|--------------|-------|-------------|
| Unit Tests | `BenchmarkConfigTest`, `BenchmarkResultTest`, `MetricsSnapshotTest` | Data classes, validation |
| FS Tests | `BenchmarkPathsTest` | File system operations |
| Report Tests | `ReportGeneratorTest` | CSV, JSON, HTML generation |
| Runner Tests | `BenchmarkRunnerTest`, `BenchmarkRunnerUnitTest` | Core benchmark logic |
| CLI Tests | `MainCLITest` | Command line parsing |
| Integration | `MainIntegrationTest` | Full workflows |
| End-to-End | `EndToEndTest` | Complete user scenarios |

Run tests with:
```bash
mvn test
```

---

## Code Quality

The project uses:
- **Checkstyle** (Google Java Style) - `mvn checkstyle:check`
- **SpotBugs** - `mvn spotbugs:check`
- **JUnit 5** for testing
- **AssertJ** for fluent assertions

---

## Project Structure
```
jstoragemark/
├── src/
│   ├── main/java/com/kira/jstoragemark/
│   │   ├── cli/           # Command-line interface
│   │   ├── config/        # Configuration classes
│   │   ├── core/          # Benchmark runner
│   │   ├── fs/            # File system utilities
│   │   ├── gui/           # Swing GUI
│   │   ├── metrics/       # System metrics (OSHI)
│   │   ├── report/        # Report generators
│   │   └── result/        # Result data classes
│   ├── test/java/         # Test classes (mirrors main)
│   └── main/resources/    # Logback configuration
├── pom.xml                # Maven configuration
├── .gitignore            # Git ignore rules
└── README.md             # This file
```

---

## License
[Your License Here]

---

## Contributing
Contributions are welcome! Please ensure:
1. Code follows Google Java Style (Checkstyle)
2. All tests pass (`mvn test`)
3. New features include tests
4. SpotBugs reports no issues
