/*
 * MIT License
 *
 * Copyright (c) 2025, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
#include <jni.h>
#include <iostream>
#include <fstream>
#include <string>
#include <cstring>
#include <vector>

#ifdef RAPL_IS_SUPPORTED
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <unistd.h>
#include <dirent.h>
#endif

#include "OCLIntelRAPLPowerMetricHandler.h"
#include "ocl_log.h"

// RAPL MSR register addresses for Intel CPUs
#define MSR_RAPL_POWER_UNIT         0x606
#define MSR_PKG_ENERGY_STATUS       0x611
#define MSR_PP0_ENERGY_STATUS       0x639
#define MSR_PP1_ENERGY_STATUS       0x641
#define MSR_DRAM_ENERGY_STATUS      0x619

// Linux powercap sysfs paths
#define POWERCAP_BASE_PATH          "/sys/class/powercap/intel-rapl"
#define POWERCAP_PACKAGE_PATTERN    "intel-rapl:"
#define POWERCAP_ENERGY_FILE        "energy_uj"

#ifdef RAPL_IS_SUPPORTED
// Global state for RAPL interface
static bool rapl_initialized = false;
static bool use_powercap = false;  // true = use sysfs powercap, false = use MSR
static std::vector<std::string> rapl_package_paths;
static int msr_fd = -1;
static double energy_unit = 0.0;

/**
 * Read 64-bit value from MSR
 */
static bool read_msr(int cpu, uint32_t msr, uint64_t *value) {
    char msr_path[256];
    snprintf(msr_path, sizeof(msr_path), "/dev/cpu/%d/msr", cpu);

    int fd = open(msr_path, O_RDONLY);
    if (fd < 0) {
        std::cerr << "[RAPL] Failed to open MSR file: " << msr_path << std::endl;
        return false;
    }

    if (pread(fd, value, sizeof(*value), msr) != sizeof(*value)) {
        std::cerr << "[RAPL] Failed to read MSR 0x" << std::hex << msr << std::endl;
        close(fd);
        return false;
    }

    close(fd);
    return true;
}

/**
 * Initialize RAPL using Linux powercap sysfs interface (preferred)
 */
static bool init_powercap() {
    DIR *dir = opendir(POWERCAP_BASE_PATH);
    if (!dir) {
        std::cerr << "[RAPL] Powercap sysfs not available at " << POWERCAP_BASE_PATH << std::endl;
        return false;
    }

    struct dirent *entry;
    while ((entry = readdir(dir)) != nullptr) {
        std::string name(entry->d_name);
        if (name.find(POWERCAP_PACKAGE_PATTERN) == 0 && name.find(":") != std::string::npos) {
            std::string path = std::string(POWERCAP_BASE_PATH) + "/" + name;

            // Check if this is a package (not a subzone)
            std::string name_file = path + "/name";
            std::ifstream name_stream(name_file);
            std::string zone_name;
            if (name_stream >> zone_name) {
                if (zone_name == "package-0" || zone_name.find("package") != std::string::npos) {
                    rapl_package_paths.push_back(path);
                    std::cout << "[RAPL] Found package: " << zone_name << " at " << path << std::endl;
                }
            }
        }
    }
    closedir(dir);

    if (rapl_package_paths.empty()) {
        std::cerr << "[RAPL] No RAPL packages found in powercap sysfs" << std::endl;
        return false;
    }

    use_powercap = true;
    return true;
}

/**
 * Initialize RAPL using MSR interface (requires root or msr kernel module)
 */
static bool init_msr() {
    // Try to read power unit from CPU 0
    uint64_t power_unit_raw;
    if (!read_msr(0, MSR_RAPL_POWER_UNIT, &power_unit_raw)) {
        std::cerr << "[RAPL] Failed to read RAPL power unit from MSR" << std::endl;
        std::cerr << "[RAPL] Ensure msr kernel module is loaded: sudo modprobe msr" << std::endl;
        return false;
    }

    // Extract energy unit (bits 12:8)
    uint32_t energy_unit_raw = (power_unit_raw >> 8) & 0x1F;
    energy_unit = 1.0 / (1 << energy_unit_raw);  // Energy in Joules

    std::cout << "[RAPL] MSR interface initialized. Energy unit: " << energy_unit << " J" << std::endl;
    use_powercap = false;
    return true;
}

/**
 * Read energy from powercap sysfs
 */
static bool read_powercap_energy(long *package_energy) {
    if (rapl_package_paths.empty()) {
        return false;
    }

    // Read energy from first package (package-0)
    std::string energy_file = rapl_package_paths[0] + "/" + POWERCAP_ENERGY_FILE;
    std::ifstream energy_stream(energy_file);

    if (!energy_stream) {
        std::cerr << "[RAPL] Failed to read energy from: " << energy_file << std::endl;
        return false;
    }

    energy_stream >> *package_energy;
    return true;
}

/**
 * Read energy from MSR
 */
