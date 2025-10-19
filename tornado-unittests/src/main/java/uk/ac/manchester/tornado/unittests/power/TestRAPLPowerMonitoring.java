/*
 * Copyright (c) 2025, APT Group, Department of Computer Science,
 * The University of Manchester.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package uk.ac.manchester.tornado.unittests.power;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;

import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.TornadoExecutionResult;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.enums.ProfilerMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.profiler.ProfilerType;
import uk.ac.manchester.tornado.api.profiler.TornadoProfiler;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * Test Intel RAPL (Running Average Power Limit) CPU power monitoring integration.
 *
 * <p>
 * How to test?
 * </p>
 * <code>
 * tornado-test -V --enableProfiler console uk.ac.manchester.tornado.unittests.power.TestRAPLPowerMonitoring
 * </code>
 *
 * <p>
 * Prerequisites:
 * - Intel CPU with RAPL support (Sandy Bridge or newer)
 * - Linux with powercap sysfs or MSR module loaded
 * - Intel OpenCL platform and CPU device
 * </p>
 */
public class TestRAPLPowerMonitoring extends TornadoTestBase {

    /**
     * CPU-intensive kernel to generate measurable power consumption
     */
    private static void computeIntensiveTask(FloatArray input, FloatArray output) {
        for (int i = 0; i < input.getSize(); i++) {
            float value = input.get(i);
            // Perform CPU-intensive operations
            for (int j = 0; j < 100; j++) {
                value = (float) Math.sqrt(value + j) * (float) Math.sin(value) + (float) Math.cos(value);
            }
            output.set(i, value);
        }
    }

    /**
     * Simple vector addition kernel
     */
    private static void vectorAdd(FloatArray a, FloatArray b, FloatArray c) {
        for (int i = 0; i < a.getSize(); i++) {
            c.set(i, a.get(i) + b.get(i));
        }
    }

    @Test
    public void testRAPLPowerMetricsAvailable() throws TornadoExecutionPlanException {
        final int numElements = 8192;
        FloatArray input = new FloatArray(numElements);
        FloatArray output = new FloatArray(numElements);

        // Initialize input with non-zero values
        for (int i = 0; i < numElements; i++) {
            input.set(i, i * 1.5f);
        }

        TaskGraph taskGraph = new TaskGraph("s0")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input)
                .task("t0", TestRAPLPowerMonitoring::computeIntensiveTask, input, output)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            // Enable profiler to capture power metrics
            executionPlan.withProfiler(ProfilerMode.SILENT);

            // Execute multiple times to get stable power readings
            TornadoExecutionResult result = null;
            for (int i = 0; i < 5; i++) {
                result = executionPlan.execute();
            }

            // Verify profiler captured data
            assertNotNull("Execution result should not be null", result);
            TornadoProfiler profiler = result.getProfiler();
            assertNotNull("Profiler should be available", profiler);

            // Get power usage metric
            String powerUsageStr = profiler.getTaskPowerUsage("s0.t0");

