package online.horizongate.hgn.cosmetics;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;

/** Keeps the same-host fallback restricted to literal private backend addresses. */
public final class HgnMembershipLanAddressTest {
	public static void main(String[] args) throws IOException {
		expectAddress(null, "192.168.2.228");
		for (String address : new String[] {
			"127.0.0.1", "10.0.0.7", "172.16.4.2",
			"172.31.255.254", "192.168.2.4"
		}) {
			expectAddress(address, address);
		}
		for (String address : new String[] {
			"", " ", "localhost", "::1", "0.0.0.0", "127.254.3.2", "169.254.1.2",
			"81.172.128.251", "8.8.8.8", "172.15.0.1", "172.32.0.1",
			"192.167.2.4", "192.169.2.4", "127.0.0.01",
			"192.168.2.004", "192.168.2.4:25582", "192.168.2.4/path",
			"192.168.2.256", "-1.168.2.4", "192.168.2.4.",
			"192.168.2.4\n"
		}) {
			try {
				HgnMembershipLanAddress.configured(address);
				throw new AssertionError("Accepted invalid LAN address: " + address);
			} catch (IOException expected) {
			}
		}
		System.out.println("Pinned HGN backend LAN address validation passed.");
	}

	private static void expectAddress(String configured, String expected) throws IOException {
		InetAddress address = HgnMembershipLanAddress.configured(configured);
		if (!(address instanceof Inet4Address)
			|| !address.getHostAddress().equals(expected)) {
			throw new AssertionError("Wrong private IPv4 route for " + configured);
		}
	}
}
