# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

TornadoVM is a plug-in to OpenJDK and GraalVM that enables automatic execution of Java programs on heterogeneous hardware (GPUs, FPGAs, multi-core CPUs). It provides three backends: OpenCL, PTX (NVIDIA CUDA), and SPIR-V (Intel Level Zero).

**Key Concepts:**
- **Task Graph**: The core abstraction for defining data-parallel computations that can be offloaded to accelerators
- **Execution Plan**: Immutable representation of a task graph ready for execution
- **Backend**: Hardware abstraction layer (OpenCL, PTX, or SPIRV) that generates device-specific code
- **Dynamic Reconfiguration**: Runtime capability to migrate tasks between devices for optimal performance

## Build System

TornadoVM uses Maven for dependency management and a combination of Makefile + Python scripts for building.

### Building the Project

```bash
# Build with OpenCL backend (default)
make build

# Build with specific backend(s)
make BACKEND=opencl
make BACKEND=ptx,opencl
make BACKEND=spirv,ptx,opencl

# Build with specific JDK
make jdk21
make graal-jdk-21

# Build with Polyglot support (GraalVM languages)
make polyglot

# Rebuild dependencies
make rebuild-deps-jdk21

# Clean build
make clean
```

The main build script is `bin/compile` (Python). Build configuration uses Maven profiles for platform detection (Windows/Linux, x86/AMD64).

### Testing

```bash
# Run all tests (comprehensive suite)
make tests

# Run fast tests (quick validation)
make fast-tests

# Run specific test class with verbose output
tornado-test -V uk.ac.manchester.tornado.unittests.foundation.TestFloats

# Run specific test method
tornado-test -V uk.ac.manchester.tornado.unittests.compute.ComputeTests#testNBody

# Run with debug options
tornado-test -V --printKernel --debug <testClass>

# Run tests for specific backend (SPIRV with Level Zero)
make tests-spirv-levelzero

# Run tests for specific backend (SPIRV with OpenCL)
make tests-spirv-opencl

# Maven-based testing (from tornado-unittests module)
mvn exec:exec -Dtest.class=uk.ac.manchester.tornado.unittests.foundation.TestFloats
```

**Test Options:**
- `-V` or `--verbose`: Detailed output
- `--ea`: Enable assertions
- `--printKernel`: Display generated kernel code
- `--debug`: Enable debug mode
- `--device`: Specify device (e.g., `s0.t0.device=0:1`)
- `--jvm`: Pass JVM flags (e.g., `-J"-Ds0.t0.device=0:1"`)
- `--enableProfiler`: Enable profiler (`silent` or `console`)

### Running Examples

```bash
# Run example with kernel printing
tornado --printKernel --debug -m tornado.examples/uk.ac.manchester.tornado.examples.VectorAddInt --params="8192"
```

### Code Formatting

TornadoVM uses auto-formatters. Before submitting PRs, format code:

```bash
# For Eclipse
python3 scripts/eclipseSetup.py
```

For IntelliJ, import `scripts/templates/eclipse-settings/Tornado.xml` and configure:
- Use single class import
- Enable optimize imports on save
- Enable reformat file on save

Check code style:
```bash
make checkstyle
```

### Documentation

```bash
# Build documentation
make docs
```

## Architecture

### Module Structure

The codebase is organized into Maven modules:

1. **tornado-api** (Apache 2.0 License)
   - Public API for users: `TaskGraph`, `ImmutableTaskGraph`, `TornadoExecutionPlan`
   - Annotations: `@Parallel`, `@Reduce`
   - Grid abstractions: `WorkerGrid`, `KernelContext`, `GridScheduler`
   - Core interfaces: `TornadoDevice`, `TornadoBackend`, `TornadoRuntime`

2. **tornado-runtime** (GPL v2 with Classpath Exception)
   - Task execution engine: `TornadoTaskGraph`, `CompilableTask`, `PrebuiltTask`
   - Data management: `DataObjectState`, `LocalObjectState`, `XPUDeviceBufferState`
   - Graal integration: Compiler tiers (Sketch, High, Mid, Low), custom IR nodes
   - Code analysis: `CodeAnalysis`, `ReduceCodeAnalysis`, `MetaReduceTasks`
   - Backend infrastructure: `TornadoAcceleratorBackend`, `XPUBackend`

3. **tornado-drivers** (GPL v2 with Classpath Exception)
   - **drivers-common**: Shared driver utilities
   - **opencl**: OpenCL backend implementation
   - **opencl-jni**: JNI bindings for OpenCL
   - **ptx**: PTX (NVIDIA CUDA) backend implementation
   - **ptx-jni**: JNI bindings for CUDA
   - **spirv**: SPIR-V backend implementation

   Each backend has:
   - `graal/`: Backend-specific Graal compiler nodes and code generators
   - `mm/`: Memory management
   - `builtins/`: Built-in function implementations
   - `*BackendImpl.java`: Main backend entry point

4. **tornado-annotation** (Apache 2.0)
   - ASM-based annotation processing for `@Parallel` and `@Reduce`

