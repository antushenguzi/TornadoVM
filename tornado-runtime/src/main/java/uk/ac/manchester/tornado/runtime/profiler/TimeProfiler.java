/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2020, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
 * GPLv2 only.
 */

package uk.ac.manchester.tornado.runtime.profiler;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import uk.ac.manchester.tornado.api.profiler.ProfilerType;
import uk.ac.manchester.tornado.api.profiler.TornadoProfiler;
import uk.ac.manchester.tornado.runtime.common.RuntimeUtilities;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;
import uk.ac.manchester.tornado.runtime.profiler.rapl.RaplReader;

public class TimeProfiler implements TornadoProfiler {

    public static String NO_TASK_NAME = "noTask";

    private HashMap<ProfilerType, Long> profilerTime;
    private HashMap<String, HashMap<ProfilerType, Long>> taskTimers;
    private HashMap<String, HashMap<ProfilerType, String>> taskPowerMetrics;
    private HashMap<String, HashMap<ProfilerType, Long>> taskSizeMetrics;
    private HashMap<String, HashMap<ProfilerType, String>> taskDeviceIdentifiers;
    private HashMap<String, HashMap<ProfilerType, String>> taskMethodNames;
    private HashMap<String, HashMap<ProfilerType, String>> taskBackends;
    private static volatile TimeProfiler INSTANCE;

    private StringBuilder indent;

    // --------- RAPL (CPU) ----------
    private final boolean enableRapl = Boolean.parseBoolean(System.getProperty("tornado.power.cpu.rapl", "false"));
    private final boolean raplDebug  = Boolean.parseBoolean(System.getProperty("tornado.power.cpu.rapl.debug", "false"));
    private uk.ac.manchester.tornado.runtime.profiler.rapl.RaplReader raplReader = null;
    private static final java.util.concurrent.ConcurrentHashMap<String, Long> raplStartEnergyUj = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.concurrent.ConcurrentHashMap<String, Long> raplStartTimeNs   = new java.util.concurrent.ConcurrentHashMap<>();


    private static final java.util.concurrent.ConcurrentHashMap<String, Long> raplSumEnergyUj   = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.concurrent.ConcurrentHashMap<String, Long> raplSumTimeNs     = new java.util.concurrent.ConcurrentHashMap<>();

    private static final java.util.concurrent.ConcurrentHashMap<String, String> raplLatestKeyByBase = new java.util.concurrent.ConcurrentHashMap<>();


    private static String raplBaseOf(String id) {
        if (id == null) return "";
        String t = id;
        int slash = t.lastIndexOf('/');  if (slash >= 0) t = t.substring(slash + 1); // saxpy#101
        int dot   = t.lastIndexOf('.');  if (dot   >= 0) t = t.substring(dot   + 1); // saxpy
        int hash  = t.indexOf('#');      if (hash  >= 0) t = t.substring(0, hash);
        return t;
    }



    public TimeProfiler() {
        INSTANCE = this;
        profilerTime = new HashMap<>();
        taskTimers = new HashMap<>();
        taskPowerMetrics = new HashMap<>();
        taskDeviceIdentifiers = new HashMap<>();
        taskMethodNames = new HashMap<>();
        taskSizeMetrics = new HashMap<>();
        taskBackends = new HashMap<>();
        indent = new StringBuilder("");

        if (enableRapl) {
            try {
                raplReader = RaplReader.discover().orElse(null);
                if (raplDebug) {
                    System.out.println("[RAPL-DEBUG] discover raplReader=" + (raplReader != null));
                }
            } catch (Throwable t) {
                raplReader = null;
                if (raplDebug) {
                    System.out.println("[RAPL-DEBUG] discover failed: " + t.getMessage());
                }
            }
        }
    }
    
