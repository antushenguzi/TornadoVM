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
package uk.ac.manchester.tornado.benchmarks;

import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.math.TornadoMath;
import uk.ac.manchester.tornado.api.types.arrays.DoubleArray;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.api.types.arrays.LongArray;
import uk.ac.manchester.tornado.api.types.arrays.ShortArray;
import uk.ac.manchester.tornado.api.types.collections.VectorFloat4;
import uk.ac.manchester.tornado.api.types.images.ImageByte3;
import uk.ac.manchester.tornado.api.types.images.ImageFloat3;
import uk.ac.manchester.tornado.api.types.vectors.Byte3;
import uk.ac.manchester.tornado.api.types.vectors.Float4;

import java.util.Random;
import java.util.stream.IntStream;

public class ComputeKernels {
    // CHECKSTYLE:OFF

    public static final float S_LOWER_LIMIT = 10.0f;

    public static final float S_UPPER_LIMIT = 100.0f;

    public static final float K_LOWER_LIMIT = 10.0f;

    public static final float K_UPPER_LIMIT = 100.0f;

    public static final float T_LOWER_LIMIT = 1.0f;

    public static final float T_UPPER_LIMIT = 10.0f;

    public static final float R_LOWER_LIMIT = 0.01f;

    public static final float R_UPPER_LIMIT = 0.05f;

    public static final float SIGMA_LOWER_LIMIT = 0.01f;

    public static final float SIGMA_UPPER_LIMIT = 0.10f;

    /**
     * Parallel Implementation of the MonteCarlo computation: this is based on the
     * Marawacc compiler framework.
     *
     * @author Juan Fumero
     */
    public static void monteCarlo(FloatArray result, int size) {
        final int total = size;
        final int iter = 25000;
        for (@Parallel int idx = 0; idx < total; idx++) {
            long seed = idx;
            float sum = 0.0f;
            for (int j = 0; j < iter; ++j) {
                // generate a pseudo random number (you do need it twice)
                seed = (seed * 0x5DEECE66DL + 0xBL) & ((1L << 48) - 1);
                seed = (seed * 0x5DEECE66DL + 0xBL) & ((1L << 48) - 1);
                // this generates a number between 0 and 1 (with an awful
                // entropy)
                float x = (seed & 0x0FFFFFFF) / 268435455f;
                // repeat for y
                seed = (seed * 0x5DEECE66DL + 0xBL) & ((1L << 48) - 1);
                seed = (seed * 0x5DEECE66DL + 0xBL) & ((1L << 48) - 1);
                float y = (seed & 0x0FFFFFFF) / 268435455f;
                float dist = TornadoMath.sqrt(x * x + y * y);
                if (dist <= 1.0f) {
                    sum += 1.0f;
                }
            }
            sum = sum * 4;
            result.set(idx, sum / iter);
        }
    }

    public static void nBody(int numBodies, FloatArray refPos, FloatArray refVel, float delT, float espSqr) {
        for (@Parallel int i = 0; i < numBodies; i++) {
            int body = 4 * i;
            float[] acc = new float[] { 0.0f, 0.0f, 0.0f };
            for (int j = 0; j < numBodies; j++) {
                float[] r = new float[3];
                int index = 4 * j;

                float distSqr = 0.0f;
                for (int k = 0; k < 3; k++) {
                    r[k] = refPos.get(index + k) - refPos.get(body + k);
                    distSqr += r[k] * r[k];
                }

                float invDist = 1.0f / TornadoMath.sqrt(distSqr + espSqr);

                float invDistCube = invDist * invDist * invDist;
                float s = refPos.get(index + 3) * invDistCube;

                for (int k = 0; k < 3; k++) {
                    acc[k] += s * r[k];
                }
            }
            for (int k = 0; k < 3; k++) {
                refPos.set(body + k, refPos.get(body + k) + refPos.get(body + k) * delT + 0.5f * acc[k] * delT * delT);
                refVel.set(body + k, refPos.get(body + k) + acc[k] * delT);
            }
        }
    }

    /**
     * @param X
     *     input value
     * @brief Abromowitz Stegun approxmimation for PHI (Cumulative Normal
     *     Distribution Function)
     */
    static float phi(final float X) {
        final float c1 = 0.319381530f;
        final float c2 = -0.356563782f;
        final float c3 = 1.781477937f;
        final float c4 = -1.821255978f;
        final float c5 = 1.330274429f;

        final float zero = 0.0f;
        final float one = 1.0f;
        final float two = 2.0f;
        final float temp4 = 0.2316419f;

        final float oneBySqrt2pi = 0.398942280f;

        final float absX = Math.abs(X);
        final float t = one / (one + (temp4 * absX));

        final float y = (one - (oneBySqrt2pi * TornadoMath.exp((-X * X) / two) * t * (c1 + (t * (c2 + (t * (c3 + (t * (c4 + (t * c5))))))))));

        return ((X < zero) ? (one - y) : y);
    }

