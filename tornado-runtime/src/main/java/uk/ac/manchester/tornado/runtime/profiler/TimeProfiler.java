/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2020, APT Group
 * The University of Manchester. All rights reserved.
 *
 * GPLv2 only.
 */
package uk.ac.manchester.tornado.runtime.profiler;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import uk.ac.manchester.tornado.api.profiler.ProfilerType;
import uk.ac.manchester.tornado.api.profiler.TornadoProfiler;
import uk.ac.manchester.tornado.runtime.common.RuntimeUtilities;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;
import uk.ac.manchester.tornado.runtime.profiler.rapl.RaplReader;

/**
 * Time + (optional) Power profiler.
 *
 * - Keeps per-task timers / sizes.
 * - Can read CPU power via Linux RAPL (energy_uj) when enabled and when the task
 *   runs on a CPU device (OpenCL CPU / pthread).
 * - Stores power under ProfilerType.POWER_USAGE_mW (do NOT change API),
 *   but renames the JSON key to CPU_POWER_USAGE_mW or GPU_POWER_USAGE_mW
 *   at dump time based on device type for readability.
 */
public class TimeProfiler implements TornadoProfiler {

    /** Used for sync ops that do not have a task name (legacy behavior). */
    public static String NO_TASK_NAME = "noTask";

    /* ------------------------ Core metrics ------------------------ */
    private HashMap<ProfilerType, Long> profilerTime;
    private HashMap<String, HashMap<ProfilerType, Long>> taskTimers;
    private HashMap<String, HashMap<ProfilerType, String>> taskPowerMetrics;
    private HashMap<String, HashMap<ProfilerType, Long>> taskSizeMetrics;
    private HashMap<String, HashMap<ProfilerType, String>> taskDeviceIdentifiers;
    private HashMap<String, HashMap<ProfilerType, String>> taskMethodNames;
    private HashMap<String, HashMap<ProfilerType, String>> taskBackends;

    private StringBuilder indent;

    /* ------------------------ CPU RAPL support ------------------------ */
    /** Enable CPU RAPL collection with -Dtornado.power.cpu.rapl=true (default true). */
    private final boolean enableRapl = Boolean.parseBoolean(System.getProperty("tornado.power.cpu.rapl", "true"));
    /** Reader discovered at init (null if not present / not permitted). */
    private RaplReader raplReader = null;
    /** Per-task start energy/time when TASK_KERNEL_TIME starts. */
    private final HashMap<String, Long> raplStartEnergyUj = new HashMap<>();
    private final HashMap<String, Long> raplStartTimeNs = new HashMap<>();

    public TimeProfiler() {
        profilerTime = new HashMap<>();
        taskTimers = new HashMap<>();
        taskPowerMetrics = new HashMap<>();
        taskDeviceIdentifiers = new HashMap<>();
        taskMethodNames = new HashMap<>();
        taskSizeMetrics = new HashMap<>();
        taskBackends = new HashMap<>();
        indent = new StringBuilder("");

        // Try to discover a RAPL reader if enabled
        try {
            if (enableRapl) {
                Optional<RaplReader> opt = RaplReader.discover();
                raplReader = opt.orElse(null);
            }
        } catch (Throwable ignore) {
            raplReader = null;
        }
    }

    /* ======================== TornadoProfiler API ======================== */

    @Override
    public synchronized void addValueToMetric(ProfilerType type, String taskName, long value) {
        if (!taskSizeMetrics.containsKey(taskName)) {
            taskSizeMetrics.put(taskName, new HashMap<>());
        }
        HashMap<ProfilerType, Long> profilerType = taskSizeMetrics.get(taskName);
        profilerType.put(type, profilerType.get(type) != null ? profilerType.get(type) + value : value);
        taskSizeMetrics.put(taskName, profilerType);
    }

    @Override
    public synchronized void start(ProfilerType type) {
        long start = System.nanoTime();
        profilerTime.put(type, start);
    }

    @Override
    public synchronized void start(ProfilerType type, String taskName) {
        long start = System.nanoTime();
        if (!taskTimers.containsKey(taskName)) {
            taskTimers.put(taskName, new HashMap<>());
        }
        HashMap<ProfilerType, Long> profilerType = taskTimers.get(taskName);
        profilerType.put(type, start);
        taskTimers.put(taskName, profilerType);

        // If this is a CPU task and we measure kernel time, snapshot RAPL energy/time
        if (enableRapl && raplReader != null && type == ProfilerType.TASK_KERNEL_TIME && isCpuTask(taskName)) {
            try {
                raplStartEnergyUj.put(taskName, raplReader.readEnergyUj());
                raplStartTimeNs.put(taskName, System.nanoTime());
            } catch (Throwable ignore) {
                // Keep robust: just skip if cannot read
            }
        }
    }

