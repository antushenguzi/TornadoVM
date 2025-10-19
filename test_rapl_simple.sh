#!/bin/bash
# Simple test to verify RAPL is working on Intel CPU

echo "=== Testing RAPL Power Monitoring on Intel CPU ==="
echo ""
echo "1. Checking system setup..."
echo "   CPU: $(lscpu | grep 'Model name' | cut -d: -f2 | xargs)"
echo "   Powercap available: $(test -d /sys/class/powercap/intel-rapl && echo 'YES' || echo 'NO')"
echo "   MSR module loaded: $(lsmod | grep -q '^msr ' && echo 'YES' || echo 'NO')"
echo ""

echo "2. Testing direct RAPL read..."
if [ -r /sys/class/powercap/intel-rapl/intel-rapl:0/energy_uj ]; then
    E1=$(cat /sys/class/powercap/intel-rapl/intel-rapl:0/energy_uj)
    sleep 0.1
    E2=$(cat /sys/class/powercap/intel-rapl/intel-rapl:0/energy_uj)
    POWER=$(( ($E2 - $E1) * 10 / 1000 ))
    echo "   ✓ RAPL read successful: ~${POWER} mW"
else
    echo "   ✗ Cannot read RAPL counters"
fi
echo ""

echo "3. Checking TornadoVM devices..."
tornado --devices 2>&1 | grep -A 3 "Intel\|pthread"
echo ""

echo "4. Running example on Intel CPU with profiler..."
tornado --enableProfiler console --jvm="-Ds0.t0.device=0:1" \
    -m tornado.examples/uk.ac.manchester.tornado.examples.compute.Montecarlo \
    --params="1024" 2>&1 | strings | grep -E "POWER_USAGE|Intel|Device|pthread" | head -20

echo ""
echo "=== Test Complete ==="
