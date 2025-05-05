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
package uk.ac.manchester.tornado.benchmarks.fft;

import static uk.ac.manchester.tornado.benchmarks.ComputeKernels.generateRandomGraph;

import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
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
 * tornado -m tornado.benchmarks/uk.ac.manchester.tornado.benchmarks.BenchmarkRunner fft
 * </code>
 */
public class FFTTornado extends BenchmarkDriver {

    private int size;
    IntArray outputJava;
    IntArray outputTornado;
    IntArray factors;
    IntArray dimArr;

    public FFTTornado(int iterations, int size) {
        super(iterations);
        this.size = size;
    }

    @Override
    public void setUp() {
        outputJava = new IntArray(size);
        outputTornado = new IntArray(size);
        factors = new IntArray(size);
        dimArr = new IntArray(2);

        Random r = new Random();
        // data initialisation
        for (int i = 0; i < size; i++) {
            int randomValue = r.nextInt(255);
            outputTornado.set(i, 1);
            factors.set(i, randomValue);
        }

        dimArr.init(size);

        TaskGraph taskGraph = new TaskGraph("benchmark") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, factors, dimArr) //
                .task("bfs", ComputeKernels::computeFFT, outputTornado, factors, dimArr) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, outputTornado);

        immutableTaskGraph = taskGraph.snapshot();
        executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.withDefaultScheduler() //
                .withWarmUp();
    }

    @Override
    public void tearDown() {
        executionResult.getProfilerResult().dumpProfiles();
        outputJava = null;
        outputTornado = null;
        factors = null;
        dimArr = null;
        super.tearDown();
    }

    @Override
    public boolean validate(TornadoDevice device) {
        boolean valid = true;

        Random r = new Random();
        // data initialisation
        for (int i = 0; i < size; i++) {
            int randomValue = r.nextInt(255);
            outputTornado.set(i, 1);
            outputJava.set(i, 1);
            factors.set(i, randomValue);
        }

        dimArr.init(size);

        TaskGraph taskGraph = new TaskGraph("benchmark") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, factors, dimArr) //
                .task("bfs", ComputeKernels::computeFFT, outputTornado, factors, dimArr) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, outputTornado);

        immutableTaskGraph = taskGraph.snapshot();
        executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.withDevice(device).withDefaultScheduler().execute();

        // Sequential
        ComputeKernels.computeFFT(outputJava, factors, dimArr);

        for (int i = 0; i < outputJava.getSize(); i++) {
            if (outputTornado.get(i) != outputJava.get(i)) {
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
