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

import uk.ac.manchester.tornado.benchmarks.BenchmarkDriver;
import uk.ac.manchester.tornado.benchmarks.BenchmarkRunner;

public class Benchmark extends BenchmarkRunner {
    private int size;
    private int blockSize;

    @Override
    protected String getName() {
        return "histogram";
    }

    @Override
    protected String getIdString() {
        return String.format("%s-%d-%d-%d", getName(), iterations, size, blockSize);
    }

    @Override
    protected String getConfigString() {
        return String.format("size=%d", size);
    }

    @Override
    protected BenchmarkDriver getJavaDriver() {
        return new HistogramJava(iterations, size);
    }

    @Override
    protected BenchmarkDriver getTornadoDriver() {
        return new HistogramTornado(iterations, size, blockSize);
    }

    @Override
    public void parseArgs(String[] args) {
        if (args.length == 3) {
            iterations = Integer.parseInt(args[0]);
            size = Integer.parseInt(args[1]);
            blockSize = Integer.parseInt(args[2]);
        } else if (args.length == 2) {
            iterations = Integer.parseInt(args[0]);
            size = Integer.parseInt(args[1]);
            blockSize = 256;
        } else if (args.length == 1) {
            System.out.printf("Three arguments are needed: iterations size block size");
        } else {
            iterations = 15;
            size = 1048576;
            blockSize = 256;
        }
    }
}