    /*
     * @brief Computes the call and put prices by using Black Scholes model
     *
     * @param randArray input array of random values of current option price
     *
     * @param out output array of calculated put price values
     *
     * @param call output array of calculated call price values
     */
    public static void blackscholes(final FloatArray randArray, final FloatArray put, final FloatArray call) {
        for (@Parallel int gid = 0; gid < call.getSize(); gid++) {
            final float two = 2.0f;
            final float inRand = randArray.get(gid);
            final float S = (S_LOWER_LIMIT * inRand) + (S_UPPER_LIMIT * (1.0f - inRand));
            final float K = (K_LOWER_LIMIT * inRand) + (K_UPPER_LIMIT * (1.0f - inRand));
            final float T = (T_LOWER_LIMIT * inRand) + (T_UPPER_LIMIT * (1.0f - inRand));
            final float R = (R_LOWER_LIMIT * inRand) + (R_UPPER_LIMIT * (1.0f - inRand));
            final float sigmaVal = (SIGMA_LOWER_LIMIT * inRand) + (SIGMA_UPPER_LIMIT * (1.0f - inRand));

            final float sigmaSqrtT = sigmaVal * TornadoMath.sqrt(T);

            final float d1 = (TornadoMath.log(S / K) + ((R + ((sigmaVal * sigmaVal) / two)) * T)) / sigmaSqrtT;
            final float d2 = d1 - sigmaSqrtT;

            final float KexpMinusRT = K * TornadoMath.exp(-R * T);

            float phiD1 = phi(d1);
            float phiD2 = phi(d2);

            call.set(gid, (S * phiD1) - (KexpMinusRT * phiD2));
            phiD1 = phi(-d1);
            phiD2 = phi(-d2);

            put.set(gid, (KexpMinusRT * phiD2) - (S * phiD1));
        }
    }

    public static void computeDFT(DoubleArray inreal, DoubleArray inimag, DoubleArray outreal, DoubleArray outimag) {
        int n = inreal.getSize();
        for (@Parallel int k = 0; k < n; k++) { // For each output element
            float sumReal = 0;
            float simImag = 0;
            for (int t = 0; t < n; t++) { // For each input element
                double angle = (2 * Math.PI * t * k) / n;
                sumReal += inreal.get(t) * Math.cos(angle) + inimag.get(t) * Math.sin(angle);
                simImag += -inreal.get(t) * Math.sin(angle) + inimag.get(t) * Math.cos(angle);
            }
            outreal.set(k, sumReal);
            outimag.set(k, simImag);
        }
    }

    public static void computeDFT(FloatArray inreal, FloatArray inimag, FloatArray outreal, FloatArray outimag) {
        int n = inreal.getSize();
        for (@Parallel int k = 0; k < n; k++) { // For each output element
            float sumReal = 0;
            float simImag = 0;
            for (int t = 0; t < n; t++) { // For each input element
                float angle = (2 * TornadoMath.floatPI() * t * k) / n;
                sumReal += inreal.get(t) * TornadoMath.cos(angle) + inimag.get(t) * TornadoMath.sin(angle);
                simImag += -inreal.get(t) * TornadoMath.sin(angle) + inimag.get(t) * TornadoMath.cos(angle);
            }
            outreal.set(k, sumReal);
            outimag.set(k, simImag);
        }
    }

    /**
     * Parallel Implementation of the Mandelbrot: this is based on the Marawacc
     * compiler framework.
     *
     * @author Juan Fumero
     */
    public static void mandelbrot(int size, ShortArray output) {
        final int iterations = 10000;
        float space = 2.0f / size;
        for (@Parallel int i = 0; i < size; i++) {
            for (@Parallel int j = 0; j < size; j++) {
                float Zr = 0.0f;
                float Zi = 0.0f;
                float Cr = (1 * j * space - 1.5f);
                float Ci = (1 * i * space - 1.0f);
                float ZrN = 0;
                float ZiN = 0;
                int y = 0;
                for (int ii = 0; ii < iterations; ii++) {
                    if (ZiN + ZrN <= 4.0f) {
                        Zi = 2.0f * Zr * Zi + Ci;
                        Zr = 1 * ZrN - ZiN + Cr;
                        ZiN = Zi * Zi;
                        ZrN = Zr * Zr;
                        y++;
                    } else {
                        ii = iterations;
                    }
                }
                short r = (short) ((y * 255) / iterations);
                output.set(i * size + j, r);
            }
        }
    }

