# Intel RAPL CPU Power Monitoring in TornadoVM

## Overview

TornadoVM now supports CPU energy monitoring through Intel RAPL (Running Average Power Limit) interface. This feature enables accurate measurement of CPU energy consumption during task execution, similar to the existing NVIDIA GPU power monitoring via NVML.

## What is Intel RAPL?

Intel RAPL is a hardware interface available on Intel processors (Sandy Bridge and newer) that provides energy consumption measurements for:

- **Package (PKG)**: Total CPU package energy including cores and integrated GPU
- **Power Plane 0 (PP0)**: CPU cores energy
- **Power Plane 1 (PP1)**: Integrated GPU energy (if available)
- **DRAM**: Memory energy (on server CPUs)

RAPL provides energy counters (in microjoules) that accumulate over time. Power consumption is calculated by measuring energy differences over time intervals.

## Architecture

### Components

1. **RAPLPowerMetric** (interface in `drivers-common`)
   - Defines RAPL power metric contract
   - Methods: `initializeRAPL()`, `getEnergyUsage()`, `closeRAPL()`

2. **OCLIntelRAPLPowerMetricHandler** (Java JNI wrapper)
   - Implements `PowerMetric` interface
   - Manages RAPL initialization and energy readings
   - Calculates instantaneous power from energy deltas
   - Automatically selected for Intel CPU OpenCL devices

3. **OCLIntelRAPLPowerMetricHandler.cpp** (Native implementation)
   - JNI C++ implementation for RAPL access
   - Supports two access methods:
     - **Linux powercap sysfs** (preferred, no root needed)
     - **MSR (Model Specific Registers)** (fallback, requires root/msr module)

### Integration

RAPL power monitoring is automatically enabled when:
- Running on Linux (not macOS or Windows)
- Using Intel OpenCL platform
- Device type is CPU

The power handler is selected in `OCLDeviceContext` constructor:

```java
if (isDeviceContextOfNvidia()) {
    this.powerMetricHandler = new OCLNvidiaPowerMetricHandler(this);
} else if (isDeviceContextOfIntelCPU()) {
    this.powerMetricHandler = new OCLIntelRAPLPowerMetricHandler(this);
} else {
    this.powerMetricHandler = new OCLEmptyPowerMetricHandler();
}
```

## System Requirements

### Hardware
- Intel CPU with RAPL support (Sandy Bridge or newer, ~2011+)
- Architectures: Haswell, Broadwell, Skylake, Cascade Lake, Ice Lake, Tiger Lake, Alder Lake, Raptor Lake, etc.

### Software
- **Linux kernel 3.13+** (for powercap sysfs interface)
- **OR MSR kernel module** (older fallback method)

### Permissions

#### Powercap Sysfs (Recommended, No Root Required)
The powercap interface at `/sys/class/powercap/intel-rapl/` is readable by default on most distributions:

```bash
# Check if powercap is available
ls /sys/class/powercap/intel-rapl/

# Expected output:
# intel-rapl:0/  intel-rapl:1/  ...
```

#### MSR Interface (Fallback, Requires Root/Capabilities)
If powercap is unavailable, RAPL falls back to reading MSRs:

```bash
# Load MSR kernel module
sudo modprobe msr

# Verify MSR devices exist
ls /dev/cpu/*/msr

# Option 1: Run TornadoVM with sudo
sudo tornado --enableProfiler console ...

# Option 2: Grant capabilities (preferred for production)
sudo setcap cap_sys_rawio=ep $JAVA_HOME/bin/java

# Option 3: Make MSR readable (less secure)
sudo chmod +r /dev/cpu/*/msr
```

## Building with RAPL Support

RAPL support is automatically enabled on Linux during build:

```bash
# Standard build (RAPL enabled automatically on Linux)
make build

# Rebuild with CMake verbose to see RAPL detection
cd tornado-drivers/opencl-jni
mkdir build && cd build
cmake .. -DCMAKE_VERBOSE_MAKEFILE=ON
make

# Expected CMake output:
# -- Intel RAPL support enabled for Linux
```

## Usage

