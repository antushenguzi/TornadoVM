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

package uk.ac.manchester.tornado.benchmarks.bfs;

import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.benchmarks.BenchmarkDriver;
import uk.ac.manchester.tornado.benchmarks.ComputeKernels;

import static uk.ac.manchester.tornado.benchmarks.ComputeKernels.generateRandomGraph;

public class BFSJava extends BenchmarkDriver {

    private int size;
    IntArray vertices;
    IntArray verticesJava;
    IntArray adjacencyMatrix;
    IntArray modify;
    IntArray modifyJava;
    IntArray currentDepth;
    private static final int ROOT_NODE = 0;

    public BFSJava(int iterations, int size) {
        super(iterations);
        this.size = size;
    }

    @Override
    public void setUp() {
        vertices = new IntArray(size);
        verticesJava = new IntArray(size);
        adjacencyMatrix = new IntArray(size * size);
        modify = new IntArray(1);
        modifyJava = new IntArray(1);
        currentDepth = new IntArray(1);

        modify.init(1);
        modifyJava.init(1);
        currentDepth.init(0);

        generateRandomGraph(adjacencyMatrix, size, ROOT_NODE);
    }

    @Override
    public boolean validate(TornadoDevice device) {
        return true;
    }

    @Override
    public void runBenchmark(TornadoDevice device) {
        ComputeKernels.bfs(verticesJava, adjacencyMatrix, size, modifyJava, currentDepth);
    }
}