    public static void hilbertComputation(FloatArray output, int rows, int cols) {
        for (@Parallel int i = 0; i < rows; i++) {
            for (@Parallel int j = 0; j < cols; j++) {
                output.set(i * rows + j, ((float) 1 / (float) ((i + 1) + (j + 1) - 1)));
            }
        }
    }

    public static void channelConvolution(IntArray rgbChannel, IntArray channelBlurred, final int numRows, final int numCols, FloatArray filter, final int filterWidth) {
        // Dealing with an even width filter is trickier
        assert (filterWidth % 2 == 1);
        // For every pixel in the image
        for (@Parallel int r = 0; r < numRows; ++r) {
            for (@Parallel int c = 0; c < numCols; ++c) {
                float result = 0.0f;
                // For every value in the filter around the pixel (c, r)
                for (int filter_r = -filterWidth / 2; filter_r <= filterWidth / 2; ++filter_r) {
                    for (int filter_c = -filterWidth / 2; filter_c <= filterWidth / 2; ++filter_c) {
                        // Find the global image position for this filter
                        // position
                        // clamp to boundary of the image
                        int image_r = Math.min(Math.max(r + filter_r, 0), (numRows - 1));
                        int image_c = Math.min(Math.max(c + filter_c, 0), (numCols - 1));

                        float image_value = (rgbChannel.get(image_r * numCols + image_c));
                        float filter_value = filter.get((filter_r + filterWidth / 2) * filterWidth + filter_c + filterWidth / 2);

                        result += image_value * filter_value;
                    }
                }
                channelBlurred.set(r * numCols + c, result > 255 ? 255 : (int) result);
            }
        }
    }

    public static void renderTrack(ImageByte3 output, ImageFloat3 input) {
        for (@Parallel int y = 0; y < input.Y(); y++) {
            for (@Parallel int x = 0; x < input.X(); x++) {
                Byte3 pixel = null;
                final int result = (int) input.get(x, y).getS2();
                switch (result) {
                    case 1: // ok GREY
                        pixel = new Byte3((byte) 128, (byte) 128, (byte) 128);
                        break;
                    case -1: // no input BLACK
                        pixel = new Byte3((byte) 0, (byte) 0, (byte) 0);
                        break;
                    case -2: // not in image RED
                        pixel = new Byte3((byte) 255, (byte) 0, (byte) 0);
                        break;
                    case -3: // no correspondence GREEN
                        pixel = new Byte3((byte) 0, (byte) 255, (byte) 0);
                        break;
                    case -4: // too far away BLUE
                        pixel = new Byte3((byte) 0, (byte) 0, (byte) 255);
                        break;
                    case -5: // wrong normal YELLOW
                        pixel = new Byte3((byte) 255, (byte) 255, (byte) 0);
                        break;
                    default:
                        pixel = new Byte3((byte) 255, (byte) 128, (byte) 128);
                        break;
                }
                output.set(x, y, pixel);
            }
        }
    }

    public static void blackAndWhiteCompute(IntArray image, final int w, final int s) {
        for (@Parallel int i = 0; i < w; i++) {
            for (@Parallel int j = 0; j < s; j++) {
                int rgb = image.get(i * s + j);
                int alpha = (rgb >> 24) & 0xff;
                int red = (rgb >> 16) & 0xFF;
                int green = (rgb >> 8) & 0xFF;
                int blue = (rgb & 0xFF);

                int grayLevel = (red + green + blue) / 3;
                int gray = (alpha << 24) | (grayLevel << 16) | (grayLevel << 8) | grayLevel;
                image.set(i * s + j, gray);
            }
        }
    }