5. **tornado-matrices** (Apache 2.0)
   - Matrix data structures (`Matrix2DFloat`, `Matrix2DInt`, etc.)

6. **tornado-unittests** (Apache 2.0)
   - Comprehensive unit test suite organized by feature area
   - Custom test runner with backend-aware exception handling

7. **tornado-benchmarks** (Apache 2.0)
   - JMH benchmarks for performance evaluation

8. **tornado-examples** (Apache 2.0)
   - Example programs demonstrating TornadoVM usage

9. **tornado-assembly**
   - Packaging and distribution configuration

### Compilation Pipeline

TornadoVM extends the Graal compiler with custom compilation tiers:

1. **Sketch Tier**: Initial graph construction and TornadoVM-specific transformations
2. **High Tier**: High-level optimizations (inlining, loop transformations)
3. **Mid Tier**: Mid-level optimizations
4. **Low Tier**: Backend-specific lowering and code generation

Key classes:
- `TornadoSketchTier`, `TornadoHighTier`, `TornadoMidTier`, `TornadoLowTier` (tornado-runtime/src/main/java/uk/ac/manchester/tornado/runtime/graal/compiler/)
- `TornadoCodeGenerator`: Generates backend-specific code
- Custom IR nodes in `tornado-runtime/src/main/java/uk/ac/manchester/tornado/runtime/graal/nodes/`

### Programming Model

TornadoVM supports two APIs:

**Loop Parallel API** (Sequential style with `@Parallel` annotations):
```java
for (@Parallel int i = 0; i < size; i++) {
    // Parallel execution
}
```

**Kernel API** (Explicit thread management):
```java
private static void kernel(KernelContext context, ...) {
    int idx = context.globalIdx;
    int idy = context.globalIdy;
    // Explicit thread indexing
}
```

### Task Execution Flow

1. User creates `TaskGraph` with tasks and data transfers
2. `TaskGraph.snapshot()` creates `ImmutableTaskGraph`
3. `TornadoExecutionPlan` wraps immutable task graph
4. `executionPlan.execute()` triggers:
   - Compilation (if needed) via backend-specific compiler
   - Data transfers to device
   - Kernel execution
   - Data transfers back to host
5. Results returned in `TornadoExecutionResult`

### Backend Architecture

Each backend implements:
- **Code generation**: Translates Graal IR to OpenCL C / PTX / SPIR-V
- **Memory management**: Device memory allocation and data transfers
- **Compilation**: Invokes device compiler (OpenCL runtime, NVCC, etc.)
- **Execution**: Kernel launches and synchronization

Backend entry points:
- `OCLBackendImpl` (tornado-drivers/opencl/)
- `PTXBackendImpl` (tornado-drivers/ptx/)
- `SPIRVBackendImpl` (tornado-drivers/spirv/)

### Dynamic Reconfiguration

TornadoVM can automatically select the best device at runtime:

```java
executionPlan.withDynamicReconfiguration(Policy.PERFORMANCE, DRMode.PARALLEL)
             .execute();
```

Policies: `PERFORMANCE`, `END_2_END`, `LATENCY`

## Development Workflow

### Contributing

1. Fork repository and create branch from `develop` branch
2. Make changes following code formatting guidelines
3. Run tests: `make tests` or `make fast-tests`
4. Run checkstyle: `make checkstyle`
5. Submit PR to `develop` branch
6. Sign [CLA](https://cla-assistant.io/beehive-lab/TornadoVM) when prompted
7. Address review comments (at least 2 reviewers)

### Common Development Tasks

- **Adding a new optimization pass**: Extend compiler tiers in `tornado-runtime/src/main/java/uk/ac/manchester/tornado/runtime/graal/compiler/`
- **Adding backend support**: Implement `TornadoAcceleratorBackend` interface in new `tornado-drivers/<backend>` module
- **Adding built-in functions**: Add to backend-specific `builtins/` directory
- **Adding Graal IR nodes**: Extend nodes in `tornado-runtime/src/main/java/uk/ac/manchester/tornado/runtime/graal/nodes/`

### Debugging

```bash
# Print generated kernel code
tornado --printKernel <mainClass>

# Enable debug mode
tornado --debug <mainClass>

# Full debug output
tornado-test --fullDebug <testClass>

# Dump Graal IR to IGV (Ideal Graph Visualizer)
tornado-test --igv <testClass>

# Print internal bytecodes
tornado-test --printBytecodes <testClass>

# Enable profiler
tornado --enableProfiler console <mainClass>
tornado-test --enableProfiler console <testClass>

# Check available devices
tornado --devices
```

### Important Notes

- The main branch for PRs is typically `feat/benchmarks/extension` (check current branch structure)
- TornadoVM requires proper JDK setup (OpenJDK 21 or GraalVM JDK 21)
- Each backend requires corresponding drivers (OpenCL, CUDA, Level Zero)
- Tests may be skipped on unsupported backends/devices
- Use `--quickPass` for faster test iterations during development
