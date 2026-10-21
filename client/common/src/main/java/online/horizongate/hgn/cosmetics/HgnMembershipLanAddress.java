package online.horizongate.hgn.cosmetics;

import java.io.IOException;
import java.net.InetAddress;

/** Resolves only literal private or loopback IPv4 backend routes, without DNS lookup. */
final class HgnMembershipLanAddress {
	static final String DEFAULT_LAN_HOST = "192.168.2.228";

	private HgnMembershipLanAddress() {
	}

	static InetAddress configured(String override) throws IOException {
		String address = override == null ? DEFAULT_LAN_HOST : override;
		String[] octets = address.split("\\.", -1);
		if (octets.length != 4) throw invalid();
		byte[] bytes = new byte[4];
		for (int index = 0; index < octets.length; index++) {
			String octet = octets[index];
			if (octet.isEmpty() || octet.length() > 3
				|| (octet.length() > 1 && octet.charAt(0) == '0')) {
				throw invalid();
			}
			int value = 0;
			for (int character = 0; character < octet.length(); character++) {
				char digit = octet.charAt(character);
				if (digit < '0' || digit > '9') throw invalid();
				value = value * 10 + digit - '0';
			}
			if (value > 255) throw invalid();
			bytes[index] = (byte) value;
		}
		int first = Byte.toUnsignedInt(bytes[0]);
		int second = Byte.toUnsignedInt(bytes[1]);
		boolean exactLoopback = first == 127 && second == 0
			&& Byte.toUnsignedInt(bytes[2]) == 0 && Byte.toUnsignedInt(bytes[3]) == 1;
		if (!exactLoopback && first != 10
			&& !(first == 172 && second >= 16 && second <= 31)
			&& !(first == 192 && second == 168)) {
			throw invalid();
		}
		return InetAddress.getByAddress(bytes);
	}

	private static IOException invalid() {
		return new IOException("HGN backend LAN address must be a private or loopback IPv4 literal");
	}
}