    public static TimeProfiler getInstance(){
    	return INSTANCE;
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
            taskTimers.put(taskName, new java.util.HashMap<>());
        }
        java.util.HashMap<ProfilerType, Long> profilerType = taskTimers.get(taskName);
        profilerType.put(type, start);
        taskTimers.put(taskName, profilerType);

        if (type != ProfilerType.TASK_KERNEL_TIME) {
            return;
        }
        if (!(enableRapl && raplReader != null)) {
            if (raplDebug) {
                System.out.println("[RAPL-DEBUG] START task=" + taskName + " rapl disabled or raplReader null");
            }
            return;
        }

        if (raplStartEnergyUj.containsKey(taskName) && raplStartTimeNs.containsKey(taskName)) {
            if (raplDebug) {
                System.out.println("[RAPL-DEBUG] START task=" + taskName + " baseline already present");
            }
            return;
        }

        try {
            long e0 = raplReader.readEnergyUj();  // μJ
            long t0 = System.nanoTime();          // ns
            raplStartEnergyUj.put(taskName, e0);
            raplStartTimeNs.put(taskName, t0);


            raplLatestKeyByBase.put(raplBaseOf(taskName), taskName);

            if (raplDebug) {
                System.out.println("[RAPL-DEBUG] START task=" + taskName + " e0(uj)=" + e0 + " t0(ns)=" + t0);
            }
        } catch (Throwable t) {
            raplStartEnergyUj.remove(taskName);
            raplStartTimeNs.remove(taskName);
            if (raplDebug) {
                System.out.println("[RAPL-DEBUG] START read fail task=" + taskName + " err=" + t);
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
        java.util.HashMap<ProfilerType, Long> profiledType = taskTimers.get(taskName);
        if (profiledType != null && profiledType.containsKey(type)) {
            long start = profiledType.get(type);
            long total = end - start;
            profiledType.put(type, total);
            taskTimers.put(taskName, profiledType);
        }

        if (!(enableRapl && raplReader != null && type == ProfilerType.TASK_KERNEL_TIME)) {
            if (raplDebug && type == ProfilerType.TASK_KERNEL_TIME) {
                System.out.println("[RAPL-DEBUG] STOP task=" + taskName + " rapl disabled or raplReader null");
            }
            return;
        }

        Long e0 = raplStartEnergyUj.get(taskName);
        Long t0 = raplStartTimeNs.get(taskName);

        boolean isCpu = false;
        try {
            if (taskDeviceIdentifiers.containsKey(taskName)) {
                String dev = taskDeviceIdentifiers.get(taskName).get(ProfilerType.DEVICE);
                if (dev != null) {
                    String d = dev.toLowerCase();
                    isCpu = d.contains("cpu") || d.contains("pthread") || d.contains("cl_device_type_cpu");
                }
            }
        } catch (Throwable ignore) {}

        if (!isCpu) {
            if (raplDebug) {
                System.out.println("[RAPL-DEBUG] STOP task=" + taskName + " not CPU → discard baseline e0=" + e0 + " t0=" + t0);
            }
            return;
        }

        if (e0 == null) {
            if (raplDebug) {
                System.out.println("[RAPL-DEBUG] STOP task=" + taskName + " missing baseline e0");
            }
            return;
        }

        try {
            long e1 = raplReader.readEnergyUj();    // μJ
            long t1 = System.nanoTime();            // ns

            long deltaUj = e1 - e0;
            if (deltaUj < 0) {
                long wrap = raplReader.minMaxRangeUj();
                if (wrap > 0) deltaUj = (wrap - e0) + e1;
            }

            long dtNs;
            if (t0 != null) {
                dtNs = t1 - t0;
            } else {
                Long dtFromTimer = null;
                java.util.HashMap<ProfilerType, Long> pt = taskTimers.get(taskName);
                if (pt != null) dtFromTimer = pt.get(ProfilerType.TASK_KERNEL_TIME);
                if (dtFromTimer == null) {
                    if (raplDebug) {
                        System.out.println("[RAPL-DEBUG] STOP task=" + taskName + " missing dtNs fallback");
                    }
                    return;
                }
                dtNs = dtFromTimer;
            }


            double dtMs      = dtNs / 1e6;           // ns → ms
            double energy_mJ = deltaUj / 1000.0;     // μJ → mJ
            long   power_mW  = (dtMs > 0.0) ? Math.round((energy_mJ / dtMs) * 1000.0) : -1;

            setTaskPowerUsage(ProfilerType.POWER_USAGE_mW, taskName, power_mW);

            raplSumEnergyUj.put(taskName, raplSumEnergyUj.getOrDefault(taskName, 0L) + deltaUj);
            raplSumTimeNs.put(taskName, raplSumTimeNs.getOrDefault(taskName, 0L) + dtNs);

            // ✅ 成功后再清理基线
            raplStartEnergyUj.remove(taskName);
            raplStartTimeNs.remove(taskName);

            if (raplDebug) {
                System.out.println("[RAPL-DEBUG] STOP  task=" + taskName
                        + " e1(uj)=" + e1
                        + " delta(uj)=" + deltaUj
                        + " dt(ms)=" + String.format("%.3f", dtMs)
                        + " power(mW)=" + power_mW);
            }
        } catch (Throwable t) {
            if (raplDebug) {
                System.out.println("[RAPL-DEBUG] STOP read fail task=" + taskName + " err=" + t);
            }
        }
    }

    /**
     * Try to resolve the best matching RAPL baseline for a given taskId.
     * This is used when auto-stopping RAPL after kernel time is recorded,
     * where the taskId may be different from the one used at start().
     *
     * @param taskId The task ID to resolve.
     * @return The resolved task ID that has a RAPL baseline, or the original taskId if none found.
     */

    private String resolveRaplTaskKeyForTimer(String taskId) {


        if (raplStartEnergyUj.containsKey(taskId)) {
            return taskId;
        }


        String base = raplBaseOf(taskId);


        String cand = raplLatestKeyByBase.get(base);
        if (cand != null && raplStartEnergyUj.containsKey(cand)) {
            return cand;
        }

        String best = null;
        long bestT0 = Long.MIN_VALUE;

        java.util.ArrayList<String> keysSnapshot = new java.util.ArrayList<>(raplStartEnergyUj.keySet());
        for (String k : keysSnapshot) {
            if (raplBaseOf(k).equals(base)) {
                Long t0 = raplStartTimeNs.get(k);

                if (best == null) {
                    best = k;
                    bestT0 = (t0 != null) ? t0 : Long.MIN_VALUE;
                } else if (t0 != null && t0 > bestT0) {
                    best = k;
                    bestT0 = t0;
                }
            }
        }

        return (best != null) ? best : taskId;
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
        json.append(indent.toString()).append("}");
    }

    private void newLine(StringBuilder json) {
        json.append("\n");
    }

    private static String jsonEscape(String s) {
        if (s == null) return "null";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private boolean isCpuTask(String taskName) {
        try {
            if (!taskDeviceIdentifiers.containsKey(taskName)) return false;
            String dev = taskDeviceIdentifiers.get(taskName).get(ProfilerType.DEVICE);
            if (dev == null) return false;
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

        for (ProfilerType p : profilerTime.keySet()) {
            json.append(indent.toString())
                .append("\"").append(p).append("\": ")
                .append("\"").append(profilerTime.get(p)).append("\",\n");
        }

        if (taskSizeMetrics.containsKey(NO_TASK_NAME)) {
            HashMap<ProfilerType, Long> noTaskValues = taskSizeMetrics.get(NO_TASK_NAME);
            for (ProfilerType p : noTaskValues.keySet()) {
                json.append(indent.toString())
                    .append("\"").append(p).append("\": ")
                    .append("\"").append(noTaskValues.get(p)).append("\",\n");
            }
        }

        final int size = taskTimers.keySet().size();
        int counter = 0;

        for (String task : taskTimers.keySet()) {
            json.append(indent.toString()).append("\"").append(jsonEscape(task)).append("\": {\n");
            increaseIndent();
            counter++;

            if (TornadoOptions.LOG_IP) {
                json.append(indent.toString()).append("\"IP\": ")
                    .append("\"").append(jsonEscape(RuntimeUtilities.getTornadoInstanceIP())).append("\",\n");
            }

            String backend = null;
            if (taskBackends.containsKey(task)) {
                backend = taskBackends.get(task).get(ProfilerType.BACKEND);
            }
            json.append(indent.toString()).append("\"").append(ProfilerType.BACKEND).append("\": ")
                .append("\"").append(jsonEscape(backend != null ? backend : "UNKNOWN")).append("\",\n");

            String method = null;
            if (taskMethodNames.containsKey(task)) {
                method = taskMethodNames.get(task).get(ProfilerType.METHOD);
            }
            json.append(indent.toString()).append("\"").append(ProfilerType.METHOD).append("\": ")
                .append("\"").append(jsonEscape(method != null ? method : "UNKNOWN")).append("\",\n");

            String deviceId = null;
            String deviceStr = null;
            if (taskDeviceIdentifiers.containsKey(task)) {
                deviceId = taskDeviceIdentifiers.get(task).get(ProfilerType.DEVICE_ID);
                deviceStr = taskDeviceIdentifiers.get(task).get(ProfilerType.DEVICE);
            }
            json.append(indent.toString()).append("\"").append(ProfilerType.DEVICE_ID).append("\": ")
                .append("\"").append(jsonEscape(deviceId != null ? deviceId : "UNKNOWN")).append("\",\n");

            json.append(indent.toString()).append("\"").append(ProfilerType.DEVICE).append("\": ")
                .append("\"").append(jsonEscape(deviceStr != null ? deviceStr : "UNKNOWN")).append("\",\n");

            if (taskSizeMetrics.containsKey(task)) {
                for (ProfilerType p1 : taskSizeMetrics.get(task).keySet()) {
                    json.append(indent.toString()).append("\"").append(p1).append("\": ")
                        .append("\"").append(taskSizeMetrics.get(task).get(p1)).append("\",\n");
                }
            }

            boolean needFallback = true;
            if (taskPowerMetrics.containsKey(task)) {
                for (ProfilerType k : taskPowerMetrics.get(task).keySet()) {
                    if (k == ProfilerType.POWER_USAGE_mW) {
                        String v = taskPowerMetrics.get(task).get(k);
                        if (v != null && !"n/a".equals(v)) {
                            needFallback = false;
                        }
                    }
                }
            }
            if (needFallback && isCpuDeviceString(deviceStr)) {
                Long sumUj = raplSumEnergyUj.get(task);
                Long sumNs = raplSumTimeNs.get(task);
                if (sumUj != null && sumNs != null && sumUj > 0 && sumNs > 0) {
                    double sum_mJ = sumUj / 1000.0;
                    double sum_ms = sumNs / 1e6;
                    long avg_mW = (sum_ms > 0.0) ? Math.round((sum_mJ / sum_ms) * 1000.0) : -1L;
                    setTaskPowerUsage(ProfilerType.POWER_USAGE_mW, task, avg_mW);
                    if (raplDebug) {
                        System.out.println("[RAPL-DEBUG] fallback avg power(mW)=" + avg_mW + " task=" + task);
                    }
                }
            }

            if (taskPowerMetrics.containsKey(task)) {
                boolean isCpu = isCpuDeviceString(deviceStr);
                for (Map.Entry<ProfilerType, String> e : taskPowerMetrics.get(task).entrySet()) {
                    String keyName;
                    if (e.getKey() == ProfilerType.POWER_USAGE_mW) {
                        keyName = isCpu ? "CPU_POWER_USAGE_mW" : "GPU_POWER_USAGE_mW";
                    } else {
                        keyName = e.getKey().toString();
                    }
                    json.append(indent.toString())
                        .append("\"").append(keyName).append("\": ")
                        .append("\"").append(jsonEscape(e.getValue())).append("\",\n");
                }
            }

            HashMap<ProfilerType, Long> timers = taskTimers.get(task);
            if (timers != null) {
                for (ProfilerType p2 : timers.keySet()) {
                    json.append(indent.toString()).append("\"").append(p2).append("\": ")
                        .append("\"").append(timers.get(p2)).append("\",\n");
                }
            }


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

    private static boolean isCpuDeviceString(String deviceStr) {
        if (deviceStr == null) return false;
        String d = deviceStr.toLowerCase();
        return d.contains("cpu") || d.contains("pthread") || d.contains("cl_device_type_cpu");
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
        raplSumEnergyUj.clear();
        raplSumTimeNs.clear();
        indent = new StringBuilder("");
    }

    @Override
    public synchronized void setTaskTimer(ProfilerType type, String taskID, long timer) {
        if (!taskTimers.containsKey(taskID)) {
            taskTimers.put(taskID, new HashMap<>());
        }
        taskTimers.get(taskID).put(type, timer);

        // --- auto-stop when kernel time is written (RAPL) ---

        // --- auto-stop when kernel time is written (RAPL) ---
        if (type == ProfilerType.TASK_KERNEL_TIME && enableRapl && raplReader != null) {

            // 0)
            if (raplStartEnergyUj.containsKey(taskID)) {
                //
                if (raplDebug) System.out.println("[RAPL-DEBUG] auto-stop (this file) direct task=" + taskID);
                stop(ProfilerType.TASK_KERNEL_TIME, taskID);
                return;
            }

            // 1)
            String base = raplBaseOf(taskID);
            String cand = raplLatestKeyByBase.get(base);
            if (cand != null && raplStartEnergyUj.containsKey(cand)) {
                if (raplDebug) System.out.println("[RAPL-DEBUG] auto-stop (this file) remap base=" + base + " task=" + taskID + " -> " + cand);
                stop(ProfilerType.TASK_KERNEL_TIME, cand);
                return;
            }

            // 2)
            String best = null;
            long bestT0 = Long.MIN_VALUE;
            java.util.ArrayList<String> keysSnapshot = new java.util.ArrayList<>(raplStartEnergyUj.keySet());
            for (String k : keysSnapshot) {
                if (raplBaseOf(k).equals(base)) {
                    Long t0 = raplStartTimeNs.get(k);
                    if (best == null) {
                        best = k;
                        bestT0 = (t0 != null) ? t0 : Long.MIN_VALUE;
                    } else if (t0 != null && t0 > bestT0) {
                        best = k;
                        bestT0 = t0;
                    }

                    if (raplDebug) System.out.println("[RAPL-DEBUG] auto-stop (this file) candidate k=" + k + " t0=" + t0);
                }
            }

            if (best != null) {
                if (raplDebug) System.out.println("[RAPL-DEBUG] auto-stop (this file) scan base=" + base + " task=" + taskID + " -> " + best);
                stop(ProfilerType.TASK_KERNEL_TIME, best);
            } else if (raplDebug) {
                StringBuilder b = new StringBuilder();
                for (String k : keysSnapshot) {
                    b.append(k).append("(base=").append(raplBaseOf(k)).append(") ");
                }
                System.out.println("[RAPL-DEBUG] auto-stop (this file) no baseline for task=" + taskID + " base=" + base + " keys=" + b);
            }
        }





    }

    @Override
    public synchronized void setTaskPowerUsage(ProfilerType type, String taskID, long power) {
        if (!taskPowerMetrics.containsKey(taskID)) {
            taskPowerMetrics.put(taskID, new HashMap<>());
        }
        if (power >= 0) {
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

