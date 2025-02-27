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
package uk.ac.manchester.tornado.benchmarks.matrixMultiplication2D;

import uk.ac.manchester.tornado.benchmarks.BenchmarkDriver;
import uk.ac.manchester.tornado.benchmarks.BenchmarkRunner;

public class Benchmark extends BenchmarkRunner {

    private int size;

    @Override
    public void parseArgs(String[] args) {
        if (args.length == 2) {
            iterations = Integer.parseInt(args[0]);
            size = Integer.parseInt(args[1]);
        } else {
            iterations = 20;
            size = 512;
        }
    }

    @Override
    protected String getName() {

        return "matrixMultiplication2D";

    }

    @Override
    protected String getIdString() {
        return String.format("%s-%d-%d-%d", getName(), iterations, size, size);
    }

    @Override
    protected String getConfigString() {
        return String.format("width=%d, height=%d", size, size);
    }

    @Override
    protected BenchmarkDriver getJavaDriver() {
        return new MatrixMultiplicationJava(iterations, size);
    }

    @Override
    protected BenchmarkDriver getTornadoDriver() {
        return new MatrixMultiplicationTornado(iterations, size);
    }

}
