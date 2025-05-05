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

import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.benchmarks.BenchmarkDriver;
import uk.ac.manchester.tornado.benchmarks.ComputeKernels;

import static uk.ac.manchester.tornado.benchmarks.ComputeKernels.generateRandomGraph;

/**
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado -m tornado.benchmarks/uk.ac.manchester.tornado.benchmarks.BenchmarkRunner bfs
 * </code>
 */
public class BFSTornado extends BenchmarkDriver {

    private int size;
    IntArray vertices;
    IntArray verticesJava;
    IntArray adjacencyMatrix;
    IntArray modify;
    IntArray modifyJava;
    IntArray currentDepth;
    private static final int ROOT_NODE = 0;

    public BFSTornado(int iterations, int size) {
        super(iterations);
        this.size = size;
    }

    @Override
    public void setUp() {
        vertices = new IntArray(size);
        verticesJava = new IntArray(size);
        modifyJava = new IntArray(size);
        adjacencyMatrix = new IntArray(size * size);
        modify = new IntArray(1);
        currentDepth = new IntArray(1);

        modify.init(1);
        currentDepth.init(0);

        generateRandomGraph(adjacencyMatrix, size, ROOT_NODE);

        TaskGraph taskGraph = new TaskGraph("benchmark") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, vertices, adjacencyMatrix, modify, currentDepth) //
                .task("bfs", ComputeKernels::bfs, vertices, adjacencyMatrix, size, modify, currentDepth) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, vertices, modify);

        immutableTaskGraph = taskGraph.snapshot();
        executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.withDefaultScheduler() //
                .withWarmUp();
    }

    @Override
    public void tearDown() {
        executionResult.getProfilerResult().dumpProfiles();
        vertices = null;
        verticesJava = null;
        adjacencyMatrix = null;
        modify = null;
        modifyJava = null;
        currentDepth = null;
        super.tearDown();
    }

    @Override
    public boolean validate(TornadoDevice device) {
        boolean valid = true;
        modifyJava.init(1);
        currentDepth.init(0);
        generateRandomGraph(adjacencyMatrix, size, ROOT_NODE);

        TaskGraph taskGraph = new TaskGraph("benchmark") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, vertices, adjacencyMatrix, modify, currentDepth) //
                .task("bfs", ComputeKernels::bfs, vertices, adjacencyMatrix, size, modify, currentDepth) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, vertices, modify);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executor = new TornadoExecutionPlan(immutableTaskGraph);
        executor.withDevice(device).withDefaultScheduler().execute();

        // Sequential
        ComputeKernels.bfs(verticesJava, adjacencyMatrix, size, modifyJava, currentDepth);

        for (int i = 0; i < vertices.getSize(); i++) {
            if (vertices.get(i) != verticesJava.get(i)) {
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
