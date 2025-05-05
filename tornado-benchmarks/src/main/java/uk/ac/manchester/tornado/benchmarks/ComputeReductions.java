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
package uk.ac.manchester.tornado.benchmarks;

import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.annotations.Reduce;
import uk.ac.manchester.tornado.api.math.TornadoMath;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;

public class ComputeReductions {
    // CHECKSTYLE:OFF

    public static int computeMaxSequential(IntArray input) {
        int acc = 0;
        for (int i = 0; i < input.getSize(); i++) {
            acc = TornadoMath.max(acc, input.get(i));
        }
        return acc;
    }

    public static void intReductionMaxGlobalMemory(KernelContext context, IntArray a, IntArray b) {
        int localIdx = context.localIdx;
        int localGroupSize = context.localGroupSizeX;
        int groupID = context.groupIdx; // Expose Group ID
        int id = localGroupSize * groupID + localIdx;

        for (int stride = (localGroupSize / 2); stride > 0; stride /= 2) {
            context.localBarrier();
            if (localIdx < stride) {
                a.set(id, TornadoMath.max(a.get(id), a.get(id + stride)));
            }
        }
        if (localIdx == 0) {
            b.set(groupID, a.get(id));
        }
    }

    public static void intReductionMaxLocalMemory(KernelContext context, IntArray a, IntArray b) {
        int globalIdx = context.globalIdx;
        int localIdx = context.localIdx;
        int localGroupSize = context.localGroupSizeX;
        int groupID = context.groupIdx; // Expose Group ID

        int[] localA = context.allocateIntLocalArray(256);
        localA[localIdx] = a.get(globalIdx);
        for (int stride = (localGroupSize / 2); stride > 0; stride /= 2) {
            context.localBarrier();
            if (localIdx < stride) {
                localA[localIdx] = TornadoMath.max(localA[localIdx], localA[localIdx + stride]);
            }
        }
        if (localIdx == 0) {
            b.set(groupID, localA[0]);
        }
    }

    public static float f(float x) {
        return (1 / ((x + 1) * TornadoMath.sqrt(x * TornadoMath.exp(x))));
    }

    public static void integration(FloatArray input, @Reduce FloatArray sum, final float a, final float b) {
        final int size = input.getSize();
        for (@Parallel int i = 0; i < input.getSize(); i++) {
            float value = f(a + (((i + 1) - (1 / 2)) * ((b - a) / size)));
            sum.set(0, sum.get(0) + input.get(i) + value);
        }
    }
    // CHECKSTYLE:ON
}