### Enable Profiler to See Power Metrics

```bash
# Run with profiler enabled
tornado --enableProfiler console --printKernel \
  -m tornado.examples/uk.ac.manchester.tornado.examples.compute.MatrixMultiplication2D

# Run tests with profiler
tornado-test --enableProfiler console -V uk.ac.manchester.tornado.unittests.compute.ComputeTests
```

### JSON Profiler Output

When RAPL is active, the profiler includes `POWER_USAGE_mW` in task metrics:

```json
{
  "s0.t0.task0": {
    "BACKEND": "OpenCL",
    "DEVICE": "Intel(R) Core(TM) i9-9900K CPU",
    "TOTAL_TASK_SCHEDULE_TIME": "1250000 ns",
    "TOTAL_KERNEL_TIME": "3500000 ns",
    "POWER_USAGE_mW": "28450",
    "COPY_IN_SIZE_BYTES": "4096",
    "COPY_OUT_SIZE_BYTES": "4096"
  }
}
```

### Programmatic Access via TornadoProfiler

```java
import uk.ac.manchester.tornado.api.*;
import uk.ac.manchester.tornado.api.profiler.*;

// Create and execute task graph with profiler
TaskGraph taskGraph = new TaskGraph("s0")
    .transferToDevice(DataTransferMode.FIRST_EXECUTION, input)
    .task("t0", MyClass::computeTask, input, output)
    .transferToHost(DataTransferMode.EVERY_EXECUTION, output);

ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);

// Enable profiler
executionPlan.withProfiler(ProfilerMode.CONSOLE);

// Execute
TornadoExecutionResult result = executionPlan.execute();

// Access profiler data
TornadoProfiler profiler = result.getProfiler();
long powerUsage = profiler.getTaskPowerUsage("s0.t0");
System.out.println("CPU Power: " + powerUsage + " mW");
```

## Technical Details

### Energy to Power Conversion

RAPL provides cumulative energy counters in microjoules (μJ). Power is derived as:

```
Power (mW) = ΔEnergy (μJ) × 1,000,000 / Δtime (ns)
```

The handler maintains previous energy and timestamp values to calculate instantaneous power between measurements.

### Counter Overflow Handling

RAPL energy counters are 32-bit and overflow approximately every:
- Desktop: ~60 seconds at 100W
- Laptop: ~200 seconds at 30W

The implementation handles overflows in the Java layer by tracking deltas.

### Supported RAPL Domains

| Domain | MSR Address | Description | Availability |
|--------|-------------|-------------|--------------|
| PKG    | 0x611       | Package (CPU + iGPU) | All Intel CPUs |
| PP0    | 0x639       | CPU Cores | All Intel CPUs |
| PP1    | 0x641       | Integrated GPU | Client CPUs only |
| DRAM   | 0x619       | Memory | Server CPUs (Xeon) |

Currently, TornadoVM reports **Package (PKG)** power as the primary metric.

### Powercap Sysfs Structure

```
/sys/class/powercap/intel-rapl/
├── intel-rapl:0/                    # Package 0
│   ├── energy_uj                    # Current energy (μJ)
│   ├── max_energy_range_uj          # Maximum before overflow
│   ├── name                         # "package-0"
│   ├── intel-rapl:0:0/              # PP0 (cores)
│   ├── intel-rapl:0:1/              # PP1 (iGPU)
│   └── intel-rapl:0:2/              # DRAM
└── intel-rapl:1/                    # Package 1 (multi-socket)
```

## Troubleshooting

### RAPL Initialization Fails

**Symptom**: Profiler shows `"POWER_USAGE_mW": "n/a"`

**Diagnosis**:
```bash
# Check if powercap exists
ls /sys/class/powercap/intel-rapl/

# Check if MSR module is loaded
lsmod | grep msr

# Check Java process capabilities
getcap $JAVA_HOME/bin/java
```

**Solutions**:
1. Ensure running on Intel CPU: `lscpu | grep "Model name"`
2. Load MSR module: `sudo modprobe msr`
3. Run with sudo or grant capabilities (see Permissions section)