    public static void euler(int size, LongArray five, LongArray outputA, LongArray outputB, LongArray outputC, LongArray outputD, LongArray outputE) {
        for (@Parallel int e = 1; e < five.getSize(); e++) {
            long e5 = five.get(e);
            for (@Parallel int a = 1; a < five.getSize(); a++) {
                long a5 = five.get(a);
                for (int b = a; b < size; b++) {
                    long b5 = five.get(b);
                    for (int c = b; c < size; c++) {
                        long c5 = five.get(c);
                        for (int d = c; d < size; d++) {
                            long d5 = five.get(d);
                            if (a5 + b5 + c5 + d5 == e5) {
                                outputA.set(e, a);
                                outputB.set(e, b);
                                outputC.set(e, c);
                                outputD.set(e, d);
                                outputE.set(e, e);
                            }
                        }
                    }
                }
            }
        }
    }

    public static void vectorAdd(FloatArray a, FloatArray b, FloatArray c) {
        for (@Parallel int i = 0; i < c.getSize(); i++) {
            c.set(i, a.get(i) + b.get(i));
        }
    }

    public static void vectorAdd(VectorFloat4 a, VectorFloat4 b, VectorFloat4 results) {
        for (@Parallel int i = 0; i < a.getLength(); i++) {
            results.set(i, Float4.add(a.get(i), b.get(i)));
        }
    }

    public static void computeFFT(IntArray output, final IntArray factors, IntArray dimArr) {
        for (@Parallel int i = 0; i < dimArr.get(0); i++) {
            for (@Parallel int j = 0; j < dimArr.get(1); j++) {
                int product = 1;
                int state = 0;

                for (int z = 0; z < factors.getSize(); z++) {
                    product *= output.get(z);

                    if (state == 0) {
                        state = 1;
                        if (factors.get(z) == 2) { // factors[z]
                            int factor = 2;
                            int q = factors.get(z) / product;
                            int p_1 = product / factor;
                            for (int k = 0; k < q; k++) {
                                for (int k1 = 0; k1 < p_1; k1++) {
                                    output.set(k1, i + j + z + k);
                                }
                            }
                        }
                    } else {
                        state = 0;
                    }
                }
            }
        }
    }

    public static void bfs(IntArray vertices, IntArray adjacencyMatrix, int numNodes, IntArray h_true, IntArray currentDepth) {
        for (@Parallel int from = 0; from < numNodes; from++) {
            for (@Parallel int to = 0; to < numNodes; to++) {
                int elementAccess = from * numNodes + to;

                if (adjacencyMatrix.get(elementAccess) == 1) {
                    int dfirst = vertices.get(from);
                    int dsecond = vertices.get(to);
                    if ((currentDepth.get(0) == dfirst) && (dsecond == -1)) {
                        vertices.set(to, dfirst + 1);
                        h_true.set(0, 0);
                    }
                }
            }
        }
    }

    /**
     * This method implements the following CUDA kernel with the TornadoVM Kernel API.
     *
     * __global__ void histogramKernel(int *data, int *hist, int dataSize) {
     * int tid = threadIdx.x + blockIdx.x * blockDim.x;
     *
     * if (tid < dataSize) {
     * atomicAdd(&hist[data[tid]], 1);
     * }
     * }
     *
     * @param context
     * @param input
     * @param output
     */
    public static void histogramKernel(KernelContext context, IntArray input, IntArray output) {
        int tid = context.globalIdx;

        if (tid < input.getSize()) {
            int index = input.get(tid);
            context.atomicAdd(output, index, 1);
        }
    }

    public static void histogram(KernelContext context, IntArray input, IntArray output) {
        for (int tid = 0; tid < input.getSize(); tid++) {
            int index = input.get(tid);
            context.atomicAdd(output, index, 1);
            output.set(index, output.get(index));
        }
    }

    private static int[] generateIntRandomArray(int numNodes) {
        Random r = new Random();
        int bound = r.nextInt(numNodes);
        IntStream streamArray = r.ints(bound, 0, numNodes);
        int[] array = streamArray.toArray();
        return array;
    }

    public static void connect(int from, int to, IntArray graph, int N) {
        if (from != to && (graph.get(from * N + to) == 0)) {
            graph.set(from * N + to, 1);
        }
    }

    public static void generateRandomGraph(IntArray adjacencyMatrix, int numNodes, int root) {
        Random r = new Random();
        int bound = r.nextInt(numNodes);
        IntStream fromStream = r.ints(bound, 0, numNodes);
        int[] f = fromStream.toArray();
        for (int k = 0; k < f.length; k++) {

            int from = f[k];
            if (k == 0) {
                from = root;
            }

            int[] toArray = generateIntRandomArray(numNodes);

            for (int i = 0; i < toArray.length; i++) {
                connect(from, toArray[i], adjacencyMatrix, numNodes);
            }
        }
    }
    // CHECKSTYLE:ON
}
