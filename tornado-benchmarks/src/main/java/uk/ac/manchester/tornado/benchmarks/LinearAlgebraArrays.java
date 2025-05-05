/*
 * Copyright (c) 2013-2023, 2025, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.benchmarks;

import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.types.arrays.DoubleArray;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.api.types.matrix.Matrix2DFloat;
import uk.ac.manchester.tornado.api.types.matrix.Matrix2DFloat4;
import uk.ac.manchester.tornado.api.types.vectors.Float4;

public class LinearAlgebraArrays {
    // CHECKSTYLE:OFF

    public static void saxpy(float alpha, FloatArray x, FloatArray y) {
        for (@Parallel int i = 0; i < y.getSize(); i++) {
            y.set(i, y.get(i) + alpha * x.get(i));
        }
    }

    public static void sgemv(int M, int N, FloatArray A, FloatArray X, FloatArray Y) {
        for (@Parallel int i = 0; i < M; i++) {
            float y0 = 0f;
            for (int j = 0; j < N; j++) {
                y0 += A.get(j + (i * N)) * X.get(j);
            }
            Y.set(i, y0);
        }
    }

    public static void sgemm(final int M, final int N, final int K, final FloatArray A, final FloatArray B, final FloatArray C) {
        for (@Parallel int i = 0; i < N; i++) {
            for (@Parallel int j = 0; j < N; j++) {
                float sum = 0.0f;
                for (int k = 0; k < K; k++) {
                    sum += A.get((i * N) + k) * B.get((k * N) + j);
                }
                C.set((i * N) + j, sum);
            }
        }

    }

    public static void dgemm(final int M, final int N, final int K, final DoubleArray A, final DoubleArray B, final DoubleArray C) {
        for (@Parallel int i = 0; i < N; i++) {
            for (@Parallel int j = 0; j < N; j++) {
                double sum = 0.0;
                for (int k = 0; k < K; k++) {
                    sum += A.get((i * N) + k) * B.get((k * N) + j);
                }
                C.set((i * N) + j, sum);
            }
        }

    }

    public static void matrixVectorMultiplication(final FloatArray A, final FloatArray B, final FloatArray C, final int size) {
        for (@Parallel int i = 0; i < size; i++) {
            float sum = 0.0f;
            for (int j = 0; j < size; j++) {
                sum += A.get((i * size) + j) * B.get(j);
            }
            C.set(i, sum);
        }
    }

    public static void matrixTranspose(final FloatArray A, FloatArray B, final int size) {
        for (@Parallel int i = 0; i < size; i++) {
            for (@Parallel int j = 0; j < size; j++) {
                B.set((i * size) + j, A.get((j * size) + i));
            }
        }
    }

    public static void matrixAddition(Matrix2DFloat4 A, Matrix2DFloat4 B, Matrix2DFloat4 C, final int size) {
        for (@Parallel int i = 0; i < size; i++) {
            for (@Parallel int j = 0; j < size; j++) {
                C.set(i, j, Float4.add(A.get(i, j), B.get(j, j)));
            }
        }
    }

    public static void matrixMultiplication(final FloatArray A, final FloatArray B, final FloatArray C, final int size) {
        for (@Parallel int i = 0; i < size; i++) {
            for (@Parallel int j = 0; j < size; j++) {
                float sum = 0.0f;
                for (int k = 0; k < size; k++) {
                    sum += A.get((i * size) + k) * B.get((k * size) + j);
                }
                C.set((i * size) + j, sum);
            }
        }
    }

    public static void matrixMultiplication(Matrix2DFloat A, Matrix2DFloat B, Matrix2DFloat C, final int size) {
        for (@Parallel int i = 0; i < size; i++) {
            for (@Parallel int j = 0; j < size; j++) {
                float sum = 0.0f;
                for (int k = 0; k < size; k++) {
                    sum += A.get(i, k) * B.get(k, j);
                }
                C.set(i, j, sum);
            }
        }
    }

    public static void spmv(final FloatArray val, final IntArray cols, final IntArray rowDelimiters, final FloatArray vec, final int dim, final FloatArray out) {
        for (@Parallel int i = 0; i < dim; i++) {
            float t = 0.0f;
            for (int j = rowDelimiters.get(i); j < rowDelimiters.get(i + 1); j++) {
                final int col = cols.get(j);
                t += val.get(j) * vec.get(col);
            }
            out.set(i, t);
        }
    }
    // CHECKSTYLE:ON
}
