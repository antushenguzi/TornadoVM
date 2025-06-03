#!/bin/bash

# 设置输出日志路径和 UPS IP
LOG_FILE="/home/yinyu/TornadoVM/3rdJune/3rdJune.log"
ENERGY_DIR="/home/yinyu/TornadoVM/3rdJune/energyMetrics"
UPS_IP="192.168.8.193"

# 确保 energyMetrics 目录存在
mkdir -p "$ENERGY_DIR"

# Python 脚本测试项
for bench in dft blurFilter matrixmultiplication1d matrixmultiplication2d; do
    echo "Running Python benchmark: $bench" | tee -a "$LOG_FILE"
    ./tornado-benchmarks-YMu.py \
        --full \
        --jvm="-Dtornado.ups.ip=$UPS_IP" \
        --skipTornadoVM \
        --iterations 100 \
        --delayInterval 60 \
        --benchmark "$bench" \
        --delayEnergyInterval 50 \
        --dumpEnergyTable "$ENERGY_DIR" >> "$LOG_FILE"
done

# Java benchmark for sgemm
echo "Running Java benchmark: sgemm" | tee -a "$LOG_FILE"
tornado \
    --jvm="-Xms32G -Xmx32G -Dtornado.device.memory=16GB -server -Dtornado.recover.bailout=False -Dtornado.benchmarks.skipTornadoVM=True -Denergy.monitor.interval=50 -Ddump.energy.metrics.to.directory=$ENERGY_DIR -Dtornado.ups.ip=$UPS_IP" \
    -m tornado.benchmarks/uk.ac.manchester.tornado.benchmarks.BenchmarkRunner \
    --params="sgemm 2000 4096 4096" >> "$LOG_FILE"