static bool read_msr_energy(long *package_energy, long *pp0_energy, long *pp1_energy, long *dram_energy) {
    uint64_t pkg_raw, pp0_raw, pp1_raw, dram_raw;

    // Read package energy
    if (read_msr(0, MSR_PKG_ENERGY_STATUS, &pkg_raw)) {
        *package_energy = (long)((pkg_raw & 0xFFFFFFFF) * energy_unit * 1000000); // Convert to μJ
    }

    // Read PP0 (cores) energy
    if (read_msr(0, MSR_PP0_ENERGY_STATUS, &pp0_raw)) {
        *pp0_energy = (long)((pp0_raw & 0xFFFFFFFF) * energy_unit * 1000000);
    }

    // Read PP1 (integrated GPU) energy - may not be available on all CPUs
    if (read_msr(0, MSR_PP1_ENERGY_STATUS, &pp1_raw)) {
        *pp1_energy = (long)((pp1_raw & 0xFFFFFFFF) * energy_unit * 1000000);
    }

    // Read DRAM energy - may not be available on all CPUs
    if (read_msr(0, MSR_DRAM_ENERGY_STATUS, &dram_raw)) {
        *dram_energy = (long)((dram_raw & 0xFFFFFFFF) * energy_unit * 1000000);
    }

    return true;
}
#endif

/*
 * Class:     uk_ac_manchester_tornado_drivers_opencl_power_OCLIntelRAPLPowerMetricHandler
 * Method:    clRAPLInit
 * Signature: ()J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_power_OCLIntelRAPLPowerMetricHandler_clRAPLInit
        (JNIEnv *env, jclass) {
#ifdef RAPL_IS_SUPPORTED
    if (rapl_initialized) {
        return 0; // Already initialized
    }

    std::cout << "[RAPL] Initializing Intel RAPL power monitoring..." << std::endl;

    // Try powercap sysfs first (preferred, no root needed)
    if (init_powercap()) {
        rapl_initialized = true;
        std::cout << "[RAPL] Initialized using powercap sysfs interface" << std::endl;
        return 0;
    }

    // Fall back to MSR interface (requires root or msr module)
    if (init_msr()) {
        rapl_initialized = true;
        std::cout << "[RAPL] Initialized using MSR interface" << std::endl;
        return 0;
    }

    std::cerr << "[RAPL] Failed to initialize RAPL. Ensure either:" << std::endl;
    std::cerr << "[RAPL]   1. Powercap sysfs is available (kernel 3.13+), or" << std::endl;
    std::cerr << "[RAPL]   2. MSR module is loaded (sudo modprobe msr) with appropriate permissions" << std::endl;

    return -1;
#else
    std::cerr << "[RAPL] RAPL support not compiled. Rebuild with RAPL_IS_SUPPORTED defined." << std::endl;
    return -1;
#endif
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_opencl_power_OCLIntelRAPLPowerMetricHandler
 * Method:    clRAPLReadEnergy
 * Signature: ([J)J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_power_OCLIntelRAPLPowerMetricHandler_clRAPLReadEnergy
        (JNIEnv *env, jclass clazz, jlongArray energyArray) {
#ifdef RAPL_IS_SUPPORTED
    if (!rapl_initialized) {
        return -1;
    }

    jlong *energyUsage = env->GetLongArrayElements(energyArray, nullptr);
    if (energyUsage == nullptr) {
        return -1;
    }

    // Initialize all to 0
    energyUsage[0] = 0; // Package
    energyUsage[1] = 0; // PP0 (cores)
    energyUsage[2] = 0; // PP1 (iGPU)
    energyUsage[3] = 0; // DRAM

    bool success = false;
    if (use_powercap) {
        long package_energy = 0;
        success = read_powercap_energy(&package_energy);
        if (success) {
            energyUsage[0] = package_energy; // Already in μJ
        }
    } else {
        long pkg = 0, pp0 = 0, pp1 = 0, dram = 0;
        success = read_msr_energy(&pkg, &pp0, &pp1, &dram);
        if (success) {
            energyUsage[0] = pkg;
            energyUsage[1] = pp0;
            energyUsage[2] = pp1;
            energyUsage[3] = dram;
        }
    }

    env->ReleaseLongArrayElements(energyArray, energyUsage, 0);
    return success ? 0 : -1;
#else
    return -1;
#endif
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_opencl_power_OCLIntelRAPLPowerMetricHandler
 * Method:    clRAPLClose
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_uk_ac_manchester_tornado_drivers_opencl_power_OCLIntelRAPLPowerMetricHandler_clRAPLClose
        (JNIEnv *env, jclass) {
#ifdef RAPL_IS_SUPPORTED
    if (rapl_initialized) {
        rapl_package_paths.clear();
        if (msr_fd >= 0) {
            close(msr_fd);
            msr_fd = -1;
        }
        rapl_initialized = false;
        std::cout << "[RAPL] Intel RAPL closed" << std::endl;
    }
#endif
}
