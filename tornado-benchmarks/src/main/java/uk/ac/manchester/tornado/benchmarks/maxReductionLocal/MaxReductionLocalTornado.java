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
package uk.ac.manchester.tornado.benchmarks.maxReductionLocal;

import java.util.Random;

import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.WorkerGrid;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.math.TornadoMath;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.benchmarks.BenchmarkDriver;
import uk.ac.manchester.tornado.benchmarks.ComputeReductions;

/**
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado -m tornado.benchmarks/uk.ac.manchester.tornado.benchmarks.BenchmarkRunner maxReductionLocal
 * </code>
 */
public class MaxReductionLocalTornado extends BenchmarkDriver {

    private int size;
    IntArray arrayA;
    IntArray arrayB;
    private static final int LOCAL_SIZE = 256;

    public MaxReductionLocalTornado(int iterations, int size) {
        super(iterations);
        this.size = size;
    }

    @Override
    public void setUp() {
        arrayA = new IntArray(size);
        arrayB = new IntArray(size);

        Random r = new Random();
        // data initialisation
        for (int i = 0; i < size; i++) {
            int randomNumber = r.nextInt(255);
            arrayA.set(i, randomNumber);
        }

        WorkerGrid worker = new WorkerGrid1D(size);
        GridScheduler gridScheduler = new GridScheduler("benchmark.maxReductionLocal", worker);
        KernelContext context = new KernelContext();
        taskGraph = new TaskGraph("benchmark") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, arrayA) //
                .task("maxReductionLocal", ComputeReductions::intReductionMaxLocalMemory, context, arrayA, arrayB) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, arrayB);

        // Change the Grid
        worker.setGlobalWork(size, 1, 1);
        worker.setLocalWork(LOCAL_SIZE, 1, 1);

        immutableTaskGraph = taskGraph.snapshot();
        executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.withGridScheduler(gridScheduler).withWarmUp();
    }

    @Override
    public void tearDown() {
        executionResult.getProfilerResult().dumpProfiles();
        arrayA = null;
        arrayB = null;
        super.tearDown();
    }

    @Override
    public boolean validate(TornadoDevice device) {
        boolean valid = true;

        arrayA = new IntArray(size);
        IntArray resultParallel = new IntArray(size);

        Random r = new Random();
        // data initialisation
        for (int i = 0; i < size; i++) {
            int randomNumber = r.nextInt(255);
            arrayA.set(i, randomNumber);
        }

        WorkerGrid worker = new WorkerGrid1D(size);
        GridScheduler gridScheduler = new GridScheduler("benchmark.maxReductionLocal", worker);
        KernelContext context = new KernelContext();
        taskGraph = new TaskGraph("benchmark") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, arrayA) //
                .task("maxReductionLocal", ComputeReductions::intReductionMaxLocalMemory, context, arrayA, resultParallel) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, resultParallel);

        // Change the Grid
        worker.setGlobalWork(size, 1, 1);
        worker.setLocalWork(LOCAL_SIZE, 1, 1);

        immutableTaskGraph = taskGraph.snapshot();
        executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.withGridScheduler(gridScheduler).execute();

        // Final SUM
        int finalSum = 0;
        for (int i = 0; i < resultParallel.getSize(); i++) {
            finalSum = TornadoMath.max(finalSum, resultParallel.get(i));
        }

        // Sequential
        int resultSequential = ComputeReductions.computeMaxSequential(arrayA);

        if (finalSum != resultSequential) {
            valid = false;
        }

        return valid;
    }

    @Override
    public void runBenchmark(TornadoDevice device) {
        executionResult = executionPlan.withDevice(device).execute();
    }
}
