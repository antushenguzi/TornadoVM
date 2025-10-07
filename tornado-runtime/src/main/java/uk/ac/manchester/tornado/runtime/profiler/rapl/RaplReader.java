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
    final boolean debug = Boolean.parseBoolean(System.getProperty("tornado.power.cpu.rapl.debug", "false"));
    final String userEnergy = System.getProperty("tornado.power.cpu.rapl.energy");
    final String userMax    = System.getProperty("tornado.power.cpu.rapl.maxrange");

    try {
        Path energy = (userEnergy != null && !userEnergy.isEmpty())
                ? Paths.get(userEnergy)
                : Paths.get("/sys/class/powercap/intel-rapl:0/energy_uj");

        Path maxrng = (userMax != null && !userMax.isEmpty())
                ? Paths.get(userMax)
                : Paths.get("/sys/class/powercap/intel-rapl:0/max_energy_range_uj");

        if (debug) {
            System.out.println("[RAPL-DEBUG] discover energy=" + energy + " maxrange=" + maxrng);
        }

        if (!Files.isReadable(energy)) {
            if (debug) System.out.println("[RAPL-DEBUG] energy not readable: " + energy);
            return Optional.empty();
        }
        if (!Files.isReadable(maxrng)) {
            if (debug) System.out.println("[RAPL-DEBUG] maxrange not readable: " + maxrng);
            return Optional.empty();
        }

        RaplReader rr = new RaplReader(energy, maxrng);

        // 连续读两次确认递增
        long e0 = rr.readEnergyUj();
        Thread.sleep(50);
        long e1 = rr.readEnergyUj();
        if (debug) {
            System.out.println("[RAPL-DEBUG] probe e0=" + e0 + " e1=" + e1 + " inc=" + (e1 - e0));
        }
        if (e1 <= e0) {
            if (debug) System.out.println("[RAPL-DEBUG] energy not increasing, keep anyway (some CPUs tick slower)");
        }

        return Optional.of(rr);
    } catch (Throwable t) {
        if (debug) {
            System.out.println("[RAPL-DEBUG] discover failed: " + t);
            t.printStackTrace(System.out);
        }
        return Optional.empty();
    }
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

