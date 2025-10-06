/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2020, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.runtime.profiler;

import java.util.HashMap;
import java.util.Set;

import uk.ac.manchester.tornado.api.profiler.ProfilerType;
import uk.ac.manchester.tornado.api.profiler.TornadoProfiler;
import uk.ac.manchester.tornado.runtime.common.RuntimeUtilities;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;

public class TimeProfiler implements TornadoProfiler {

    /**
     * Use this dummy field because {@link #addValueToMetric} needs a task name.
     * However, sync operations operate on task schedules, not on tasks. TODO remove
     * this field when the {@link TimeProfiler} is refactored. Related to issue #94.
     */
    public static String NO_TASK_NAME = "noTask";

    private HashMap<ProfilerType, Long> profilerTime;
    private HashMap<String, HashMap<ProfilerType, Long>> taskTimers;
    private HashMap<String, HashMap<ProfilerType, String>> taskPowerMetrics;
    private HashMap<String, HashMap<ProfilerType, Long>> taskSizeMetrics;
    private HashMap<String, HashMap<ProfilerType, String>> taskDeviceIdentifiers;
    private HashMap<String, HashMap<ProfilerType, String>> taskMethodNames;

    // --- RAPL support (CPU) ---
    private final boolean enableRapl = Boolean.parseBoolean(System.getProperty("tornado.power.cpu.rapl", "true"));
    private uk.ac.manchester.tornado.runtime.profiler.rapl.RaplReader raplReader = null;
    private final HashMap<String, Long> raplStartEnergyUj = new HashMap<>();
    private final HashMap<String, Long> raplStartTimeNs = new HashMap<>();

    private HashMap<String, HashMap<ProfilerType, String>> taskBackends;

    private StringBuilder indent;

    public TimeProfiler() {
        profilerTime = new HashMap<>();
        taskTimers = new HashMap<>();
        taskPowerMetrics = new HashMap<>();
        taskDeviceIdentifiers = new HashMap<>();
        taskMethodNames = new HashMap<>();
        taskSizeMetrics = new HashMap<>();
        taskBackends = new HashMap<>();
        indent = new StringBuilder("");
        // Try to discover RAPL once (Linux, Intel CPUs). Safe to fail quietly.
        try {
            if (enableRapl) {
                java.util.Optional<uk.ac.manchester.tornado.runtime.profiler.rapl.RaplReader> opt =
                        uk.ac.manchester.tornado.runtime.profiler.rapl.RaplReader.discover();
                raplReader = opt.orElse(null);
            }
        } catch (Throwable ignore) {
            raplReader = null;
        }
    }

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