    @Override
    public synchronized void registerMethodHandle(ProfilerType type, String taskName, String methodName) {
        if (!taskMethodNames.containsKey(taskName)) {
            taskMethodNames.put(taskName, new HashMap<>());
        }
        HashMap<ProfilerType, String> profilerType = taskMethodNames.get(taskName);
        profilerType.put(type, methodName);
        taskMethodNames.put(taskName, profilerType);
    }

    @Override
    public synchronized void registerDeviceName(String taskName, String deviceInfo) {
        if (!taskDeviceIdentifiers.containsKey(taskName)) {
            taskDeviceIdentifiers.put(taskName, new HashMap<>());
        }
        HashMap<ProfilerType, String> profilerType = taskDeviceIdentifiers.get(taskName);
        profilerType.put(ProfilerType.DEVICE, deviceInfo);
        taskDeviceIdentifiers.put(taskName, profilerType);
    }

    @Override
    public synchronized void registerBackend(String taskName, String backend) {
        if (!taskBackends.containsKey(taskName)) {
            taskBackends.put(taskName, new HashMap<>());
        }
        HashMap<ProfilerType, String> profilerType = taskBackends.get(taskName);
        profilerType.put(ProfilerType.BACKEND, backend);
        taskBackends.put(taskName, profilerType);
    }

    @Override
    public synchronized void registerDeviceID(String taskName, String deviceID) {
        if (!taskDeviceIdentifiers.containsKey(taskName)) {
            taskDeviceIdentifiers.put(taskName, new HashMap<>());
        }
        HashMap<ProfilerType, String> profilerType = taskDeviceIdentifiers.get(taskName);
        profilerType.put(ProfilerType.DEVICE_ID, deviceID);
        taskDeviceIdentifiers.put(taskName, profilerType);
    }

    @Override
    public synchronized void stop(ProfilerType type) {
        long end = System.nanoTime();
        Long start = profilerTime.get(type);
        long total = (start == null) ? 0L : (end - start);
        profilerTime.put(type, total);
    }

    @Override
    public synchronized void stop(ProfilerType type, String taskName) {
        long end = System.nanoTime();
        HashMap<ProfilerType, Long> profiledType = taskTimers.get(taskName);
        if (profiledType != null && profiledType.containsKey(type)) {
            long start = profiledType.get(type);
            long total = end - start;
            profiledType.put(type, total);
            taskTimers.put(taskName, profiledType);
        }

        // If CPU task's kernel time just ended, compute power from RAPL
        if (enableRapl && raplReader != null && type == ProfilerType.TASK_KERNEL_TIME && isCpuTask(taskName)) {
            try {
                Long e0 = raplStartEnergyUj.remove(taskName);
                Long t0 = raplStartTimeNs.remove(taskName);
                if (e0 != null && t0 != null) {
                    long e1 = raplReader.readEnergyUj();      // micro-joules
                    long t1 = System.nanoTime();
                    long deltaUj = e1 - e0;
                    if (deltaUj < 0) {                        // counter wrap
                        long wrap = raplReader.minMaxRangeUj();
                        if (wrap > 0) {
                            deltaUj = (wrap - e0) + e1;
                        }
                    }
                    double dtMs = (t1 - t0) / 1e6;            // ns -> ms
                    double energy_mJ = deltaUj / 1000.0;      // μJ -> mJ
                    long power_mW = (dtMs > 0) ? Math.round(energy_mJ / dtMs) : -1;

                    // Store under POWER_USAGE_mW (keep API) — rename at JSON dump time
                    setTaskPowerUsage(ProfilerType.POWER_USAGE_mW, taskName, power_mW);
                }
            } catch (Throwable ignore) {
                // Keep robust
            }
        }
    }

    @Override
    public long getTimer(ProfilerType type) {
        if (!profilerTime.containsKey(type)) {
            return 0;
        }
        return profilerTime.get(type);
    }

    @Override
    public long getSize(ProfilerType type) {
        long size = 0;
        for (String s : taskSizeMetrics.keySet()) {
            HashMap<ProfilerType, Long> copySizes = taskSizeMetrics.get(s);
            if (copySizes != null && copySizes.containsKey(type)) {
                size += copySizes.get(type);
            }
        }
        return size;
    }

    @Override
    public long getTaskTimer(ProfilerType type, String taskName) {
        if (!taskTimers.containsKey(taskName)) {
            return 0;
        }
        if (!taskTimers.get(taskName).containsKey(type)) {
            return 0;
        }
        return taskTimers.get(taskName).get(type);
    }

