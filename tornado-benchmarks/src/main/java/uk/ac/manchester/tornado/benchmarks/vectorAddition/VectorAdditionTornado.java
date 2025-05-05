/*
 * Copyright (c) 2013-2023, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.benchmarks.vectorAddition;

import static uk.ac.manchester.tornado.api.math.TornadoMath.abs;
import static uk.ac.manchester.tornado.benchmarks.ComputeKernels.vectorAdd;

import java.util.Random;

import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.WorkerGrid;
import uk.ac.manchester.tornado.api.WorkerGrid2D;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.runtime.TornadoRuntimeProvider;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.benchmarks.BenchmarkDriver;
import uk.ac.manchester.tornado.benchmarks.ComputeKernels;
import uk.ac.manchester.tornado.benchmarks.LinearAlgebraArrays;

/**
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado -m tornado.benchmarks/uk.ac.manchester.tornado.benchmarks.BenchmarkRunner vectorAddition
 * </code>
 */
public class VectorAdditionTornado extends BenchmarkDriver {

    private final int size;
    private WorkerGrid worker;
    private FloatArray a;
    private FloatArray b;
    private FloatArray c;
    private GridScheduler grid;
    private boolean USE_GRID = Boolean.parseBoolean(TornadoRuntimeProvider.getProperty("usegrid", "False"));

    public VectorAdditionTornado(int iterations, int size) {
        super(iterations);
        this.size = size;
    }

    @Override
    public void setUp() {
        a = new FloatArray(size);
        b = new FloatArray(size);
        c = new FloatArray(size);

        final Random random = new Random();

        for (int i = 0; i < size; i++) {
            a.set(i, random.nextFloat());
            b.set(i, random.nextFloat());
        }

        if (USE_GRID) {
            worker = new WorkerGrid2D(size, size);
            worker.setLocalWork(16, 16, 1);
            grid = new GridScheduler();
            grid.addWorkerGrid("benchmark.vectorAddition", worker);
        }

        taskGraph = new TaskGraph("benchmark");

        taskGraph.transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b);
        taskGraph.task("vectorAddition", ComputeKernels::vectorAdd, a, b, c);
        taskGraph.transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        executionPlan = new TornadoExecutionPlan(taskGraph.snapshot());
        executionPlan.withWarmUp();
    }

    @Override
    public void tearDown() {
        executionResult.getProfilerResult().dumpProfiles();

        a = null;
        b = null;
        c = null;

        executionPlan.resetDevice();
        super.tearDown();
    }

    @Override
    public void runBenchmark(TornadoDevice device) {
        if (grid != null) {
            executionPlan.withGridScheduler(grid);
        }
        executionResult = executionPlan.withDevice(device).execute();
    }

    @Override
    public boolean validate(TornadoDevice device) {

        final FloatArray result = new FloatArray(size);
        boolean val = true;

        runBenchmark(device);
        executionPlan.clearProfiles();
        vectorAdd(a, b, result);

        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                if (abs(result.get(i) - c.get(i)) > 0.01) {
                    val = false;
                    break;
                }
            }
        }
        System.out.printf("Number validation: " + val + "\n");
        return val;
    }

}
