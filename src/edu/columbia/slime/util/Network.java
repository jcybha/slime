package edu.columbia.slime.util;

import java.util.Enumeration;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;


public class Network {
	public static final Log LOG = LogFactory.getLog(Network.class);
	public static String myIP = getMyAddressInternal();

	public static String getMyAddress() {
		return myIP;
	}

	private static String getMyAddressInternal() {
		try {
			Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
			while (interfaces.hasMoreElements()) {
				NetworkInterface iface = interfaces.nextElement();
				// filters out 127.0.0.1 and inactive interfaces
				if (iface.isLoopback() || !iface.isUp())
					continue;

				Enumeration<InetAddress> addresses = iface.getInetAddresses();
				while (addresses.hasMoreElements()) {
					InetAddress addr = addresses.nextElement();
					String ip = addr.getHostAddress();
					if (ip.contains(":")) // ipv6
						continue;
					return ip;
				}
			}
		} catch (SocketException e) {
		}
		return "";
	}

	public static boolean checkIfMyAddress(String target) {
		try {
			Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
			while (interfaces.hasMoreElements()) {
				NetworkInterface iface = interfaces.nextElement();
				// filters out 127.0.0.1 and inactive interfaces
				if (iface.isLoopback() || !iface.isUp())
					continue;

				Enumeration<InetAddress> addresses = iface.getInetAddresses();
				while (addresses.hasMoreElements()) {
					InetAddress addr = addresses.nextElement();
					String ip = addr.getHostAddress();
					//System.out.println(iface.getDisplayName() + " " + ip);
					if (target.equals(ip))
						return true;
				}
			}
		} catch (SocketException e) {
		}
		return false;
	}
}
