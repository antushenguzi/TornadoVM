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

import static uk.ac.manchester.tornado.benchmarks.ComputeKernels.vectorAdd;

import java.util.Random;

import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.types.collections.VectorFloat4;
import uk.ac.manchester.tornado.api.types.vectors.Float4;
import uk.ac.manchester.tornado.benchmarks.BenchmarkDriver;

public class VectorAdditionFloat4Java extends BenchmarkDriver {

    private final int size;

    private VectorFloat4 a;
    private VectorFloat4 b;
    private VectorFloat4 c;

    public VectorAdditionFloat4Java(int iterations, int size) {
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
        vectorAdd(a, b, c);
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
