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
package uk.ac.manchester.tornado.drivers.opencl.power;

import uk.ac.manchester.tornado.drivers.common.power.PowerMetric;
import uk.ac.manchester.tornado.drivers.opencl.OCLDeviceContext;
import uk.ac.manchester.tornado.drivers.opencl.exceptions.OCLException;
import uk.ac.manchester.tornado.runtime.common.TornadoLogger;

/**
 * Intel RAPL (Running Average Power Limit) power metric handler for OpenCL devices.
 *
 * This handler provides CPU energy consumption monitoring through Intel's RAPL interface.
 * RAPL energy counters are accessed via:
 * - MSR (Model Specific Registers) at /dev/cpu/N/msr (requires root or msr module)
 * - Linux powercap sysfs interface at /sys/class/powercap/intel-rapl/ (preferred)
 *
 * Power is derived from energy delta over time: Power(mW) = ΔEnergy(μJ) / Δtime(μs)
 */
public class OCLIntelRAPLPowerMetricHandler implements PowerMetric {

    private final OCLDeviceContext deviceContext;
    private final TornadoLogger logger;
    private boolean isInitialized;
    private long previousEnergy;
    private long previousTimestamp;

    public OCLIntelRAPLPowerMetricHandler(OCLDeviceContext deviceContext) {
        this.deviceContext = deviceContext;
        this.logger = new TornadoLogger(this.getClass());
        this.isInitialized = false;
        initializePowerLibrary();
    }

    /**
     * Initialize RAPL interface via JNI.
     * Attempts to open powercap sysfs or MSR interface.
     */
    static native long clRAPLInit() throws OCLException;

    /**
     * Read RAPL energy counters.
     *
     * @param energyUsage Array to receive energy values in microjoules:
     *                    [0] = Package energy (total CPU + iGPU)
     *                    [1] = PP0 (cores) energy
     *                    [2] = PP1 (integrated GPU) energy
     *                    [3] = DRAM energy
     * @return Status code (0 = success)
     * @throws OCLException if reading fails
     */
    static native long clRAPLReadEnergy(long[] energyUsage) throws OCLException;

    /**
     * Close RAPL interface and release resources.
     */
    static native void clRAPLClose() throws OCLException;

    @Override
    public void initializePowerLibrary() {
        try {
            long result = clRAPLInit();
            if (result == 0) {
                isInitialized = true;
                previousTimestamp = System.nanoTime();
                // Initialize baseline energy measurement
                long[] initialEnergy = new long[4];
                clRAPLReadEnergy(initialEnergy);
                previousEnergy = initialEnergy[0]; // Use package energy
                logger.info("Intel RAPL initialized successfully for device: " + deviceContext.getDeviceName());
            } else {
                logger.warn("Intel RAPL initialization failed with code: " + result +
                           ". Power monitoring disabled for " + deviceContext.getDeviceName());
            }
        } catch (OCLException e) {
            logger.error("Failed to initialize Intel RAPL: " + e.getMessage());
            isInitialized = false;
        } catch (UnsatisfiedLinkError e) {
            logger.error("RAPL native library not available: " + e.getMessage());
            isInitialized = false;
        }
    }

    @Override
    public void getPowerUsage(long[] powerUsage) {
        if (!isInitialized) {
            powerUsage[0] = 0; // Return 0 if not initialized
            return;
        }

        try {
            long currentTimestamp = System.nanoTime();
            long[] energyUsage = new long[4];
            clRAPLReadEnergy(energyUsage);

            // Calculate power from energy difference
            long currentEnergy = energyUsage[0]; // Package energy in μJ
            long energyDelta = currentEnergy - previousEnergy;
            long timeDelta = currentTimestamp - previousTimestamp; // in nanoseconds

            // Convert to milliwatts: Power(mW) = ΔEnergy(μJ) × 1,000,000 / Δtime(ns)
            if (timeDelta > 0) {
                powerUsage[0] = (energyDelta * 1_000_000L) / timeDelta;
            } else {
                powerUsage[0] = 0;
            }

            // Update previous values for next measurement
            previousEnergy = currentEnergy;
            previousTimestamp = currentTimestamp;

        } catch (OCLException e) {
            logger.error("Failed to read RAPL energy: " + e.getMessage());
            powerUsage[0] = 0;
        }
    }

    /**
     * Cleanup RAPL resources. Should be called when device context is destroyed.
     */
    public void cleanup() {
        if (isInitialized) {
            try {
                clRAPLClose();
                isInitialized = false;
                logger.debug("Intel RAPL closed for device: " + deviceContext.getDeviceName());
            } catch (OCLException e) {
                logger.error("Failed to close Intel RAPL: " + e.getMessage());
            }
        }
    }
}
