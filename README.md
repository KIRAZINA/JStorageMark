# JStorageMark

## Overview
**JStorageMark** is a Java‑based storage benchmarking tool designed to measure sequential and random read/write performance.  
It provides a simple **Swing GUI** for configuration and execution, generates detailed reports in multiple formats (CSV, JSON, HTML), and displays benchmark results directly in the application window.

---

## Features
- 📊 **Benchmark types**: SEQ_READ, SEQ_WRITE, RAND_READ, RAND_WRITE
- ⚙️ **Configurable parameters**:
    - Test directory
    - File size
    - Block size
    - Threads
    - Iterations
    - Queue depth
- 🖥️ **User interface**:
    - Input fields for parameters
    - Real‑time results table with averages
    - Copy results to clipboard
- 📑 **Reports**:
    - CSV, JSON, HTML (with embedded charts)
- 🚀 **Portable executable**:
    - Built with `jpackage` (Windows `.exe` or `.msi`)

---

## Requirements
- Java 17+ (tested with JDK 17 and JDK 21)
- Maven or Gradle for building
- Optional: WiX Toolset v3.14+ (required for MSI installer packaging on Windows)

---

## Build Instructions
### 1. Compile and package
```bash
mvn clean package
```
This creates the JAR file in `target/`.

### 2. Run directly
```bash
java -jar target/jstoragemark-1.0-SNAPSHOT.jar
```

### 3. Create executable (Windows)
Using `jpackage`:
```powershell
jpackage --input target `
         --name JStorageMark `
         --main-jar jstoragemark-1.0-SNAPSHOT.jar `
         --main-class com.kira.jstoragemark.gui.BenchmarkUI `
         --type app-image
```

---

## Usage
1. Launch the application (`JStorageMark.exe` or via `java -jar`).
2. Configure benchmark parameters in the GUI.
3. Click **Run Benchmark**.
4. View results in the table and copy them if needed.
5. Reports are saved in the selected test directory.

---

## Example Results
```
RunId  TestType   ThroughputMBps   AvgLatencyMs   IOPS
1      SEQ_READ   12132.70         0.01           194123.22
2      SEQ_READ   13128.21         0.00           210051.28
...
AVG    -          12664.23         0.01           202627.63
```
