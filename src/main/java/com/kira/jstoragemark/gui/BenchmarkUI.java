package com.kira.jstoragemark.gui;

import com.kira.jstoragemark.config.BenchmarkConfig;
import com.kira.jstoragemark.fs.BenchmarkPaths;
import com.kira.jstoragemark.core.BenchmarkRunner;
import com.kira.jstoragemark.report.ReportGenerator;
import com.kira.jstoragemark.result.BenchmarkResult;
import com.kira.jstoragemark.metrics.MetricsSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.Toolkit;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.List;
import java.util.Locale;
import java.util.Vector;

public class BenchmarkUI {
    private static final Logger logger = LoggerFactory.getLogger(BenchmarkUI.class);

    public static void main(String[] args) {
        SwingUtilities.invokeLater(BenchmarkUI::createWindow);
    }

    private static void createWindow() {
        JFrame frame = new JFrame("JStorageMark");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(950, 700);
        frame.setLayout(new BorderLayout(10, 10));

        // Settings panel
        JPanel inputPanel = new JPanel(new GridLayout(0, 2, 10, 10));
        inputPanel.setBorder(BorderFactory.createTitledBorder("Benchmark Configuration"));

        JTextField dirField = new JTextField("./jstoragemark-tests");
        JTextField sizeField = new JTextField("5368709120"); // 5 GB
        JTextField blockField = new JTextField("65536");      // 64 KB
        JTextField threadsField = new JTextField("4");
        JTextField iterationsField = new JTextField("5");
        JTextField queueField = new JTextField("8");

        // Multiple test type selection using checkboxes
        JCheckBox seqReadBox = new JCheckBox("SEQ_READ", true);
        JCheckBox seqWriteBox = new JCheckBox("SEQ_WRITE", true);
        JCheckBox randReadBox = new JCheckBox("RAND_READ");
        JCheckBox randWriteBox = new JCheckBox("RAND_WRITE");
        
        JPanel testTypePanel = new JPanel(new GridLayout(2, 2));
        testTypePanel.add(seqReadBox);
        testTypePanel.add(seqWriteBox);
        testTypePanel.add(randReadBox);
        testTypePanel.add(randWriteBox);

        JCheckBox htmlReportBox = new JCheckBox("Generate HTML report");

        JButton runButton = new JButton("Run Benchmark");
        JButton copyButton = new JButton("Copy Results");
        JButton clearButton = new JButton("Clear Results");

        inputPanel.add(new JLabel("Test Directory:"));
        inputPanel.add(dirField);
        inputPanel.add(new JLabel("Test Types:"));
        inputPanel.add(testTypePanel);
        inputPanel.add(new JLabel("File Size (bytes):"));
        inputPanel.add(sizeField);
        inputPanel.add(new JLabel("Block Size (bytes):"));
        inputPanel.add(blockField);
        inputPanel.add(new JLabel("Threads:"));
        inputPanel.add(threadsField);
        inputPanel.add(new JLabel("Iterations:"));
        inputPanel.add(iterationsField);
        inputPanel.add(new JLabel("Queue Depth:"));
        inputPanel.add(queueField);
        inputPanel.add(new JLabel(""));
        inputPanel.add(htmlReportBox);
        
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttonPanel.add(runButton);
        buttonPanel.add(copyButton);
        buttonPanel.add(clearButton);
        inputPanel.add(new JLabel(""));
        inputPanel.add(buttonPanel);

        // Table for results
        String[] columnNames = {"RunId", "TestType", "Throughput (MB/s)", "Latency (ms)", "IOPS"};
        DefaultTableModel tableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        JTable resultTable = new JTable(tableModel);
        resultTable.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        JScrollPane scrollPane = new JScrollPane(resultTable);

        // Progress bar with label
        JPanel progressPanel = new JPanel(new BorderLayout());
        JProgressBar progressBar = new JProgressBar();
        progressBar.setStringPainted(true);
        progressBar.setVisible(false);
        JLabel progressLabel = new JLabel("Ready");
        progressPanel.add(progressLabel, BorderLayout.NORTH);
        progressPanel.add(progressBar, BorderLayout.CENTER);

        frame.add(inputPanel, BorderLayout.NORTH);
        frame.add(scrollPane, BorderLayout.CENTER);
        frame.add(progressPanel, BorderLayout.SOUTH);
        frame.setVisible(true);

        // Launch logic
        runButton.addActionListener(e -> {
            try {
                // Validate inputs
                Path testDir = validateAndGetPath(dirField.getText());
                long fileSize = validateFileSize(sizeField.getText());
                int blockSize = validateBlockSize(blockField.getText());
                int threads = validateThreads(threadsField.getText());
                int iterations = validateIterations(iterationsField.getText());
                int queueDepth = validateQueueDepth(queueField.getText());

                // Collect selected test types
                java.util.Set<BenchmarkConfig.TestType> testTypes = new java.util.LinkedHashSet<>();
                if (seqReadBox.isSelected()) testTypes.add(BenchmarkConfig.TestType.SEQ_READ);
                if (seqWriteBox.isSelected()) testTypes.add(BenchmarkConfig.TestType.SEQ_WRITE);
                if (randReadBox.isSelected()) testTypes.add(BenchmarkConfig.TestType.RAND_READ);
                if (randWriteBox.isSelected()) testTypes.add(BenchmarkConfig.TestType.RAND_WRITE);

                if (testTypes.isEmpty()) {
                    JOptionPane.showMessageDialog(frame, "Please select at least one test type",
                            "Configuration Error", JOptionPane.WARNING_MESSAGE);
                    return;
                }

                // Update UI
                runButton.setEnabled(false);
                copyButton.setEnabled(false);
                clearButton.setEnabled(false);
                progressBar.setVisible(true);
                progressBar.setIndeterminate(false);
                progressBar.setValue(0);
                progressLabel.setText("Initializing...");

                SwingWorker<List<BenchmarkResult>, String> worker = new SwingWorker<List<BenchmarkResult>, String>() {
                    @Override
                    protected List<BenchmarkResult> doInBackground() throws IOException, InterruptedException, IllegalArgumentException {
                        try {
                            BenchmarkConfig.Builder builder = new BenchmarkConfig.Builder()
                                    .testDirectory(testDir)
                                    .testTypes(testTypes)
                                    .fileSizeBytes(fileSize)
                                    .blockSizeBytes(blockSize)
                                    .threads(threads)
                                    .iterations(iterations)
                                    .queueDepth(queueDepth);

                            if (htmlReportBox.isSelected()) {
                                builder.addReportFormat(BenchmarkConfig.ReportFormat.HTML);
                                builder.embedCharts(true);
                            }

                            builder.addReportFormat(BenchmarkConfig.ReportFormat.CSV);
                            builder.addReportFormat(BenchmarkConfig.ReportFormat.JSON);

                            BenchmarkConfig config = builder.build();
                            BenchmarkPaths paths = new BenchmarkPaths(config.getTestDirectory(), 
                                    config.getSessionId());

                            BenchmarkRunner runner = new BenchmarkRunner(config, paths);
                            runner.startMetricsPolling();

                            // Calculate total operations
                            int totalRuns = testTypes.size() * iterations;
                            int completedRuns = 0;

                            SwingUtilities.invokeLater(() -> 
                                progressLabel.setText(String.format("Running %d benchmark(s)...", totalRuns)));

                            List<BenchmarkResult> results = runner.runAll();
                            List<MetricsSnapshot> metrics = runner.getMetricsLog();

                            // Update progress to 90%
                            SwingUtilities.invokeLater(() -> {
                                progressBar.setValue(90);
                                progressLabel.setText("Generating reports...");
                            });

                            ReportGenerator generator = new ReportGenerator(config, paths);
                            generator.writeCsv(results);
                            generator.writeJson(results, metrics);
                            generator.writeHtml(results, metrics);

                            // Update progress to 100%
                            SwingUtilities.invokeLater(() -> {
                                progressBar.setValue(100);
                                progressLabel.setText("Completed successfully!");
                                logger.info("Benchmark completed. Reports saved in: {}", config.getTestDirectory());
                            });

                            return results;
                        } catch (IllegalArgumentException ex) {
                            throw ex;
                        } catch (IOException ex) {
                            logger.error("IO error during benchmark", ex);
                            throw new IOException("IO error: " + ex.getMessage(), ex);
                        } catch (InterruptedException ex) {
                            logger.error("Benchmark interrupted", ex);
                            throw ex;
                        }
                    }

                    @Override
                    protected void process(List<String> chunks) {
                        // Update progress incrementally
                        for (String message : chunks) {
                            progressLabel.setText(message);
                        }
                    }

                    @Override
                    protected void done() {
                        try {
                            List<BenchmarkResult> results = get();
                            
                            // Clear table
                            tableModel.setRowCount(0);

                            // Fill table with results
                            for (BenchmarkResult r : results) {
                                Vector<Object> row = new Vector<>();
                                row.add(r.getRunId());
                                row.add(r.getTestType());
                                row.add(String.format(Locale.ROOT, "%.2f", r.getThroughputMBps()));
                                row.add(String.format(Locale.ROOT, "%.2f", r.getAvgLatencyMs()));
                                row.add(String.format(Locale.ROOT, "%.2f", r.getIops()));
                                tableModel.addRow(row);
                            }

                            // Add average row
                            if (!results.isEmpty()) {
                                double avgThroughput = results.stream()
                                        .mapToDouble(BenchmarkResult::getThroughputMBps)
                                        .average()
                                        .orElse(0);
                                double avgLatency = results.stream()
                                        .mapToDouble(BenchmarkResult::getAvgLatencyMs)
                                        .average()
                                        .orElse(0);
                                double avgIops = results.stream()
                                        .mapToDouble(BenchmarkResult::getIops)
                                        .average()
                                        .orElse(0);

                                Vector<Object> avgRow = new Vector<>();
                                avgRow.add("AVG");
                                avgRow.add("-");
                                avgRow.add(String.format(Locale.ROOT, "%.2f", avgThroughput));
                                avgRow.add(String.format(Locale.ROOT, "%.2f", avgLatency));
                                avgRow.add(String.format(Locale.ROOT, "%.2f", avgIops));
                                tableModel.addRow(avgRow);
                            }

                            copyButton.setEnabled(true);
                            clearButton.setEnabled(true);
                        } catch (java.util.concurrent.ExecutionException ex) {
                            Throwable cause = ex.getCause();
                            if (cause instanceof IllegalArgumentException) {
                                SwingUtilities.invokeLater(() -> {
                                    JOptionPane.showMessageDialog(frame, 
                                        "Configuration Error: " + cause.getMessage(),
                                        "Benchmark Failed", JOptionPane.ERROR_MESSAGE);
                                    progressBar.setVisible(false);
                                    progressLabel.setText("Failed");
                                    logger.error("Configuration error", cause);
                                });
                            } else if (cause instanceof IOException) {
                                SwingUtilities.invokeLater(() -> {
                                    JOptionPane.showMessageDialog(frame, 
                                        "IO Error: " + cause.getMessage(),
                                        "Benchmark Failed", JOptionPane.ERROR_MESSAGE);
                                    progressBar.setVisible(false);
                                    progressLabel.setText("Failed");
                                    logger.error("IO error", cause);
                                });
                            } else {
                                SwingUtilities.invokeLater(() -> {
                                    JOptionPane.showMessageDialog(frame, 
                                        "Error: " + (cause != null ? cause.getMessage() : ex.getMessage()),
                                        "Benchmark Failed", JOptionPane.ERROR_MESSAGE);
                                    progressBar.setVisible(false);
                                    progressLabel.setText("Failed");
                                    logger.error("Benchmark error", ex);
                                });
                            }
                        } catch (InterruptedException ex) {
                            SwingUtilities.invokeLater(() -> {
                                JOptionPane.showMessageDialog(frame, "Benchmark was interrupted",
                                    "Benchmark Interrupted", JOptionPane.WARNING_MESSAGE);
                                progressBar.setVisible(false);
                                progressLabel.setText("Interrupted");
                                Thread.currentThread().interrupt();
                                logger.warn("Benchmark interrupted by user");
                            });
                        } finally {
                            SwingUtilities.invokeLater(() -> {
                                runButton.setEnabled(true);
                            });
                        }
                    }
                };
                worker.execute();
            } catch (IllegalArgumentException ex) {
                JOptionPane.showMessageDialog(frame, "Input Error: " + ex.getMessage(),
                        "Invalid Input", JOptionPane.ERROR_MESSAGE);
                logger.warn("Input validation failed", ex);
            }
        });

        // Copy button
        copyButton.addActionListener(e -> {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < tableModel.getRowCount(); i++) {
                for (int j = 0; j < tableModel.getColumnCount(); j++) {
                    sb.append(tableModel.getValueAt(i, j)).append("\t");
                }
                sb.append("\n");
            }
            Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new StringSelection(sb.toString()), null);
            JOptionPane.showMessageDialog(frame, "Results copied to clipboard!",
                    "Success", JOptionPane.INFORMATION_MESSAGE);
            logger.info("Results copied to clipboard");
        });

        // Clear button
        clearButton.addActionListener(e -> {
            tableModel.setRowCount(0);
            progressBar.setVisible(false);
            progressLabel.setText("Ready");
            logger.debug("Results cleared");
        });
    }

    private static Path validateAndGetPath(String pathStr) throws IllegalArgumentException {
        if (pathStr == null || pathStr.trim().isEmpty()) {
            throw new IllegalArgumentException("Test directory path cannot be empty");
        }
        Path path = Path.of(pathStr.trim());
        if (!Files.exists(path)) {
            throw new IllegalArgumentException("Test directory does not exist: " + pathStr);
        }
        if (!Files.isDirectory(path)) {
            throw new IllegalArgumentException("Path is not a directory: " + pathStr);
        }
        return path;
    }

    private static long validateFileSize(String sizeStr) throws IllegalArgumentException {
        try {
            long size = Long.parseLong(sizeStr.trim());
            if (size <= 0) {
                throw new IllegalArgumentException("File size must be positive");
            }
            if (size < 1024 * 1024) {
                throw new IllegalArgumentException("File size must be at least 1 MB");
            }
            return size;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid file size: " + sizeStr);
        }
    }

    private static int validateBlockSize(String blockStr) throws IllegalArgumentException {
        try {
            int size = Integer.parseInt(blockStr.trim());
            if (size < 512 || size > 16 * 1024 * 1024) {
                throw new IllegalArgumentException("Block size must be between 512 bytes and 16 MB");
            }
            return size;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid block size: " + blockStr);
        }
    }

    private static int validateThreads(String threadStr) throws IllegalArgumentException {
        try {
            int threads = Integer.parseInt(threadStr.trim());
            if (threads < 1 || threads > 128) {
                throw new IllegalArgumentException("Threads must be between 1 and 128");
            }
            return threads;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid thread count: " + threadStr);
        }
    }

    private static int validateIterations(String iterStr) throws IllegalArgumentException {
        try {
            int iter = Integer.parseInt(iterStr.trim());
            if (iter < 1 || iter > 100) {
                throw new IllegalArgumentException("Iterations must be between 1 and 100");
            }
            return iter;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid iteration count: " + iterStr);
        }
    }

    private static int validateQueueDepth(String queueStr) throws IllegalArgumentException {
        try {
            int depth = Integer.parseInt(queueStr.trim());
            if (depth < 1 || depth > 1024) {
                throw new IllegalArgumentException("Queue depth must be between 1 and 1024");
            }
            return depth;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid queue depth: " + queueStr);
        }
    }
}