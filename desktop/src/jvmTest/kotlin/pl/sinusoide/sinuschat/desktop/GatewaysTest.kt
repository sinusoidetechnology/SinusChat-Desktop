package pl.sinusoide.sinuschat.desktop

import pl.sinusoide.sinuschat.desktop.net.Gateways
import kotlin.test.Test
import kotlin.test.assertEquals

class GatewaysTest {

    @Test
    fun parsesWindowsRouteTable() {
        val text = """
            IPv4 Route Table
            ===========================================================================
            Active Routes:
            Network Destination        Netmask          Gateway       Interface  Metric
                      0.0.0.0          0.0.0.0    10.208.228.28   10.208.228.120     35
                      0.0.0.0          0.0.0.0      192.168.1.1    192.168.1.20      50
            ===========================================================================
        """.trimIndent()
        assertEquals(listOf("10.208.228.28", "192.168.1.1"), Gateways.parseWindows(text))
    }

    @Test
    fun parsesLinuxRouteTable() {
        val text = "Iface\tDestination\tGateway \tFlags\tRefCnt\tUse\tMetric\tMask\n" +
            "wlan0\t00000000\t1CE4D00A\t0003\t0\t0\t600\t00000000\n" +
            "wlan0\t00E4D00A\t00000000\t0001\t0\t0\t600\t00FFFFFF\n"
        assertEquals(listOf("10.208.228.28"), Gateways.parseLinux(text))
    }
}
