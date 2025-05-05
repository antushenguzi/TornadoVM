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
package uk.ac.manchester.tornado.benchmarks.histogram;

import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.benchmarks.BenchmarkDriver;
import uk.ac.manchester.tornado.benchmarks.ComputeKernels;

import java.util.Random;

public class HistogramJava extends BenchmarkDriver {

    private static final int NUM_BINS = 4;
    private int size;
    private IntArray input;
    private IntArray output;
    private KernelContext context;

    public HistogramJava(int iterations, int size) {
        super(iterations);
        this.size = size;
    }

    @Override
    public void setUp() {
        input = new IntArray(size);
        output = new IntArray(size);
        Random rand = new Random();
        context = new KernelContext();

        for (int i = 0; i < size; i++) {
            input.set(i, rand.nextInt(NUM_BINS));
        }
    }

    @Override
    public boolean validate(TornadoDevice device) {
        return true;
    }

    @Override
    public void tearDown() {
        output = null;
    }

    @Override
    public void runBenchmark(TornadoDevice device) {
        ComputeKernels.histogram(context, input, output);
    }
}
