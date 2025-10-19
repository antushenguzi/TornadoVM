// Simple test program to verify RAPL native code works
#include <iostream>
#include <fstream>
#include <unistd.h>

int main() {
    std::cout << "=== RAPL Native Code Test ===" << std::endl;

    // Test 1: Check powercap sysfs
    std::cout << "\n1. Testing powercap sysfs access..." << std::endl;
    std::string energy_file = "/sys/class/powercap/intel-rapl/intel-rapl:0/energy_uj";
    std::ifstream energy_stream(energy_file);

    if (energy_stream.is_open()) {
        long energy1, energy2;
        energy_stream >> energy1;
        std::cout << "   ✓ Initial energy: " << energy1 << " μJ" << std::endl;

        energy_stream.close();

        // Wait a bit
        usleep(100000); // 100ms

        // Read again
        energy_stream.open(energy_file);
        energy_stream >> energy2;
        energy_stream.close();

        long energy_diff = energy2 - energy1;
        long power_mw = (energy_diff * 10000) / 100; // 100ms to mW

        std::cout << "   ✓ Final energy: " << energy2 << " μJ" << std::endl;
        std::cout << "   ✓ Energy delta: " << energy_diff << " μJ" << std::endl;
        std::cout << "   ✓ Estimated power: " << power_mw << " mW" << std::endl;
        std::cout << "   SUCCESS: Powercap RAPL is working!" << std::endl;

        return 0;
    } else {
        std::cerr << "   ✗ Cannot open: " << energy_file << std::endl;
        std::cerr << "   FAIL: Powercap not available" << std::endl;
        return 1;
    }
}
