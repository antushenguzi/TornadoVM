# Intel RAPL Quick Start Guide

Quick reference for using Intel RAPL CPU power monitoring in TornadoVM.

## Prerequisites

✅ Intel CPU (Sandy Bridge or newer, ~2011+)
✅ Linux operating system
✅ One of:
  - Powercap sysfs (kernel 3.13+, preferred) - no special permissions
  - MSR module (`sudo modprobe msr`) - requires root/capabilities

## Quick Check: Is RAPL Available?

```bash
# Check if powercap exists (best option)
ls /sys/class/powercap/intel-rapl/

# Alternative: Check MSR module
lsmod | grep msr
# If not loaded: sudo modprobe msr
```

## Build TornadoVM with RAPL Support

```bash
# RAPL is automatically enabled on Linux during build
make clean
make build

# Verify RAPL was compiled
grep -r "RAPL" tornado-drivers/opencl-jni/build/ 2>/dev/null || echo "Build directory not found, but RAPL should be enabled"
```

## Usage Examples

### 1. Run with Profiler Console Output

```bash
tornado --enableProfiler console \
  -m tornado.examples/uk.ac.manchester.tornado.examples.compute.MatrixMultiplication2D
```

Look for `POWER_USAGE_mW` in the output.

### 2. Run Unit Tests with Power Monitoring

```bash
# RAPL-specific tests
tornado-test -V --enableProfiler console \
  uk.ac.manchester.tornado.unittests.power.TestRAPLPowerMonitoring

# Any test with power monitoring
tornado-test --enableProfiler console -V \
  uk.ac.manchester.tornado.unittests.foundation.TestFloats
```

### 3. Programmatic Access

```java
import uk.ac.manchester.tornado.api.*;
import uk.ac.manchester.tornado.api.enums.*;
import uk.ac.manchester.tornado.api.profiler.*;

// Create task graph
TaskGraph taskGraph = new TaskGraph("s0")
    .transferToDevice(DataTransferMode.FIRST_EXECUTION, input)
    .task("t0", MyClass::compute, input, output)
    .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

// Create execution plan with profiler
ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
TornadoExecutionPlan plan = new TornadoExecutionPlan(immutableTaskGraph);
plan.withProfiler(ProfilerMode.SILENT);

// Execute and get power metrics
TornadoExecutionResult result = plan.execute();
String power = result.getProfiler().getTaskPowerUsage("s0.t0");
System.out.println("CPU Power: " + power + " mW");
```

## Expected Output

### Success (RAPL Working)

```json
{
  "s0.t0": {
    "BACKEND": "OpenCL",
    "DEVICE": "Intel(R) Core(TM) i9-9900K CPU",
    "TOTAL_KERNEL_TIME": "3500000 ns",
    "POWER_USAGE_mW": "28450"
  }
}
```

### Not Available

```json
{
  "s0.t0": {
    "POWER_USAGE_mW": "n/a"
  }
}
```

## Troubleshooting

| Problem | Solution |
|---------|----------|
| `POWER_USAGE_mW: "n/a"` | 1. Check if running on Intel CPU<br>2. Load MSR module: `sudo modprobe msr`<br>3. Or run with sudo |
| Power readings are 0 | Task duration too short, run longer tasks (>10ms) |
| Permission denied | Use powercap (no root) or grant capabilities:<br>`sudo setcap cap_sys_rawio=ep $JAVA_HOME/bin/java` |

## Quick Validation

Test RAPL directly:

```bash
# Read power over 1 second
E1=$(cat /sys/class/powercap/intel-rapl/intel-rapl:0/energy_uj)
sleep 1
E2=$(cat /sys/class/powercap/intel-rapl/intel-rapl:0/energy_uj)
echo "CPU Power: $(( ($E2 - $E1) / 1000 )) mW"
```

Expected output: `CPU Power: 15000 mW` (varies by CPU and load)

## Device Selection

RAPL is automatically enabled for:
- Platform: Intel OpenCL
- Device Type: CPU

To check your devices:
```bash
tornado --devices
```

## Benchmark Example

```bash
# Run benchmark with power profiling
tornado --enableProfiler console --printKernel \
  -m tornado.benchmarks/uk.ac.manchester.tornado.benchmarks.DFTBenchmark \
  --params="1024"
```

## Documentation

For detailed information, see:
- `docs/RAPL_POWER_MONITORING.md` - Complete user guide
- `RAPL_IMPLEMENTATION_SUMMARY.md` - Technical implementation details

## Support

If RAPL doesn't work:
1. Verify Intel CPU: `lscpu | grep Intel`
2. Check kernel version: `uname -r` (need 3.13+ for powercap)
3. Try MSR: `sudo modprobe msr && sudo tornado ...`
4. Check logs: `tornado --debug ...`

## Platform Support

| Platform | Status | Notes |
|----------|--------|-------|
| Linux + Intel CPU | ✅ Supported | Preferred platform |
| Linux + AMD CPU | ❌ Not yet | Different RAPL implementation |
| Windows | ❌ Not yet | Could use Performance Counters |
| macOS | ❌ Not yet | No direct RAPL access |

## Performance Impact

RAPL adds minimal overhead:
- ~1-10 microseconds per power reading
- Negligible for typical tasks (>1ms)
- May affect micro-benchmarks (<100μs)

To disable power monitoring, simply don't enable the profiler.

## Further Reading

- Intel RAPL specification in [Intel SDM Vol 3B, Section 14.9](https://www.intel.com/content/www/us/en/developer/articles/technical/intel-sdm.html)
- Linux [Powercap documentation](https://www.kernel.org/doc/html/latest/power/powercap/powercap.html)
- Original NVIDIA power monitoring: [PR #377](https://github.com/beehive-lab/TornadoVM/pull/377)
