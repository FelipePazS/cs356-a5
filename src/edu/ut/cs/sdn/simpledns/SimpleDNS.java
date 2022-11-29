package edu.ut.cs.sdn.simpledns;

import edu.ut.cs.sdn.simpledns.packet.DNS;
import edu.ut.cs.sdn.simpledns.packet.DNSResourceRecord;
import jdk.nashorn.internal.runtime.ECMAException;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.List;

public class SimpleDNS
{
	private static final int REQUIRED_NUM_ARGS = 4;
	private static final String ROOT_IP_ARG = "-r";
	private static final String EC2_ARG = "-e";

	private static final int MAX_PACKET_SIZE = 1500;
	private static final int LISTEN_PORT = 8053;

	private static String rootServerIp;
	private static String ec2;



	public static void main(String[] args)
	{

		if (REQUIRED_NUM_ARGS != args.length) System.exit(0);
		if (!ROOT_IP_ARG.equals(args[0])) System.exit(0);
		if (!EC2_ARG.equals(args[2])) System.exit(0);

		rootServerIp = args[1];
		ec2 = args[3];

		while (true) {
			try {
				DatagramSocket socket = new DatagramSocket(LISTEN_PORT);
				DatagramPacket packet  = new DatagramPacket(new byte[MAX_PACKET_SIZE], MAX_PACKET_SIZE);
				socket.receive(packet);
				DNS dns = DNS.deserialize(packet.getData(), packet.getLength());
				short queryType = dns.getQuestions().get(0).getType();
				if (!validQueryType(queryType)) continue;

			} catch (Exception e) {
				System.out.println(e);
				System.exit(0);
			}
		}

		System.out.println("Hello, DNS!");
	}


	private static boolean validQueryType(short queryType) {
		return (DNS.TYPE_A == queryType || DNS.TYPE_AAAA == queryType || DNS.TYPE_CNAME == queryType || DNS.TYPE_NS == queryType);
	}

	private static DNS recursiveDNS() {
		DNS dns = null;
		try {
			DatagramSocket socket = new DatagramSocket();
			DatagramPacket sendPacket = new DatagramPacket();

			DatagramPacket receivePacket = new DatagramPacket(new byte[MAX_PACKET_SIZE], MAX_PACKET_SIZE);
			socket.receive(receivePacket);
			dns = DNS.deserialize(receivePacket.getData(), receivePacket.getLength());

		} catch (SocketException e) {

		} catch (IOException e) {

		}
		List<DNSResourceRecord> dnsAnswers = dns.getAnswers();
		List<DNSResourceRecord> dnsAdditionals = dns.getAdditional();
		List<DNSResourceRecord> dnsAuthorities = dns.getAuthorities();

		if (dnsAnswers.size() > 0) {
			for (DNSResourceRecord answer : dnsAnswers) {

			}
		}


		return res;
	}
}
