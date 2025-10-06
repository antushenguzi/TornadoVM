package uk.ac.manchester.tornado.runtime.profiler.rapl;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Minimal RAPL sysfs reader for Linux (Intel RAPL via powercap).
 * Reads energy_uj and max_energy_range_uj under /sys/devices/virtual/powercap/intel-rapl:*
 */
public final class RaplReader {
    private static final Path POWERCAP = Paths.get("/sys/devices/virtual/powercap");
    private static final String PREFIX = "intel-rapl:";

    private final List<Path> energyFiles = new ArrayList<>();
    private final Map<Path, Path> maxRangeFiles = new HashMap<>();

    private RaplReader() {}

    public static Optional<RaplReader> discover() {
        RaplReader r = new RaplReader();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(POWERCAP)) {
            for (Path g : ds) {
                String name = g.getFileName().toString();
                if (!name.startsWith(PREFIX)) continue;
                r.addIfPresent(g);
                try (DirectoryStream<Path> subs = Files.newDirectoryStream(g)) {
                    for (Path sub : subs) {
                        if (Files.isDirectory(sub) && sub.getFileName().toString().startsWith(PREFIX)) {
                            r.addIfPresent(sub);
                        }
                    }
                } catch (IOException ignore) {}
            }
        } catch (IOException ignore) {}
        return r.energyFiles.isEmpty() ? Optional.empty() : Optional.of(r);
    }

    private void addIfPresent(Path base) {
        Path e = base.resolve("energy_uj");
        if (Files.isReadable(e)) {
            Path abs = e.toAbsolutePath();
            energyFiles.add(abs);
            Path max = base.resolve("max_energy_range_uj");
            if (Files.isReadable(max)) {
                maxRangeFiles.put(abs, max.toAbsolutePath());
            }
        }
    }

    /** Sum energy_uj across discovered domains (micro-joules). */
    public long readEnergyUj() throws IOException {
        long sum = 0L;
        for (Path e : energyFiles) {
            String s = new String(Files.readAllBytes(e)).trim();
            sum += Long.parseLong(s);
        }
        return sum;
    }

    /** Conservative wrap range: minimum max_energy_range_uj across domains. */
    public long minMaxRangeUj() {
        long min = Long.MAX_VALUE;
        for (Path e : energyFiles) {
            Path m = maxRangeFiles.get(e);
            if (m != null && Files.isReadable(m)) {
                try {
                    String s = new String(Files.readAllBytes(m)).trim();
                    long v = Long.parseLong(s);
                    if (v < min) min = v;
                } catch (Exception ignore) {}
            }
        }
        return (min == Long.MAX_VALUE) ? 0L : min;
    }
}