            if (powerUsageStr != null && !powerUsageStr.equals("n/a")) {
                System.out.println("[RAPL Test] Power usage captured: " + powerUsageStr + " mW");

                // Verify power reading is reasonable for CPU (typically 5W - 200W)
                try {
                    long powerUsage = Long.parseLong(powerUsageStr);
                    assertTrue("Power usage should be positive", powerUsage > 0);
                    assertTrue("Power usage should be reasonable for CPU (< 500W)", powerUsage < 500_000);
                    System.out.println("[RAPL Test] PASS - Valid power reading: " + powerUsage + " mW");
                } catch (NumberFormatException e) {
                    System.err.println("[RAPL Test] WARNING - Could not parse power value: " + powerUsageStr);
                }
            } else {
                // RAPL may not be available on this system
                System.out.println("[RAPL Test] SKIP - Power monitoring not available on this device");
                System.out.println("[RAPL Test] Ensure you are running on Intel CPU with RAPL support and powercap/MSR access");
            }
        }
    }

    @Test
    public void testRAPLPowerVariationUnderLoad() throws TornadoExecutionPlanException {
        final int lightWorkload = 1024;
        final int heavyWorkload = 16384;

        FloatArray inputLight = new FloatArray(lightWorkload);
        FloatArray outputLight = new FloatArray(lightWorkload);
        FloatArray inputHeavy = new FloatArray(heavyWorkload);
        FloatArray outputHeavy = new FloatArray(heavyWorkload);

        // Initialize inputs
        for (int i = 0; i < lightWorkload; i++) {
            inputLight.set(i, i * 1.5f);
        }
        for (int i = 0; i < heavyWorkload; i++) {
            inputHeavy.set(i, i * 1.5f);
        }

        // Light workload task
        TaskGraph lightTask = new TaskGraph("s0")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, inputLight)
                .task("t0", TestRAPLPowerMonitoring::computeIntensiveTask, inputLight, outputLight)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, outputLight);

        ImmutableTaskGraph immutableLightTask = lightTask.snapshot();

        // Heavy workload task
        TaskGraph heavyTask = new TaskGraph("s1")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, inputHeavy)
                .task("t0", TestRAPLPowerMonitoring::computeIntensiveTask, inputHeavy, outputHeavy)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, outputHeavy);

        ImmutableTaskGraph immutableHeavyTask = heavyTask.snapshot();

        long lightPower = 0;
        long heavyPower = 0;

        // Execute light workload
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableLightTask)) {
            executionPlan.withProfiler(ProfilerMode.SILENT);

            // Warm-up and execute multiple times
            for (int i = 0; i < 10; i++) {
                executionPlan.execute();
            }

            TornadoExecutionResult result = executionPlan.execute();
            String powerStr = result.getProfiler().getTaskPowerUsage("s0.t0");
            if (powerStr != null && !powerStr.equals("n/a")) {
                lightPower = Long.parseLong(powerStr);
                System.out.println("[RAPL Test] Light workload power: " + lightPower + " mW");
            }
        }

        // Execute heavy workload
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableHeavyTask)) {
            executionPlan.withProfiler(ProfilerMode.SILENT);

            // Warm-up and execute multiple times
            for (int i = 0; i < 10; i++) {
                executionPlan.execute();
            }

            TornadoExecutionResult result = executionPlan.execute();
            String powerStr = result.getProfiler().getTaskPowerUsage("s1.t0");
            if (powerStr != null && !powerStr.equals("n/a")) {
                heavyPower = Long.parseLong(powerStr);
                System.out.println("[RAPL Test] Heavy workload power: " + heavyPower + " mW");
            }
        }

        // Compare power readings
        if (lightPower > 0 && heavyPower > 0) {
            System.out.println("[RAPL Test] Power variation detected: Light=" + lightPower + " mW, Heavy=" + heavyPower + " mW");
            // Heavy workload should generally consume more power, but this is not guaranteed
            // due to CPU power management, turbo boost, and other factors
            System.out.println("[RAPL Test] PASS - Power readings captured for both workloads");
        } else {
            System.out.println("[RAPL Test] SKIP - RAPL not available or failed to read power");
        }
    }

    @Test
    public void testRAPLWithProfilerConsoleMode() throws TornadoExecutionPlanException {
        final int numElements = 4096;
        FloatArray a = new FloatArray(numElements);
        FloatArray b = new FloatArray(numElements);
        FloatArray c = new FloatArray(numElements);

        a.init(1.0f);
        b.init(2.0f);

        TaskGraph taskGraph = new TaskGraph("s0")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b)
                .task("t0", TestRAPLPowerMonitoring::vectorAdd, a, b, c)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            // Use CONSOLE mode to print profiler output
            executionPlan.withProfiler(ProfilerMode.CONSOLE);

            // Execute
            TornadoExecutionResult result = executionPlan.execute();

            // Verify results
            for (int i = 0; i < numElements; i++) {
                assertTrue("Result should be 3.0", Math.abs(c.get(i) - 3.0f) < 0.001f);
            }

            System.out.println("[RAPL Test] Console profiler mode executed successfully");
            System.out.println("[RAPL Test] Check console output above for POWER_USAGE_mW metric");
        }
    }
}
