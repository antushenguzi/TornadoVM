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
package uk.ac.manchester.tornado.benchmarks.matrixVectorMultiplication;

import static uk.ac.manchester.tornado.benchmarks.LinearAlgebraArrays.matrixVectorMultiplication;

import java.util.Random;

import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.benchmarks.BenchmarkDriver;

public class MatrixVectorMultiplicationJava extends BenchmarkDriver {

    private final int size;

    private FloatArray a;
    private FloatArray b;
    private FloatArray c;

    public MatrixVectorMultiplicationJava(int iterations, int size) {
        super(iterations);
        this.size = size;
    }

    @Override
    public void setUp() {
        a = new FloatArray(size * size);
        b = new FloatArray(size * size);
        c = new FloatArray(size * size);

        final Random random = new Random();

        for (int i = 0; i < size; i++) {
            a.set(i * (size + 1), 1);
        }

        for (int i = 0; i < size * size; i++) {
            b.set(i, random.nextFloat());
        }

    }

    @Override
    public void tearDown() {
        a = null;
        b = null;
        c = null;
        super.tearDown();
    }

    @Override
    public void runBenchmark(TornadoDevice device) {
        matrixVectorMultiplication(a, b, c, size);
    }

    @Override
    public void barrier() {

    }

    @Override
    public boolean validate(TornadoDevice device) {
        return true;
    }

    public void printSummary() {
        System.out.printf("id=java-serial, elapsed=%f, per iteration=%f\n", getElapsed(), getElapsedPerIteration());
    }

}
