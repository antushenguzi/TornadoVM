package uk.ac.manchester.tornado.drivers.opencl.util;

import java.lang.reflect.Method;

public final class ProfilerShim {
    private static final String TP_CLASS = "uk.ac.manchester.tornado.runtime.common.profiler.TimeProfiler";
    private static final String PT_CLASS = "uk.ac.manchester.tornado.runtime.common.profiler.ProfilerType";

    private static volatile Object tp;        // TimeProfiler singleton
    private static volatile Class<?> ptClass; // ProfilerType class

    private ProfilerShim() {}

    private static Object tp() throws Exception {
        if (tp != null) return tp;
        Class<?> c = Class.forName(TP_CLASS);
        Method getInstance = c.getMethod("getInstance");
        tp = getInstance.invoke(null);
        return tp;
    }

    private static Class<?> pt() throws Exception {
        if (ptClass != null) return ptClass;
        ptClass = Class.forName(PT_CLASS);
        return ptClass;
    }

    @SuppressWarnings({"unchecked","rawtypes"})
    private static Object kernelType() throws Exception {
        Class<?> e = pt();
        return Enum.valueOf((Class<Enum>) e.asSubclass(Enum.class), "TASK_KERNEL_TIME");
    }

    public static void startKernel(String taskName, String deviceString) {
        try {
            Object tpi = tp();
            Class<?> c  = tpi.getClass();
            Class<?> pt = pt();

            // registerDeviceName(String, String)
            Method reg = c.getMethod("registerDeviceName", String.class, String.class);
            reg.invoke(tpi, taskName, deviceString);

            // start(ProfilerType, String)
            Method start = c.getMethod("start", pt, String.class);
            start.invoke(tpi, kernelType(), taskName);
        } catch (Throwable ignore) {
            System.err.println("[ProfilerShim] startKernel error: " + ignore);
        }
    }

    public static void stopKernel(String taskName) {
        try {
            Object tpi = tp();
            Class<?> c  = tpi.getClass();
            Class<?> pt = pt();

            // stop(ProfilerType, String)
            Method stop = c.getMethod("stop", pt, String.class);
            stop.invoke(tpi, kernelType(), taskName);
        } catch (Throwable ignore) {
            System.err.println("[ProfilerShim] startKernel error: " + ignore);
        }
    }
}