        // --- RAPL begin: record unconditionally for kernel interval ---
        if (enableRapl && raplReader != null && type == ProfilerType.TASK_KERNEL_TIME) {
            try {
                raplStartEnergyUj.put(taskName, raplReader.readEnergyUj());
                raplStartTimeNs.put(taskName, System.nanoTime());
            } catch (Exception ignore) {
                // do nothing
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
        long start = profilerTime.get(type);
        long total = end - start;
        profilerTime.put(type, total);
    }

    @Override
    public synchronized void stop(ProfilerType type, String taskName) {
        long end = System.nanoTime();
        HashMap<ProfilerType, Long> profiledType = taskTimers.get(taskName);
        long start = profiledType.get(type);
        long total = end - start;
        profiledType.put(type, total);
        taskTimers.put(taskName, profiledType);

        // --- RAPL end & record (only for CPU tasks) ---
        if (enableRapl && raplReader != null && type == ProfilerType.TASK_KERNEL_TIME) {
            try {
                Long e0 = raplStartEnergyUj.remove(taskName);
                Long t0 = raplStartTimeNs.remove(taskName);
                if (e0 != null && t0 != null && isCpuTask(taskName)) {
                    long e1 = raplReader.readEnergyUj();
                    long t1 = System.nanoTime();
                    long deltaUj = e1 - e0;
                    if (deltaUj < 0) {
                        long wrap = raplReader.minMaxRangeUj();
                        if (wrap > 0) {
                            deltaUj = (wrap - e0) + e1;
                        }
                    }
                    double dtMs = (t1 - t0) / 1e6;
                    double energy_mJ = deltaUj / 1000.0;
                    long power_mW = (dtMs > 0.0) ? Math.round(energy_mJ / dtMs) : -1L;
                    if (power_mW > 0L) {
                        // Write into existing metric key (no new enum needed)
                        setTaskPowerUsage(ProfilerType.POWER_USAGE_mW, taskName, power_mW);
                    }
                }
                // else: non-CPU or missing start — skip silently
            } catch (Throwable ignore) {
                // skip silently
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
        // for all tasks in the task graph, accumulate the size
        Set<String> strings = taskSizeMetrics.keySet();
        long size = 0;
        for (String s : taskSizeMetrics.keySet()) {
            HashMap<ProfilerType, Long> copySizes = taskSizeMetrics.get(s);
            if (copySizes.containsKey(type)) {
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
        indent.delete(indent.length() - 4, indent.length());
    }

    private void closeScope(StringBuilder json) {
        json.append(indent.toString() + "}");
    }

    private void newLine(StringBuilder json) {
        json.append("\n");
    }

    @Override
    public String createJson(StringBuilder json, String sectionName) {
        json.append("{\n");
        increaseIndent();
        json.append(indent.toString() + "\"" + sectionName + "\": " + "{\n");
        increaseIndent();
        for (ProfilerType p : profilerTime.keySet()) {
            json.append(indent.toString() + "\"" + p + "\"" + ": " + "\"" + profilerTime.get(p) + "\",\n");
        }
        if (taskSizeMetrics.containsKey(NO_TASK_NAME)) {
            HashMap<ProfilerType, Long> noTaskValues = taskSizeMetrics.get(NO_TASK_NAME);
            for (ProfilerType p : noTaskValues.keySet()) {
                json.append(indent.toString() + "\"" + p + "\"" + ": " + "\"" + noTaskValues.get(p) + "\",\n");
            }
        }

        final int size = taskTimers.keySet().size();
        int counter = 0;
        for (String p : taskTimers.keySet()) {
            json.append(indent.toString() + "\"" + p + "\"" + ": {\n");
            increaseIndent();
            counter++;
            if (TornadoOptions.LOG_IP) {
                json.append(indent.toString() + "\"" + "IP" + "\"" + ": " + "\"" + RuntimeUtilities.getTornadoInstanceIP() + "\",\n");
            }
            json.append(indent.toString() + "\"" + ProfilerType.BACKEND + "\"" + ": " + "\"" + taskBackends.get(p).get(ProfilerType.BACKEND) + "\",\n");
            json.append(indent.toString() + "\"" + ProfilerType.METHOD + "\"" + ": " + "\"" + taskMethodNames.get(p).get(ProfilerType.METHOD) + "\",\n");
            json.append(indent.toString() + "\"" + ProfilerType.DEVICE_ID + "\"" + ": " + "\"" + taskDeviceIdentifiers.get(p).get(ProfilerType.DEVICE_ID) + "\",\n");
            json.append(indent.toString() + "\"" + ProfilerType.DEVICE + "\"" + ": " + "\"" + taskDeviceIdentifiers.get(p).get(ProfilerType.DEVICE) + "\",\n");
            if (taskSizeMetrics.containsKey(p)) {
                for (ProfilerType p1 : taskSizeMetrics.get(p).keySet()) {
                    json.append(indent.toString() + "\"" + p1 + "\"" + ": " + "\"" + taskSizeMetrics.get(p).get(p1) + "\",\n");
                }
            }
            if (taskPowerMetrics.containsKey(p)) {
                for (ProfilerType p1 : taskPowerMetrics.get(p).keySet()) {
                    json.append(indent.toString() + "\"" + p1 + "\"" + ": " + "\"" + taskPowerMetrics.get(p).get(p1) + "\",\n");
                }
            }
            if (taskPowerMetrics.containsKey(p)) {
    String val = taskPowerMetrics.get(p).get(ProfilerType.POWER_USAGE_mW);
    if (val != null && !val.equals("n/a")) {
        if (isCpuTask(p)) {
            json.append(indent.toString() + "\"CPU_POWER_USAGE_mW\": " + "\"" + val + "\",\n");
        } else {
            json.append(indent.toString() + "\"GPU_POWER_USAGE_mW\": " + "\"" + val + "\",\n");
        	}
    	}
	}
            for (ProfilerType p2 : taskTimers.get(p).keySet()) {
                json.append(indent.toString() + "\"" + p2 + "\"" + ": " + "\"" + taskTimers.get(p).get(p2) + "\",\n");
            }
            json.delete(json.length() - 2, json.length() - 1); // remove last comma
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
        indent = new StringBuilder("");
    }

    private boolean isCpuTask(String taskName) {
        try {
            if (!taskDeviceIdentifiers.containsKey(taskName)) return false;
            String dev = taskDeviceIdentifiers.get(taskName).get(ProfilerType.DEVICE);
            if (dev == null) return false;
            String d = dev.toLowerCase();
            // e.g. "pthread-12th Gen Intel(R) Core..." or "... CL_DEVICE_TYPE_CPU ..."
            return d.contains("cpu") || d.contains("pthread");
        } catch (Throwable t) {
            return false;
        }
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
            taskPowerMetrics.get(taskID).remove(type);
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

