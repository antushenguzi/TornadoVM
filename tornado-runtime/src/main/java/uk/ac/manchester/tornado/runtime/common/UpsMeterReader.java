/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2024, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 */
package uk.ac.manchester.tornado.runtime.common;

import org.snmp4j.CommunityTarget;
import org.snmp4j.PDU;
import org.snmp4j.Snmp;
import org.snmp4j.TransportMapping;
import org.snmp4j.event.ResponseEvent;
import org.snmp4j.mp.SnmpConstants;
import org.snmp4j.smi.Address;
import org.snmp4j.smi.GenericAddress;
import org.snmp4j.smi.OID;
import org.snmp4j.smi.OctetString;
import org.snmp4j.smi.VariableBinding;
import org.snmp4j.transport.DefaultUdpTransportMapping;

public class UpsMeterReader {

    private static final String OUTPUT_VOLTAGE_OID = "1.3.6.1.2.1.33.1.4.4.1.2.1";
    private static final String OUTPUT_POWER_OID = "1.3.6.1.2.1.33.1.4.4.1.4.1";
    private static final String COMMUNITY = "public";
    private static final int SNMP_VERSION = SnmpConstants.version1;

    private static String addressString = TornadoOptions.UPS_IP_ADDRESS;

    private static String latestPowerValue;

    // Single shared SNMP components
    private static Snmp snmp;
    private static CommunityTarget target;

    static {
        try {
            // Initialize SNMP and target ONCE
            if (addressString != null) {
                Address address = GenericAddress.parse("udp:" + addressString + "/161");
                TransportMapping<?> transport = new DefaultUdpTransportMapping();
                snmp = new Snmp(transport);
                transport.listen();

                target = new CommunityTarget();
                target.setCommunity(new OctetString(COMMUNITY));
                target.setAddress(address);
                target.setRetries(2);
                target.setTimeout(200);
                target.setVersion(SNMP_VERSION);
            } else {
                System.err.println("Error: UPS IP address not set.");
            }
        } catch (Exception e) {
            System.err.println("Error initializing SNMP: " + e.getMessage());
        }
    }

    private static String getSnmpValue(String oid) {
        if (snmp == null || target == null) {
            return null;
        }

        try {
            PDU pdu = new PDU();
            pdu.add(new VariableBinding(new OID(oid)));
            pdu.setType(PDU.GET);

            ResponseEvent response = snmp.get(pdu, target);

            if (response != null && response.getResponse() != null) {
                latestPowerValue = response.getResponse().get(0).getVariable().toString();
                return latestPowerValue;
            } else {
                return (latestPowerValue != null) ? latestPowerValue : "-1";
            }
        } catch (Exception e) {
            System.err.println("Error in SNMP GET: " + e.getMessage());
        }

        return null;
    }

    public static String getOutputPowerMetric() {
        return getSnmpValue(OUTPUT_POWER_OID);
    }

    public static String getOutputVoltageMetric() {
        return getSnmpValue(OUTPUT_VOLTAGE_OID);
    }

    // Optional: Call this when the program is done
    public static void shutdown() {
        try {
            if (snmp != null) {
                snmp.close();
            }
        } catch (Exception e) {
            System.err.println("Error closing SNMP: " + e.getMessage());
        }
    }
}
