/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2025, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 */
package uk.ac.manchester.tornado.drivers.common.power;

/**
 * Interface for Intel RAPL (Running Average Power Limit) power metrics.
 * RAPL provides energy consumption measurements for Intel CPU packages,
 * cores, and integrated GPUs through MSR (Model Specific Register) access.
 *
 * This interface abstracts RAPL energy readings to allow different backends
 * to provide platform-specific implementations for CPU energy monitoring.
 */
public interface RAPLPowerMetric {

    /**
     * Initialize RAPL library and open energy measurement interfaces.
     * This should handle MSR file descriptor opening or powercap sysfs access.
     *
     * @return true if initialization successful, false otherwise
     */
    boolean initializeRAPL();

    /**
     * Read current energy consumption from RAPL counters.
     *
     * @param energyUsage Array to store energy values in microjoules (μJ).
     *                    Index 0: Package energy (CPU + integrated GPU)
     *                    Index 1: PP0 (cores) energy
     *                    Index 2: PP1 (integrated GPU) energy if available
     *                    Index 3: DRAM energy if available
     * @return true if reading successful, false otherwise
     */
    boolean getEnergyUsage(long[] energyUsage);

    /**
     * Calculate average power consumption between two measurements.
     *
     * @param startEnergy Starting energy measurement in microjoules
     * @param endEnergy Ending energy measurement in microjoules
     * @param durationNs Duration between measurements in nanoseconds
     * @return Average power in milliwatts
     */
    default long calculateAveragePower(long startEnergy, long endEnergy, long durationNs) {
        long energyDiff = endEnergy - startEnergy;
        // Convert μJ to mW: (μJ * 1000) / (ns)
        return (energyDiff * 1_000_000L) / durationNs;
    }

    /**
     * Clean up RAPL resources and close file descriptors.
     */
    void closeRAPL();
}
