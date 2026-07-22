package com.kira.jstoragemark.metrics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import oshi.SystemInfo;
import oshi.hardware.HardwareAbstractionLayer;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class MetricsCollector implements IMetricsCollector {

    private static final Logger logger = LoggerFactory.getLogger(MetricsCollector.class);

    private final ScheduledExecutorService poller;
    private final long pollIntervalMs;
    private final SystemInfo systemInfo;
    private final HardwareAbstractionLayer hardware;
    private final List<MetricsSnapshot> metricsLog = new CopyOnWriteArrayList<>();

    public MetricsCollector(Duration pollInterval) {
        this.pollIntervalMs = pollInterval.toMillis();
        this.systemInfo = new SystemInfo();
        this.hardware = systemInfo.getHardware();
        this.poller = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "MetricsCollectorPoller");
            t.setDaemon(true);
            return t;
        });
    }

    @Override
    public void start() {
        poller.scheduleAtFixedRate(this::collectMetrics, 0, pollIntervalMs, TimeUnit.MILLISECONDS);
        logger.info("Metrics collection started with interval {} ms", pollIntervalMs);
    }

    private void collectMetrics() {
        try {
            Instant now = Instant.now();

            com.sun.management.OperatingSystemMXBean osBean =
                    (com.sun.management.OperatingSystemMXBean)
                    java.lang.management.ManagementFactory.getOperatingSystemMXBean();
            double cpuLoad = Math.max(0, osBean.getProcessCpuLoad() * 100);

            long totalMemory = hardware.getMemory().getTotal();
            long availableMemory = hardware.getMemory().getAvailable();
            double ramUsage = ((totalMemory - availableMemory) / (double) totalMemory) * 100;

            long diskReads = 0;
            long diskWrites = 0;
            long diskReadBytes = 0;
            long diskWriteBytes = 0;
            try {
                java.util.List<oshi.hardware.HWDiskStore> diskStores = hardware.getDiskStores();
                if (!diskStores.isEmpty()) {
                    oshi.hardware.HWDiskStore disk = diskStores.get(0);
                    diskReads = disk.getReads();
                    diskWrites = disk.getWrites();
                    diskReadBytes = disk.getReadBytes();
                    diskWriteBytes = disk.getWriteBytes();
                }
            } catch (Exception e) {
                logger.debug("Could not retrieve disk counters", e);
            }

            metricsLog.add(new MetricsSnapshot(now, cpuLoad, ramUsage,
                    diskReads, diskWrites, diskReadBytes, diskWriteBytes, null));
        } catch (Exception e) {
            logger.warn("Error collecting metrics", e);
        }
    }

    @Override
    public void stop() {
        poller.shutdown();
        try {
            if (!poller.awaitTermination(5, TimeUnit.SECONDS)) {
                logger.warn("Metrics poller did not terminate, forcing shutdown");
                poller.shutdownNow();
            }
        } catch (InterruptedException e) {
            logger.error("Interrupted while stopping metrics poller", e);
            Thread.currentThread().interrupt();
            poller.shutdownNow();
        }
    }

    @Override
    public List<MetricsSnapshot> getMetricsLog() {
        return Collections.unmodifiableList(metricsLog);
    }
}
