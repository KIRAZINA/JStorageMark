package com.kira.jstoragemark.gui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Vector;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableModel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.kira.jstoragemark.config.AppConstants;
import com.kira.jstoragemark.config.BenchmarkConfig;
import com.kira.jstoragemark.core.BenchmarkRunner;
import com.kira.jstoragemark.fs.BenchmarkPaths;
import com.kira.jstoragemark.metrics.MetricsSnapshot;
import com.kira.jstoragemark.report.ReportGenerator;
import com.kira.jstoragemark.result.BenchmarkResult;

public class BenchmarkUI {
    private static final Logger logger = LoggerFactory.getLogger(BenchmarkUI.class);

    public static void main(String[] args) {
        SwingUtilities.invokeLater(BenchmarkUI::createWindow);
    }

    private static void createWindow() {
        JFrame frame = new JFrame("JStorageMark");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1200, 800);
        frame.setMinimumSize(new Dimension(800, 600));
        frame.setLayout(new BorderLayout(10, 10));

        JPanel inputPanel = new JPanel(new GridBagLayout());
        inputPanel.setBorder(BorderFactory.createTitledBorder("Benchmark Configuration"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JTextField dirField = new JTextField(AppConstants.DEFAULT_TEST_DIR, 20);
        JTextField sizeField = new JTextField("5G", 15);
        JTextField blockField = new JTextField("65536", 10);
        JTextField threadsField = new JTextField("4", 5);
        JTextField iterationsField = new JTextField("5", 5);
        JTextField queueField = new JTextField("8", 5);

        JCheckBox seqReadBox = new JCheckBox("SEQ_READ", true);
        JCheckBox seqWriteBox = new JCheckBox("SEQ_WRITE", true);
        JCheckBox randReadBox = new JCheckBox("RAND_READ");
        JCheckBox randWriteBox = new JCheckBox("RAND_WRITE");

        seqReadBox.setOpaque(false);
        seqWriteBox.setOpaque(false);
        randReadBox.setOpaque(false);
        randWriteBox.setOpaque(false);

        JPanel testTypePanel = new JPanel(new GridLayout(2, 2, 10, 10));
        testTypePanel.setBorder(BorderFactory.createEmptyBorder());
        testTypePanel.add(seqReadBox);
        testTypePanel.add(seqWriteBox);
        testTypePanel.add(randReadBox);
        testTypePanel.add(randWriteBox);

        JCheckBox htmlReportBox = new JCheckBox("Generate HTML report");

        JCheckBox forceSyncBox = new JCheckBox("Force sync after writes", true);
        JTextField syncEveryField = new JTextField("0", 10);
        JCheckBox preallocateBox = new JCheckBox("Preallocate files", true);
        JCheckBox directBufferBox = new JCheckBox("Use direct buffer", true);

        forceSyncBox.setOpaque(false);
        preallocateBox.setOpaque(false);
        directBufferBox.setOpaque(false);

        JButton runButton = new JButton("Run Benchmark");
        JButton copyButton = new JButton("Copy Results");
        JButton clearButton = new JButton("Clear Results");

        gbc.gridx = 0; gbc.gridy = 0;
        inputPanel.add(new JLabel("Test Directory:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        inputPanel.add(dirField, gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0;
        inputPanel.add(new JLabel("Test Types:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        inputPanel.add(testTypePanel, gbc);

        gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 0;
        inputPanel.add(new JLabel("File Size (bytes):"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        inputPanel.add(sizeField, gbc);

        gbc.gridx = 0; gbc.gridy = 3; gbc.weightx = 0;
        inputPanel.add(new JLabel("Block Size (bytes):"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        inputPanel.add(blockField, gbc);

        gbc.gridx = 0; gbc.gridy = 4; gbc.weightx = 0;
        inputPanel.add(new JLabel("Threads:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        inputPanel.add(threadsField, gbc);

        gbc.gridx = 0; gbc.gridy = 5; gbc.weightx = 0;
        inputPanel.add(new JLabel("Iterations:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        inputPanel.add(iterationsField, gbc);

        gbc.gridx = 0; gbc.gridy = 6; gbc.weightx = 0;
        inputPanel.add(new JLabel("Queue Depth:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        inputPanel.add(queueField, gbc);

        gbc.gridx = 0; gbc.gridy = 7; gbc.weightx = 0;
        inputPanel.add(new JLabel(""), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        inputPanel.add(htmlReportBox, gbc);

        gbc.gridx = 0; gbc.gridy = 8; gbc.weightx = 0;
        inputPanel.add(new JLabel("Force Sync:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        inputPanel.add(forceSyncBox, gbc);

        gbc.gridx = 0; gbc.gridy = 9; gbc.weightx = 0;
        inputPanel.add(new JLabel("Sync Every N Blocks:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        inputPanel.add(syncEveryField, gbc);

        gbc.gridx = 0; gbc.gridy = 10; gbc.weightx = 0;
        inputPanel.add(new JLabel("Preallocate Files:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        inputPanel.add(preallocateBox, gbc);

        gbc.gridx = 0; gbc.gridy = 11; gbc.weightx = 0;
        inputPanel.add(new JLabel("Buffer Type:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        inputPanel.add(directBufferBox, gbc);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttonPanel.add(runButton);
        buttonPanel.add(copyButton);
        buttonPanel.add(clearButton);

        gbc.gridx = 0; gbc.gridy = 12; gbc.weightx = 0;
        inputPanel.add(new JLabel(""), gbc);
        gbc.gridx = 1; gbc.gridy = 12; gbc.weightx = 1.0;
        inputPanel.add(buttonPanel, gbc);

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

        runButton.addActionListener(e -> {
            try {
                Path testDir = validateAndGetPath(dirField.getText());
                long fileSize = validateFileSize(sizeField.getText());
                int blockSize = validateBlockSize(blockField.getText());
                int threads = validateThreads(threadsField.getText());
                int iterations = validateIterations(iterationsField.getText());
                int queueDepth = validateQueueDepth(queueField.getText());
                int syncEvery = validateSyncEvery(syncEveryField.getText());

                if (queueDepth > threads * 2) {
                    queueDepth = threads * 2;
                }
                final int effectiveQueueDepth = queueDepth;

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
                                    .queueDepth(effectiveQueueDepth)
                                    .forceSync(forceSyncBox.isSelected())
                                    .syncEveryNBlocks(syncEvery)
                                    .preallocateFiles(preallocateBox.isSelected())
                                    .useDirectBuffer(directBufferBox.isSelected());

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

                            int totalRuns = testTypes.size() * iterations;

                            SwingUtilities.invokeLater(() ->
                                progressLabel.setText(String.format("Running %d benchmark(s)...", totalRuns)));

                            List<BenchmarkResult> results = runner.runAll();
                            List<MetricsSnapshot> metrics = runner.getMetricsLog();

                            SwingUtilities.invokeLater(() -> {
                                progressBar.setValue(90);
                                progressLabel.setText("Generating reports...");
                            });

                            ReportGenerator generator = new ReportGenerator(config, paths);
                            generator.writeCsv(results);
                            generator.writeJson(results, metrics, runner.getSystemInfo());
                            generator.writeHtml(results, metrics, runner.getSystemInfo());

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
                        for (String message : chunks) {
                            progressLabel.setText(message);
                        }
                    }

                    @Override
                    protected void done() {
                        try {
                            List<BenchmarkResult> results = get();

                            tableModel.setRowCount(0);

                            for (BenchmarkResult r : results) {
                                Vector<Object> row = new Vector<>();
                                row.add(r.runId());
                                row.add(r.testType());
                                row.add(String.format(Locale.ROOT, "%.2f", r.throughputMBps()));
                                row.add(String.format(Locale.ROOT, "%.2f", r.avgLatencyMs()));
                                row.add(String.format(Locale.ROOT, "%.2f", r.iops()));
                                tableModel.addRow(row);
                            }

                            if (!results.isEmpty()) {
                                double avgThroughput = results.stream()
                                        .mapToDouble(BenchmarkResult::throughputMBps)
                                        .average()
                                        .orElse(0);
                                double avgLatency = results.stream()
                                        .mapToDouble(BenchmarkResult::avgLatencyMs)
                                        .average()
                                        .orElse(0);
                                double avgIops = results.stream()
                                        .mapToDouble(BenchmarkResult::iops)
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

    private static int validateSyncEvery(String syncStr) throws IllegalArgumentException {
        try {
            int sync = Integer.parseInt(syncStr.trim());
            if (sync < 0 || sync > 10000) {
                throw new IllegalArgumentException("Sync every must be between 0 and 10000");
            }
            return sync;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid sync every value: " + syncStr);
        }
    }

    private static long validateFileSize(String sizeStr) throws IllegalArgumentException {
        try {
            String input = sizeStr.trim().toUpperCase(Locale.ROOT);
            long multiplier = 1L;
            String numPart = input;
            if (input.endsWith("T")) {
                multiplier = 1024L * 1024 * 1024 * 1024;
                numPart = input.substring(0, input.length() - 1);
            } else if (input.endsWith("G")) {
                multiplier = 1024L * 1024 * 1024;
                numPart = input.substring(0, input.length() - 1);
            } else if (input.endsWith("M")) {
                multiplier = 1024L * 1024;
                numPart = input.substring(0, input.length() - 1);
            } else if (input.endsWith("K")) {
                multiplier = 1024L;
                numPart = input.substring(0, input.length() - 1);
            }
            long size = Long.parseLong(numPart.trim()) * multiplier;
            long minBytes = 1L * 1024 * 1024 * 1024;
            long maxBytes = 10L * 1024 * 1024 * 1024;
            if (size < minBytes || size > maxBytes) {
                throw new IllegalArgumentException("File size must be between 1 GB and 10 GB");
            }
            return size;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid file size: " + sizeStr);
        }
    }

    private static int validateBlockSize(String blockStr) throws IllegalArgumentException {
        try {
            int size = Integer.parseInt(blockStr.trim());
            if (size < 4 * 1024 || size > 1 * 1024 * 1024) {
                throw new IllegalArgumentException("Block size must be between 4 KB and 1 MB");
            }
            return size;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid block size: " + blockStr);
        }
    }

    private static int validateThreads(String threadStr) throws IllegalArgumentException {
        try {
            int threads = Integer.parseInt(threadStr.trim());
            if (threads < 1 || threads > 32) {
                throw new IllegalArgumentException("Threads must be between 1 and 32");
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
