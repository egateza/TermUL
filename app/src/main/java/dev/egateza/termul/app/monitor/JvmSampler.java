package dev.egateza.termul.app.monitor;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.ThreadMXBean;
import java.util.List;

/**
 * Membaca {@link JvmSample} dari MXBean. CPU proses dihitung dari selisih CPU time antar pemanggilan, jadi hasil
 * pertama NaN. Data CPU dan RAM fisik butuh modul {@code jdk.management}; kalau runtime tidak menyertakannya, nilai
 * itu NaN/-1 dan sisanya tetap jalan. Tidak thread-safe: dipanggil dari satu thread sampler.
 */
final class JvmSampler {

    private static final boolean SUN_OS = ModuleLayer.boot().findModule("jdk.management").isPresent();

    private final MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
    private final ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
    private final List<GarbageCollectorMXBean> gcs = ManagementFactory.getGarbageCollectorMXBeans();
    private final int cpus = Runtime.getRuntime().availableProcessors();
    private long lastCpuTime = -1;
    private long lastWall;

    JvmSample sample() {
        var heap = memory.getHeapMemoryUsage();
        var nonHeap = memory.getNonHeapMemoryUsage();
        long gcCount = 0;
        long gcMillis = 0;
        for (var gc : gcs) {
            gcCount += Math.max(0, gc.getCollectionCount());
            gcMillis += Math.max(0, gc.getCollectionTime());
        }
        double processCpu = Double.NaN;
        double systemCpu = Double.NaN;
        long physicalTotal = -1;
        long physicalFree = -1;
        if (SUN_OS && SunOs.available()) {
            long cpuTime = SunOs.processCpuTime();
            long wall = System.nanoTime();
            if (lastCpuTime >= 0 && cpuTime >= 0) {
                processCpu = cpuRatio(cpuTime - lastCpuTime, wall - lastWall, cpus);
            }
            lastCpuTime = cpuTime;
            lastWall = wall;
            double load = SunOs.cpuLoad();
            systemCpu = load < 0 ? Double.NaN : Math.min(1.0, load);
            physicalTotal = SunOs.totalMemory();
            physicalFree = SunOs.freeMemory();
        }
        return new JvmSample(processCpu, systemCpu, heap.getUsed(), heap.getCommitted(), heap.getMax(),
                nonHeap.getUsed(), threadBean.getThreadCount(), gcCount, gcMillis, physicalTotal, physicalFree);
    }

    /**
     * @param cpuDelta  CPU time proses yang terpakai (ns)
     * @param wallDelta waktu nyata yang berlalu (ns)
     * @return pemakaian CPU 0..1 dari total semua core, atau NaN kalau tidak bisa dihitung
     */
    static double cpuRatio(long cpuDelta, long wallDelta, int cpus) {
        if (wallDelta <= 0 || cpus <= 0 || cpuDelta < 0) {
            return Double.NaN;
        }
        return Math.min(1.0, (double) cpuDelta / ((double) wallDelta * cpus));
    }

    /** Akses {@code com.sun.management}; class ini baru dimuat kalau modulnya ada. */
    private static final class SunOs {
        private static final com.sun.management.OperatingSystemMXBean OS =
                ManagementFactory.getOperatingSystemMXBean() instanceof com.sun.management.OperatingSystemMXBean b
                        ? b : null;

        static boolean available() {
            return OS != null;
        }

        static long processCpuTime() {
            return OS.getProcessCpuTime();
        }

        static double cpuLoad() {
            return OS.getCpuLoad();
        }

        static long totalMemory() {
            return OS.getTotalMemorySize();
        }

        static long freeMemory() {
            return OS.getFreeMemorySize();
        }
    }
}
