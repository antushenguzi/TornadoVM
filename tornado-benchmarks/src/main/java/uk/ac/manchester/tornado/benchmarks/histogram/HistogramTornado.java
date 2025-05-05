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
package uk.ac.manchester.tornado.benchmarks.histogram;

import static uk.ac.manchester.tornado.api.math.TornadoMath.abs;

import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.WorkerGrid;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.benchmarks.BenchmarkDriver;
import uk.ac.manchester.tornado.benchmarks.ComputeKernels;

import java.util.Random;

/**
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado -m tornado.benchmarks/uk.ac.manchester.tornado.benchmarks.BenchmarkRunner histogram
 * </code>
 */
public class HistogramTornado extends BenchmarkDriver {

    private static final int NUM_BINS = 4;
    private int size;
    private int blockSize;
    private IntArray input;
    private IntArray output;
    private KernelContext context;
    private GridScheduler gridScheduler;

    public HistogramTornado(int iterations, int size, int blockSize) {
        super(iterations);
        this.size = size;
        this.blockSize = blockSize;
    }

    private void initData() {
        input = new IntArray(size);
        output = new IntArray(size);
        context = new KernelContext();
        Random rand = new Random();

        for (int i = 0; i < size; i++) {
            input.set(i, rand.nextInt(NUM_BINS));
        }
    }

    @Override
    public void setUp() {
        initData();
        taskGraph = new TaskGraph("benchmark") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("t0", ComputeKernels::histogramKernel, context, input, output) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

        WorkerGrid workerGrid = new WorkerGrid1D(size);
        workerGrid.setGlobalWork(size, 1, 1);
        workerGrid.setLocalWork(blockSize, 1, 1);
        gridScheduler = new GridScheduler("benchmark.t0", workerGrid);
        immutableTaskGraph = taskGraph.snapshot();
        executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.withWarmUp().withGridScheduler(gridScheduler);
    }

    @Override
    public boolean validate(TornadoDevice device) {
        boolean validation = true;
        IntArray outputJava = new IntArray(size);

        executionPlan.withDevice(device) //
                .withWarmUp() //
                .withGridScheduler(gridScheduler) //
                .execute();

        ComputeKernels.histogram(context, input, outputJava);

        executionPlan.clearProfiles();

        for (int i = 0; i < size; i++) {
            if (abs(outputJava.get(i) - output.get(i)) > 0.01) {
                validation = false;
                break;
            }
        }
        System.out.print("Is correct?: " + validation + "\n");
        return validation;
    }

    @Override
    public void tearDown() {
        executionResult.getProfilerResult().dumpProfiles();

        output = null;

        executionPlan.resetDevice();
        super.tearDown();
    }

    @Override
    public void runBenchmark(TornadoDevice device) {
        executionResult = executionPlan.withGridScheduler(gridScheduler).withDevice(device).execute();
    }
}
