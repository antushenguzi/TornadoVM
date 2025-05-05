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
package uk.ac.manchester.tornado.benchmarks.vectorAdditionFloat4;

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
import uk.ac.manchester.tornado.api.types.collections.VectorFloat4;
import uk.ac.manchester.tornado.api.types.vectors.Float4;
import uk.ac.manchester.tornado.benchmarks.BenchmarkDriver;
import uk.ac.manchester.tornado.benchmarks.ComputeKernels;

/**
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado -m tornado.benchmarks/uk.ac.manchester.tornado.benchmarks.BenchmarkRunner vectorAdditionFloat4
 * </code>
 */
public class VectorAdditionFloat4Tornado extends BenchmarkDriver {

    private final int size;
    private WorkerGrid worker;
    private VectorFloat4 a;
    private VectorFloat4 b;
    private VectorFloat4 c;
    private GridScheduler grid;
    private boolean USE_GRID = Boolean.parseBoolean(TornadoRuntimeProvider.getProperty("usegrid", "False"));

    public VectorAdditionFloat4Tornado(int iterations, int size) {
        super(iterations);
        this.size = size;
    }

    @Override
    public void setUp() {
        a = new VectorFloat4(size);
        b = new VectorFloat4(size);
        c = new VectorFloat4(size);

        final Random random = new Random();

        for (int i = 0; i < size; i++) {
            float x = random.nextFloat();
            a.set(i, new Float4(x, x, x, x));
            b.set(i, new Float4(2 * x, 2 * x, 2 * x, 2 * x));
        }

        if (USE_GRID) {
            worker = new WorkerGrid2D(size, size);
            worker.setLocalWork(16, 16, 1);
            grid = new GridScheduler();
            grid.addWorkerGrid("benchmark.vectorAdditionFloat4", worker);
        }

        taskGraph = new TaskGraph("benchmark");

        taskGraph.transferToDevice(DataTransferMode.EVERY_EXECUTION, a, b);
        taskGraph.task("vectorAdditionFloat4", ComputeKernels::vectorAdd, a, b, c);
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

        final VectorFloat4 result = new VectorFloat4(size);
        boolean val = true;

        runBenchmark(device);
        executionPlan.clearProfiles();
        vectorAdd(a, b, result);

        for (int i = 0; i < size; i++) {
            if (abs(result.get(i).getW() - c.get(i).getW()) > 0.01) {
                val = false;
                break;
            }
            if (abs(result.get(i).getX() - c.get(i).getX()) > 0.01) {
                val = false;
                break;
            }
            if (abs(result.get(i).getY() - c.get(i).getY()) > 0.01) {
                val = false;
                break;
            }
            if (abs(result.get(i).getZ() - c.get(i).getZ()) > 0.01) {
                val = false;
                break;
            }
        }
        System.out.printf("Number validation: " + val + "\n");
        return val;
    }

}
