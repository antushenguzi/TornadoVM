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
package uk.ac.manchester.tornado.benchmarks.blackAndWhiteFilter;

import java.util.Random;

import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.benchmarks.BenchmarkDriver;
import uk.ac.manchester.tornado.benchmarks.ComputeKernels;

/**
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado -m tornado.benchmarks/uk.ac.manchester.tornado.benchmarks.BenchmarkRunner blackAndWhiteFilter
 * </code>
 */
public class BlackAndWhiteFilterTornado extends BenchmarkDriver {

    private int size;
    IntArray imageRGB;

    public BlackAndWhiteFilterTornado(int iterations, int size) {
        super(iterations);
        this.size = size;
    }

    @Override
    public void setUp() {
        int w = size;
        int h = size;

        imageRGB = new IntArray(w * h);

        Random r = new Random();
        // data initialisation
        for (int i = 0; i < w; i++) {
            for (int j = 0; j < h; j++) {
                int rgb = r.nextInt(255);
                imageRGB.set(i * h + j, (rgb >> 24) & 0xFF);
            }
        }

        taskGraph = new TaskGraph("benchmark") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, imageRGB) //
                .task("blackAndWhite", ComputeKernels::blackAndWhiteCompute, imageRGB, size, size) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, imageRGB);

        immutableTaskGraph = taskGraph.snapshot();
        executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.withDefaultScheduler() //
                .withWarmUp();
    }

    @Override
    public void tearDown() {
        executionResult.getProfilerResult().dumpProfiles();
        imageRGB = null;
        super.tearDown();
    }

    @Override
    public boolean validate(TornadoDevice device) {
        boolean valid = true;
        int w = size;
        int h = size;

        IntArray imageRGBParallel = new IntArray(w * h);
        IntArray imageRGBSequential = new IntArray(w * h);

        Random r = new Random();
        for (int i = 0; i < w; i++) {
            for (int j = 0; j < h; j++) {
                int rgb = r.nextInt(255);
                imageRGBParallel.set(i * h + j, (rgb >> 24) & 0xFF);
                imageRGBSequential.set(i * h + j, (rgb >> 24) & 0xFF);
            }
        }

        TaskGraph parallelFilter = new TaskGraph("blur") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, imageRGBParallel) //
                .task("blackAndWhite", ComputeKernels::blackAndWhiteCompute, imageRGBParallel, size, size) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, imageRGBParallel);

        ImmutableTaskGraph immutableTaskGraph1 = parallelFilter.snapshot();
        TornadoExecutionPlan executor = new TornadoExecutionPlan(immutableTaskGraph1);
        executor.withDevice(device).withDefaultScheduler().execute();

        // Sequential
        ComputeKernels.blackAndWhiteCompute(imageRGBSequential, size, size);

        for (int i = 0; i < imageRGBParallel.getSize(); i++) {
            if (imageRGBParallel.get(i) != imageRGBSequential.get(i)) {
                valid = false;
                break;
            }
        }

        return valid;
    }

    @Override
    public void runBenchmark(TornadoDevice device) {
        executionResult = executionPlan.withDevice(device).execute();
    }
}
