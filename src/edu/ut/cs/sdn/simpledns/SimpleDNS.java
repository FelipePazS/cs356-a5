package edu.ut.cs.sdn.simpledns;

import edu.ut.cs.sdn.simpledns.packet.DNS;
import edu.ut.cs.sdn.simpledns.packet.DNSRdataString;
import edu.ut.cs.sdn.simpledns.packet.DNSResourceRecord;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.List;
import java.util.Scanner;  
import java.io.*;
import java.util.ArrayList;

public class SimpleDNS
{
	private static final int REQUIRED_NUM_ARGS = 4;
	private static final String ROOT_IP_ARG = "-r";
	private static final String EC2_ARG = "-e";

	private static final int MAX_PACKET_SIZE = 1500;

	private static final int LISTEN_PORT = 8053;
	private static final int SEND_PORT = 53;
	private static final short DNS_TXT = 16;

	private static String rootServerIp;
	private static String ec2;


	/*
	TODO: handle EC2, just have to match the strings I think
	Finish recursive method

	The assignment spec expects you to handle the case where you receive an Authoritative resource record, but no additional records, 
	in which case you will query the received Authoritative Name Server to first get it’s IP, and then continue your initial “recursive process” 
	and query the initially requested domain using the IP of the received Authoritative Name Server, to finally get the required IP.
	 */
	public static void main(String[] args)
	{

		if (REQUIRED_NUM_ARGS != args.length) System.exit(0);
		if (!ROOT_IP_ARG.equals(args[0])) System.exit(0);
		if (!EC2_ARG.equals(args[2])) System.exit(0);

		rootServerIp = args[1];
		ec2 = args[3];

		while (true) {
			try {
				System.out.println("--Starting to listen for packets");
				DatagramSocket socket = new DatagramSocket(LISTEN_PORT);
				DatagramPacket packet  = new DatagramPacket(new byte[MAX_PACKET_SIZE], MAX_PACKET_SIZE);
				socket.receive(packet);
				DNS dns = DNS.deserialize(packet.getData(), packet.getLength());
				System.out.println("--Got a packet:");
				System.out.println(dns.toString());

				short queryType = dns.getQuestions().get(0).getType();

				if (dns.getOpcode() != 0) continue;
				if (!validQueryType(queryType)) continue;

				List<DNSResourceRecord> answers = new ArrayList<DNSResourceRecord>();
				DNS dnsResponse = new DNS();
				if(dns.isRecursionDesired()){
					System.out.println("--Is recursive.");
					answers = recursiveDNS(dns);
				}
				else {
					System.out.println("--Is non recursive.");
					dnsResponse = nonrecursiveDNS(dns, socket);
				}

				// for (DNSResourceRecord answer : answers) {
				// 	System.out.println("--Going over answers");
				// 	//handle EC2
				// 	if (DNS.TYPE_A == queryType && DNS.TYPE_CNAME == answer.getType()) {
				// 		longest match
				// 		DNSRdataString ec2String = ec2match(answer);
				// 		DNSResourceRecord txtRecord = new DNSResourceRecord(answer.getName(), DNS_TXT, ec2String);
				// 		dnsResponse.addAnswer(txtRecord);
				// 	}
				// }
				
				System.out.println("--Sending response packet:");
				System.out.println(dnsResponse.toString());
				System.out.println("--Here 1");
				DatagramPacket responsePacket = new DatagramPacket(dnsResponse.serialize(), dnsResponse.getLength());
				System.out.println("--Here 1.5");
				socket.send(responsePacket);
				System.out.println("--Here 2");
				socket.close();
				System.out.println("--Here 3");
			} catch (Exception e) {
				System.out.println("--In Main:");
				System.out.println(e);
				System.exit(0);
			}
		}
	}


	private static boolean validQueryType(short queryType) {
		return (DNS.TYPE_A == queryType || DNS.TYPE_AAAA == queryType || DNS.TYPE_CNAME == queryType || DNS.TYPE_NS == queryType);
	}

	private static List<DNSResourceRecord> recursiveDNS(DNS dns) {
		// try {

		// 	InetAddress inet = InetAddress.getByName(rootServerIp);
		// 	DatagramSocket socket = new DatagramSocket();
		// 	DatagramPacket sendPacket = new DatagramPacket(dns.serialize(), dns.getLength(), inet, SEND_PORT);
		// 	DatagramPacket receivePacket = new DatagramPacket(new byte[MAX_PACKET_SIZE], MAX_PACKET_SIZE);

		// 	socket.send(sendPacket);
		// 	socket.receive(receivePacket);

		// 	DNS recDNS = DNS.deserialize(receivePacket.getData(), receivePacket.getLength());

		// 	List<DNSResourceRecord> answers = recDNS.getAnswers();
		// 	DNSResourceRecord rec;
		// 	if (answers.size() > 0) {
		// 		rec = answers.get(0);
		// 	} else {

		// 	}



		// } catch (Exception e) {
		// 	System.out.println(e);
		// 	System.exit(0);
		// }
		return null;
	}

	private static DNS nonrecursiveDNS(DNS dns, DatagramSocket socket) {
		try {
			System.out.println("--Asking the root server");
			InetAddress inet = InetAddress.getByName(rootServerIp);
			DatagramPacket sendPacket = new DatagramPacket(dns.serialize(), dns.getLength(), inet, SEND_PORT);
			DatagramPacket receivePacket = new DatagramPacket(new byte[MAX_PACKET_SIZE], MAX_PACKET_SIZE);

			socket.send(sendPacket);
			socket.receive(receivePacket);
			DNS recDNS = DNS.deserialize(receivePacket.getData(), receivePacket.getLength());
			System.out.println("--Received packet from root server:");
			System.out.println(recDNS.toString());
			return recDNS;

			// List<DNSResourceRecord> answers = recDNS.getAnswers();
			// DNSResourceRecord res;
			// socket.close();
			// if (answers.size() > 0) {
			// 	res = answers.get(0);
			// 	System.out.println("--It contains an answer:");
			// 	System.out.println(res.toString());
			// 	return res;
			// }

		} catch (Exception e) {
			System.out.println("--In non recursive DNS:");
			System.out.println(e);
			System.exit(0);
		}
		System.out.println("--Didn't got an answer:");
		return null;
	}


	private static DNSRdataString ec2match(DNSResourceRecord answer){
		DNSRdataString ans = new DNSRdataString();
		try{
			Scanner sc = new Scanner(new File("../ec2.csv"));
			sc.useDelimiter(",");     
			while (sc.hasNext()){  
				System.out.print(sc.next());
			}   
			sc.close(); 
			return ans;
		} catch (Exception e) {
			System.out.println(e);
			System.exit(0);
		}
		return null;
	}
}
