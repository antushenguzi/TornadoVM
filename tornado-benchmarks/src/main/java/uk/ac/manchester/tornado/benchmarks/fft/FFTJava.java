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

import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.benchmarks.BenchmarkDriver;
import uk.ac.manchester.tornado.benchmarks.ComputeKernels;

import java.util.Random;

public class FFTJava extends BenchmarkDriver {

    private int size;
    IntArray outputJava;
    IntArray outputTornado;
    IntArray factors;
    IntArray dimArr;

    public FFTJava(int iterations, int size) {
        super(iterations);
        this.size = size;
    }

    @Override
    public void setUp() {
        outputJava = new IntArray(size);
        factors = new IntArray(size);
        dimArr = new IntArray(2);

        Random r = new Random();
        // data initialisation
        for (int i = 0; i < size; i++) {
            int randomValue = r.nextInt(255);
            outputJava.set(i, 1);
            factors.set(i, randomValue);
        }

        dimArr.init(size);
    }

    @Override
    public boolean validate(TornadoDevice device) {
        return true;
    }

    @Override
    public void runBenchmark(TornadoDevice device) {
        ComputeKernels.computeFFT(outputJava, factors, dimArr);
    }
}
