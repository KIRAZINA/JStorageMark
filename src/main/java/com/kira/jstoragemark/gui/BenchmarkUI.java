package com.kira.jstoragemark.gui;

import com.kira.jstoragemark.config.BenchmarkConfig;
import com.kira.jstoragemark.fs.BenchmarkPaths;
import com.kira.jstoragemark.core.BenchmarkRunner;
import com.kira.jstoragemark.report.ReportGenerator;
import com.kira.jstoragemark.result.BenchmarkResult;
import com.kira.jstoragemark.metrics.MetricsSnapshot;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.Toolkit;
import java.nio.file.Path;
import java.util.List;
import java.util.Vector;

public class BenchmarkUI {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(BenchmarkUI::createWindow);
    }

    private static void createWindow() {
        JFrame frame = new JFrame("JStorageMark");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(900, 600);
        frame.setLayout(new BorderLayout(10, 10));

        // Settings panel
        JPanel inputPanel = new JPanel(new GridLayout(0, 2, 10, 10));

        JTextField dirField = new JTextField("./jstoragemark-tests");
        JTextField sizeField = new JTextField("5368709120"); // 5 GB
        JTextField blockField = new JTextField("65536");      // 64 KB
        JTextField threadsField = new JTextField("4");
        JTextField iterationsField = new JTextField("5");
        JTextField queueField = new JTextField("8");

        JCheckBox htmlReportBox = new JCheckBox("Generate HTML report");
        JComboBox<String> testTypeBox = new JComboBox<>(new String[]{
                "SEQ_READ", "SEQ_WRITE", "RAND_READ", "RAND_WRITE"
        });

        JButton runButton = new JButton("Run Benchmark");
        JButton copyButton = new JButton("Copy Results");

        inputPanel.add(new JLabel("Test Directory:"));
        inputPanel.add(dirField);
        inputPanel.add(new JLabel("Test Type:"));
        inputPanel.add(testTypeBox);
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
        inputPanel.add(runButton);
        inputPanel.add(copyButton);

        // Table for results
        String[] columnNames = {"RunId", "TestType", "ThroughputMBps", "AvgLatencyMs", "IOPS"};
        DefaultTableModel tableModel = new DefaultTableModel(columnNames, 0);
        JTable resultTable = new JTable(tableModel);
        JScrollPane scrollPane = new JScrollPane(resultTable);

        // Progress bar
        JProgressBar progressBar = new JProgressBar();
        progressBar.setStringPainted(true);
        progressBar.setVisible(false);

        frame.add(inputPanel, BorderLayout.NORTH);
        frame.add(scrollPane, BorderLayout.CENTER);
        frame.add(progressBar, BorderLayout.SOUTH);
        frame.setVisible(true);

        // Launch logic
        runButton.addActionListener(e -> {
            SwingWorker<Void, Void> worker = new SwingWorker<>() {
                @Override
                protected Void doInBackground() {
                    try {
                        progressBar.setVisible(true);
                        progressBar.setIndeterminate(true);
                        runButton.setEnabled(false);

                        BenchmarkConfig.Builder builder = new BenchmarkConfig.Builder()
                                .testDirectory(Path.of(dirField.getText()))
                                .addTestType(BenchmarkConfig.TestType.valueOf(testTypeBox.getSelectedItem().toString()))
                                .fileSizeBytes(Long.parseLong(sizeField.getText()))
                                .blockSizeBytes(Integer.parseInt(blockField.getText()))
                                .threads(Integer.parseInt(threadsField.getText()))
                                .iterations(Integer.parseInt(iterationsField.getText()))
                                .queueDepth(Integer.parseInt(queueField.getText()));

                        if (htmlReportBox.isSelected()) {
                            builder.addReportFormat(BenchmarkConfig.ReportFormat.HTML);
                            builder.embedCharts(true);
                        }

                        builder.addReportFormat(BenchmarkConfig.ReportFormat.CSV);
                        builder.addReportFormat(BenchmarkConfig.ReportFormat.JSON);

                        BenchmarkConfig config = builder.build();
                        BenchmarkPaths paths = new BenchmarkPaths(config.getTestDirectory(), config.getSessionId());

                        BenchmarkRunner runner = new BenchmarkRunner(config, paths);
                        runner.startMetricsPolling();

                        List<BenchmarkResult> results = runner.runAll();
                        List<MetricsSnapshot> metrics = runner.getMetricsLog();

                        ReportGenerator generator = new ReportGenerator(config, paths);
                        generator.writeCsv(results);
                        generator.writeJson(results, metrics);
                        generator.writeHtml(results, metrics);

                        // Clear the table before restarting
                        tableModel.setRowCount(0);

                        // Fill in the table with the results
                        for (BenchmarkResult r : results) {
                            Vector<Object> row = new Vector<>();
                            row.add(r.getRunId());
                            row.add(r.getTestType());
                            row.add(String.format("%.2f", r.getThroughputMBps()));
                            row.add(String.format("%.2f", r.getAvgLatencyMs()));
                            row.add(String.format("%.2f", r.getIops()));
                            tableModel.addRow(row);
                        }

                        // Add a row of average values
                        double avgThroughput = results.stream().mapToDouble(BenchmarkResult::getThroughputMBps).average().orElse(0);
                        double avgLatency = results.stream().mapToDouble(BenchmarkResult::getAvgLatencyMs).average().orElse(0);
                        double avgIops = results.stream().mapToDouble(BenchmarkResult::getIops).average().orElse(0);

                        Vector<Object> avgRow = new Vector<>();
                        avgRow.add("AVG");
                        avgRow.add("-");
                        avgRow.add(String.format("%.2f", avgThroughput));
                        avgRow.add(String.format("%.2f", avgLatency));
                        avgRow.add(String.format("%.2f", avgIops));
                        tableModel.addRow(avgRow);

                    } catch (Exception ex) {
                        JOptionPane.showMessageDialog(frame, "Error: " + ex.getMessage(),
                                "Benchmark Failed", JOptionPane.ERROR_MESSAGE);
                    }
                    return null;
                }

                @Override
                protected void done() {
                    progressBar.setIndeterminate(false);
                    progressBar.setVisible(false);
                    runButton.setEnabled(true);
                }
            };
            worker.execute();
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
            JOptionPane.showMessageDialog(frame, "Results copied to clipboard!");
        });
    }
}