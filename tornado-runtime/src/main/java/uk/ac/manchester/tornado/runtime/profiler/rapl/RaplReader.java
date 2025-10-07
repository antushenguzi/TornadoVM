/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * GPLv2 only.
 */
package uk.ac.manchester.tornado.runtime.profiler.rapl;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

/**
 * Minimal RAPL reader for Linux powercap sysfs.
 *
 * Provides:
 *  - discover(): locate a readable energy_uj path (prefer "package" domain)
 *  - readEnergyUj(): read current energy in micro-joules
 *  - minMaxRangeUj(): read counter wrap range (max_energy_range_uj), 0 if unknown
 *
 * You may override auto-discovery with system properties:
 *   -Dtornado.power.cpu.rapl.energy=/sys/class/powercap/intel-rapl:0/energy_uj
 *   -Dtornado.power.cpu.rapl.maxrange=/sys/class/powercap/intel-rapl:0/max_energy_range_uj
 */
public final class RaplReader {

    private final Path energyPath;
    private final Path maxRangePath;

    private RaplReader(Path energyPath, Path maxRangePath) {
        this.energyPath = energyPath;
        this.maxRangePath = maxRangePath;
    }

    /**
     * Try to construct a RaplReader either from explicit system properties
     * or by scanning common powercap locations.
     */
    public static Optional<RaplReader> discover() {
        // 1) Respect explicit overrides (very handy for debugging)
        String energyProp = System.getProperty("tornado.power.cpu.rapl.energy");
        if (energyProp != null && !energyProp.isEmpty()) {
            Path e = Paths.get(energyProp);
            if (Files.isReadable(e)) {
                Path mr = null;
                String mrProp = System.getProperty("tornado.power.cpu.rapl.maxrange");
                if (mrProp != null && !mrProp.isEmpty()) {
                    Path p = Paths.get(mrProp);
                    if (Files.isReadable(p)) {
                        mr = p;
                    }
                } else {
                    // Try sibling default
                    Path sibling = e.getParent() != null ? e.getParent().resolve("max_energy_range_uj") : null;
                    if (sibling != null && Files.isReadable(sibling)) {
                        mr = sibling;
                    }
                }
                return Optional.of(new RaplReader(e, mr));
            }
        }

        // 2) Auto-discovery in typical locations
        String[] roots = new String[] {
            "/sys/class/powercap",
            "/sys/devices/virtual/powercap"
        };
        String[] domains = new String[] { "intel-rapl", "intel-rapl-mmio" };

        // First pass: prefer "package" domains (by reading "name")
        for (String root : roots) {
            for (String domPrefix : domains) {
                Optional<RaplReader> pkg = scanOneLevel(root, domPrefix, true);
                if (pkg.isPresent()) {
                    return pkg;
                }
            }
        }

        // Second pass: accept any readable domain with energy_uj
        for (String root : roots) {
            for (String domPrefix : domains) {
                Optional<RaplReader> any = scanOneLevel(root, domPrefix, false);
                if (any.isPresent()) {
                    return any;
                }
            }
        }

        return Optional.empty();
    }

    private static Optional<RaplReader> scanOneLevel(String root, String domPrefix, boolean preferPackage) {
        Path rootPath = Paths.get(root);
        if (!Files.isDirectory(rootPath)) {
            return Optional.empty();
        }
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(rootPath, domPrefix + "*")) {
            Path pickedEnergy = null;
            Path pickedMax = null;
            boolean pickedIsPackage = false;

            for (Path dom : ds) {
                Path energy = dom.resolve("energy_uj");
                if (!Files.isReadable(energy)) {
                    continue;
                }
                boolean isPackage = false;
                Path name = dom.resolve("name");
                if (Files.isReadable(name)) {
                    try {
                        String n = readTrim(name);
                        // "package-0", etc.
                        if (n != null && n.toLowerCase().contains("package")) {
                            isPackage = true;
                        }
                    } catch (Throwable ignore) {
                        // ignore
                    }
                }

                Path maxRange = dom.resolve("max_energy_range_uj");
                if (!Files.isReadable(maxRange)) {
                    maxRange = null; // not fatal
                }

                if (preferPackage) {
                    if (isPackage) {
                        return Optional.of(new RaplReader(energy, maxRange));
                    }
                } else {
                    // pick the first found if nothing chosen yet,
                    // or prefer package if we stumble on it
                    if (pickedEnergy == null || (!pickedIsPackage && isPackage)) {
                        pickedEnergy = energy;
                        pickedMax = maxRange;
                        pickedIsPackage = isPackage;
                    }
                }
            }

            if (!preferPackage && pickedEnergy != null) {
                return Optional.of(new RaplReader(pickedEnergy, pickedMax));
            }
        } catch (IOException | SecurityException ignore) {
            // ignore and continue other roots
        }
        return Optional.empty();
    }

    private static String readTrim(Path p) throws IOException {
        // keep it robust to trailing newlines
        return Files.readString(p, StandardCharsets.US_ASCII).trim();
    }

    /** Current accumulated energy in micro-joules. */
    public long readEnergyUj() throws IOException {
        String s = readTrim(energyPath);
        return Long.parseLong(s);
    }

    /**
     * Counter wrap range (max_energy_range_uj). 0 if unknown.
     * When energy_uj wraps, delta should be:
     *   (wrap - e0) + e1
     */
    public long minMaxRangeUj() {
        try {
            if (maxRangePath != null && Files.isReadable(maxRangePath)) {
                String s = readTrim(maxRangePath);
                return Long.parseLong(s);
            }
        } catch (Throwable ignore) {
            // fall through
        }
        return 0L;
    }

    /** Expose the path (optional, useful for diagnostics). */
    public Path energyFile() {
        return energyPath;
    }
}