### Inaccurate Power Readings

**Issue**: Power values seem incorrect or fluctuate wildly

**Causes**:
- Measurement interval too short (< 10ms)
- CPU frequency scaling (turbo boost, power states)
- Background processes consuming CPU

**Recommendations**:
- Run longer-duration tasks (>100ms) for stable readings
- Disable CPU frequency scaling for benchmarks:
  ```bash
  sudo cpupower frequency-set --governor performance
  ```
- Minimize background processes

### Platform-Specific Issues

#### Ubuntu/Debian
```bash
# Install MSR tools
sudo apt-get install msr-tools

# Load MSR module persistently
echo "msr" | sudo tee /etc/modules-load.d/msr.conf
```

#### RHEL/Fedora/CentOS
```bash
# Install MSR tools
sudo dnf install msr-tools

# Load MSR module
sudo modprobe msr
```

#### Secure Boot
MSR module may be blocked with Secure Boot enabled. Either:
- Disable Secure Boot in BIOS
- Sign the MSR module with your MOK key
- Use powercap sysfs instead (recommended)

## Validation

### Verify RAPL Works Independently

Test RAPL directly using `turbostat` (requires root):

```bash
sudo turbostat --interval 1
# Observe "PkgWatt" column for package power
```

Or read powercap directly:

```bash
# Read package energy over 1 second
E1=$(cat /sys/class/powercap/intel-rapl/intel-rapl:0/energy_uj)
sleep 1
E2=$(cat /sys/class/powercap/intel-rapl/intel-rapl:0/energy_uj)
echo "Power: $(( ($E2 - $E1) / 1000 )) mW"
```

### Compare with Hardware Monitoring

Cross-validate RAPL readings with:
- `lm-sensors` (motherboard sensors)
- `intel_pstate` driver statistics
- External power meter (wall socket measurement)

Note: RAPL measures CPU package only, not total system power.

## Limitations

1. **Linux Only**: RAPL support is Linux-specific. Not available on macOS or Windows in current implementation.

2. **Intel CPUs Only**: AMD CPUs use different energy monitoring (RAPL exists but with different MSRs). Future work could add AMD support.

3. **Package-Level Granularity**: Reports total package power. Per-core power available via PP0 domain but not currently exposed.

4. **Sampling Overhead**: Each RAPL read takes ~1-10μs. Negligible for typical task durations but may affect micro-benchmarks.

5. **No Historical Data**: Only instantaneous power between consecutive measurements. Energy-over-time requires external integration.

## Future Enhancements

- [ ] Expose PP0, PP1, DRAM power separately
- [ ] Add AMD RAPL support
- [ ] Windows support via WMI/Performance Counters
- [ ] Multi-socket CPU support (aggregate power)
- [ ] Integration with TornadoVM energy-aware scheduling
- [ ] Cumulative energy reporting per execution plan

## References

- [Intel® 64 and IA-32 Architectures Software Developer's Manual, Volume 3B](https://www.intel.com/content/www/us/en/developer/articles/technical/intel-sdm.html) (Section 14.9: RAPL)
- [Linux Kernel Powercap Documentation](https://www.kernel.org/doc/html/latest/power/powercap/powercap.html)
- [RAPL in Action: Experiences in Using RAPL for Power Measurements](https://dl.acm.org/doi/10.1145/3177754)

## Citation

If you use TornadoVM's RAPL power monitoring in your research, please cite:

```bibtex
@inproceedings{fumero2017tornadovm,
  title={{TornadoVM: A practical and efficient heterogeneous programming framework for managed languages}},
  author={Fumero, Juan and Papadimitriou, Michel and Zakkak, Foivos S and Xekalaki, Maria and Clarkson, James and Kotselidis, Christos},
  booktitle={Proceedings of the 14th International Conference on Managed Languages and Runtimes},
  pages={1--13},
  year={2017}
}
```

## License

RAPL power monitoring implementation is released under the same licenses as TornadoVM:
- **tornado-drivers** (including RAPL handlers): GPL v2 with Classpath Exception
- **Native JNI code** (C++ implementation): MIT License
