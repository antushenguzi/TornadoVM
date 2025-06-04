#!/bin/bash

LOG_FILE="/home/yinyu/TornadoVM/3rdJune/3rdJune.log"
ENERGY_DIR="/home/yinyu/TornadoVM/3rdJune/energyMetrics"
UPS_IP="192.168.8.193"
PYTHON_SCRIPT="/home/yinyu/TornadoVM/bin/bin/tornado-benchmarks-YMu.py"

mkdir -p "$ENERGY_DIR"

# Python benchmarks
for bench in dft blurFilter matrixmultiplication1d matrixmultiplication2d; do
    echo "Running Python benchmark: $bench" | tee -a "$LOG_FILE"
    python3 "$PYTHON_SCRIPT" \
        --full \
        --jvm="-Dtornado.ups.ip=$UPS_IP" \
        --skipTornadoVM \
        --iterations 100 \
        --delayInterval 60 \
        --benchmark "$bench" \
        --delayEnergyInterval 50 \
        --dumpEnergyTable "$ENERGY_DIR" >> "$LOG_FILE"
done

# Java benchmark
echo "Running Java benchmark: sgemm" | tee -a "$LOG_FILE"
tornado \
    --jvm="-Xms32G -Xmx32G -Dtornado.device.memory=16GB -server -Dtornado.recover.bailout=False -Dtornado.benchmarks.skipTornadoVM=True -Denergy.monitor.interval=50 -Ddump.energy.metrics.to.directory=$ENERGY_DIR -Dtornado.ups.ip=$UPS_IP" \
    -m tornado.benchmarks/uk.ac.manchester.tornado.benchmarks.BenchmarkRunner \
    --params="sgemm 2000 4096 4096" >> "$LOG_FILE"