    @Override
    public synchronized void setTimer(ProfilerType type, long time) {
        profilerTime.put(type, time);
    }

    @Override
    public synchronized void dump() {
        for (ProfilerType p : profilerTime.keySet()) {
            System.out.println("[PROFILER] " + p.getDescription() + ": " + profilerTime.get(p));
        }
        for (String p : taskTimers.keySet()) {
            System.out.println("[PROFILER-TASK] " + p + ": " + taskTimers.get(p));
        }
    }

    private void increaseIndent() {
        indent.append("    ");
    }

    private void decreaseIndent() {
        if (indent.length() >= 4) {
            indent.delete(indent.length() - 4, indent.length());
        }
    }

    private void closeScope(StringBuilder json) {
        json.append(indent.toString()).append("}");
    }

    private void newLine(StringBuilder json) {
        json.append("\n");
    }

    private static String jsonEscape(String s) {
        if (s == null) return "null";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /** Determine if a task runs on CPU device by its registered device name. */
    private boolean isCpuTask(String taskName) {
        try {
            if (!taskDeviceIdentifiers.containsKey(taskName)) {
                return false;
            }
            String dev = taskDeviceIdentifiers.get(taskName).get(ProfilerType.DEVICE);
            if (dev == null) {
                return false;
            }
            String d = dev.toLowerCase();
            return d.contains("cpu") || d.contains("pthread") || d.contains("cl_device_type_cpu");
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public String createJson(StringBuilder json, String sectionName) {
        json.append("{\n");
        increaseIndent();
        json.append(indent.toString()).append("\"").append(sectionName).append("\": ").append("{\n");
        increaseIndent();

        // Global profiler timers
        for (ProfilerType p : profilerTime.keySet()) {
            json.append(indent.toString())
                .append("\"").append(p).append("\": ")
                .append("\"").append(profilerTime.get(p)).append("\",\n");
        }

        // Global NO_TASK_NAME sizes (e.g., copies accumulated at schedule level)
        if (taskSizeMetrics.containsKey(NO_TASK_NAME)) {
            HashMap<ProfilerType, Long> noTaskValues = taskSizeMetrics.get(NO_TASK_NAME);
            if (noTaskValues != null) {
                for (ProfilerType p : noTaskValues.keySet()) {
                    json.append(indent.toString())
                        .append("\"").append(p).append("\": ")
                        .append("\"").append(noTaskValues.get(p)).append("\",\n");
                }
            }
        }

        final int size = taskTimers.keySet().size();
        int counter = 0;

        for (String task : taskTimers.keySet()) {
            json.append(indent.toString()).append("\"").append(jsonEscape(task)).append("\": {\n");
            increaseIndent();
            counter++;

            if (TornadoOptions.LOG_IP) {
                json.append(indent.toString())
                    .append("\"IP\": ")
                    .append("\"").append(jsonEscape(RuntimeUtilities.getTornadoInstanceIP())).append("\",\n");
            }

            // Backend
            String backend = null;
            if (taskBackends.containsKey(task) && taskBackends.get(task) != null) {
                backend = taskBackends.get(task).get(ProfilerType.BACKEND);
            }
            json.append(indent.toString())
                .append("\"").append(ProfilerType.BACKEND).append("\": ")
                .append("\"").append(jsonEscape(backend != null ? backend : "UNKNOWN")).append("\",\n");

            // Method name
            String method = null;
            if (taskMethodNames.containsKey(task) && taskMethodNames.get(task) != null) {
                method = taskMethodNames.get(task).get(ProfilerType.METHOD);
            }
            json.append(indent.toString())
                .append("\"").append(ProfilerType.METHOD).append("\": ")
                .append("\"").append(jsonEscape(method != null ? method : "UNKNOWN")).append("\",\n");

            // Device ID + Device
            String deviceId = null;
            String deviceStr = null;
            if (taskDeviceIdentifiers.containsKey(task) && taskDeviceIdentifiers.get(task) != null) {
                deviceId = taskDeviceIdentifiers.get(task).get(ProfilerType.DEVICE_ID);
                deviceStr = taskDeviceIdentifiers.get(task).get(ProfilerType.DEVICE);
            }
            json.append(indent.toString())
                .append("\"").append(ProfilerType.DEVICE_ID).append("\": ")
                .append("\"").append(jsonEscape(deviceId != null ? deviceId : "UNKNOWN")).append("\",\n");

            json.append(indent.toString())
                .append("\"").append(ProfilerType.DEVICE).append("\": ")
                .append("\"").append(jsonEscape(deviceStr != null ? deviceStr : "UNKNOWN")).append("\",\n");

            // Sizes for this task
            if (taskSizeMetrics.containsKey(task) && taskSizeMetrics.get(task) != null) {
                for (ProfilerType p1 : taskSizeMetrics.get(task).keySet()) {
                    json.append(indent.toString())
                        .append("\"").append(p1).append("\": ")
                        .append("\"").append(taskSizeMetrics.get(task).get(p1)).append("\",\n");
                }
            }

            // Power metrics for this task (rename POWER_USAGE_mW by device type for readability)
            boolean isCpuDev = (deviceStr != null) &&
                    deviceStr.toLowerCase().matches(".*(cpu|pthread|cl_device_type_cpu).*");

            if (taskPowerMetrics.containsKey(task) && taskPowerMetrics.get(task) != null) {
                for (Map.Entry<ProfilerType, String> e : taskPowerMetrics.get(task).entrySet()) {
                    String keyName;
                    if (e.getKey() == ProfilerType.POWER_USAGE_mW) {
                        keyName = isCpuDev ? "CPU_POWER_USAGE_mW" : "GPU_POWER_USAGE_mW";
                    } else {
                        keyName = e.getKey().toString();
                    }
                    json.append(indent.toString())
                        .append("\"").append(keyName).append("\": ")
                        .append("\"").append(jsonEscape(e.getValue())).append("\",\n");
                }
            }

            // Timers for this task
            HashMap<ProfilerType, Long> timers = taskTimers.get(task);
            if (timers != null) {
                for (ProfilerType p2 : timers.keySet()) {
                    json.append(indent.toString())
                        .append("\"").append(p2).append("\": ")
                        .append("\"").append(timers.get(p2)).append("\",\n");
                }
            }

            // Remove trailing comma if present (safe-guard)
            int len = json.length();
            if (len >= 2 && json.substring(len - 2, len).equals(",\n")) {
                json.delete(len - 2, len);
                json.append("\n");
            }

            decreaseIndent();
            closeScope(json);
            if (counter != size) {
                json.append(", ");
            }
            newLine(json);
        }

        decreaseIndent();
        closeScope(json);
        newLine(json);
        decreaseIndent();
        closeScope(json);
        newLine(json);
        return json.toString();
    }

    @Override
    public synchronized void dumpJson(StringBuilder json, String id) {
        String jsonContent = createJson(json, id);
        System.out.println(jsonContent);
    }

    @Override
    public synchronized void clean() {
        taskSizeMetrics.clear();
        profilerTime.clear();
        taskTimers.clear();
        taskPowerMetrics.clear();
        taskDeviceIdentifiers.clear();
        taskMethodNames.clear();
        taskBackends.clear();
        raplStartEnergyUj.clear();
        raplStartTimeNs.clear();
        indent = new StringBuilder("");
    }

    @Override
    public synchronized void setTaskTimer(ProfilerType type, String taskID, long timer) {
        if (!taskTimers.containsKey(taskID)) {
            taskTimers.put(taskID, new HashMap<>());
        }
        taskTimers.get(taskID).put(type, timer);
    }

    @Override
    public synchronized void setTaskPowerUsage(ProfilerType type, String taskID, long power) {
        if (!taskPowerMetrics.containsKey(taskID)) {
            taskPowerMetrics.put(taskID, new HashMap<>());
        }
        if (power > 0) {
            taskPowerMetrics.get(taskID).put(type, Long.toString(power));
        } else {
            taskPowerMetrics.get(taskID).put(type, "n/a");
        }
    }

    @Override
    public void setSystemPowerConsumption(ProfilerType systemPowerConsumptionType, String taskID, long powerConsumption) {
        if (!taskPowerMetrics.containsKey(taskID)) {
            taskPowerMetrics.put(taskID, new HashMap<>());
        }
        if (powerConsumption > 0) {
            taskPowerMetrics.get(taskID).put(systemPowerConsumptionType, Long.toString(powerConsumption));
        } else {
            taskPowerMetrics.get(taskID).put(systemPowerConsumptionType, "n/a");
        }
    }

    @Override
    public void setSystemVoltage(ProfilerType systemPowerVoltageType, String taskID, long voltage) {
        if (!taskPowerMetrics.containsKey(taskID)) {
            taskPowerMetrics.put(taskID, new HashMap<>());
        }
        if (voltage > 0) {
            taskPowerMetrics.get(taskID).put(systemPowerVoltageType, Float.toString(voltage));
        } else {
            taskPowerMetrics.get(taskID).put(systemPowerVoltageType, "n/a");
        }
    }

    @Override
    public synchronized void sum(ProfilerType acc, long value) {
        long sum = getTimer(acc) + value;
        profilerTime.put(acc, sum);
    }
}

