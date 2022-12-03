package edu.ut.cs.sdn.simpledns;

import edu.ut.cs.sdn.simpledns.packet.DNS;
import edu.ut.cs.sdn.simpledns.packet.DNSQuestion;
import edu.ut.cs.sdn.simpledns.packet.DNSRdataString;
import edu.ut.cs.sdn.simpledns.packet.DNSResourceRecord;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.List;
import java.util.Scanner;  
import java.io.*;
import java.util.ArrayList;

class EC2Entry {
	private String ip;
	private String mask;
	private String location;

	public EC2Entry(String ip, String mask, String location) {
		this.ip = ip;
		this.mask = mask;
		this.location = location;
	}

	public String getIp() {
		return this.ip;
	}

	public String getMask() {
		return this.mask;
	}

	public String getLocation() {
		return this.location;
	}


}

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
	private static List<EC2Entry> ec2Entries;




	/*
	TODO: handle EC2, just have to match the strings I think
	Finish recursive method

	This is a very straightforward task. You can obtain the address and port of the client (dig) from its incoming request. Then, sending a response is
	 a matter of sending an appropriately constructed DatagramPacket via the DatagramSocket through which the request was received.

	 If you don’t get an answer, you should now query in a loop the name servers present in the authority section.
	To make the query to these name servers, take their ip address (to send dns udp packet) from the additional section, it will most likely be there.
	If additional section doesn’t have the ip address for that, query the root name server for the ip address of the name server from authority section, and then make the query
	If the above fails, just ignore and try the other name servers in the authority list
	 */
	public static void main(String[] args)
	{

		if (REQUIRED_NUM_ARGS != args.length) System.exit(0);
		if (!ROOT_IP_ARG.equals(args[0])) System.exit(0);
		if (!EC2_ARG.equals(args[2])) System.exit(0);

		rootServerIp = args[1];
		ec2 = args[3];
		ec2Entries = getEc2Entries(ec2);


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

				DNS dnsResponse = new DNS();
				if(dns.isRecursionDesired()){
					System.out.println("--Is recursive.");
					// take out all questions
					DNSQuestion q = dns.getQuestions().get(0);
					List<DNSQuestion> qs = new ArrayList<DNSQuestion>();
					qs.add(q);
					dns.setQuestions(qs);
					// change recursion and id
					dns.setRecursionDesired(false);
					dnsResponse = recursiveDNS(dns, rootServerIp, socket);
				}
				else {
					System.out.println("--Is non recursive.");
					dnsResponse = nonrecursiveDNS(dns, socket);
				}

				DNSResourceRecord answer_to_add = null;
				for (DNSResourceRecord answer : dnsResponse.getAnswers()) {
					System.out.println("--Going over EC2");
					//handle EC2
					if (DNS.TYPE_A == answer.getType()) {
						// longest match
						DNSRdataString ec2String = ec2match(answer);
						if (ec2String != null){
							DNSResourceRecord txtRecord = new DNSResourceRecord(answer.getName(), DNS_TXT, ec2String);
							answer_to_add = txtRecord;
						}
					}
				}
				dnsResponse.addAnswer(answer_to_add);

				System.out.println("--Sending response packet:");
				System.out.println(dnsResponse.toString());
				DatagramPacket responsePacket = new DatagramPacket(dnsResponse.serialize(), dnsResponse.getLength(), packet.getAddress(), packet.getPort());
				socket.send(responsePacket);
				socket.close();
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

	private static List<EC2Entry> getEc2Entries(String ec2CSV) {
		List<EC2Entry> ec2Entries = new ArrayList<>();
		try {
			System.out.println("--Getting EC2 entries");
			BufferedReader reader = new BufferedReader(new FileReader(ec2CSV));
			String ec2Line = reader.readLine();
			while (ec2Line != null) {
				String[] ipAndLoc = ec2Line.split(",");
				String[] ipAndMask = ipAndLoc[0].split("/");
				String ec2Loc = ipAndLoc[1];
				String ec2Ip = ipAndMask[0];
				String ec2Mask = ipAndMask[1];
				EC2Entry ec2Entry = new EC2Entry(ec2Ip, ec2Mask, ec2Loc);
				ec2Entries.add(ec2Entry);
				ec2Line = reader.readLine();
			}
			reader.close();
		} catch (Exception e) {
			System.out.println(e);
			System.exit(0);
		}
		return ec2Entries;
	}

	private static DNS recursiveDNS(DNS dns, String IP,  DatagramSocket socket) {
		try {
			System.out.println("--Recursive over:");
			System.out.println(dns.toString());
			DNSQuestion question = dns.getQuestions().get(0);
			InetAddress inet = InetAddress.getByName(IP);
			DatagramPacket sendPacket = new DatagramPacket(dns.serialize(), dns.getLength(), inet, SEND_PORT);
			DatagramPacket receivePacket = new DatagramPacket(new byte[MAX_PACKET_SIZE], MAX_PACKET_SIZE);

			socket.send(sendPacket);
			socket.receive(receivePacket);

			DNS recDNS = DNS.deserialize(receivePacket.getData(), receivePacket.getLength());
			List<DNSResourceRecord> answers = recDNS.getAnswers();
			for (DNSResourceRecord answer : answers){
				System.out.println("--Got an answer: " + answer.toString());
				if (answer.getName().equals(question.getName()) && answer.getType() == question.getType()){
					System.out.println("--It was what I was looking for");
					return recDNS;
				}
				// got a different answer, maybe I asked for A / AAAA and got CNAME?
				if ((question.getType() == DNS.TYPE_A || question.getType() == DNS.TYPE_AAAA) && answer.getType() == DNS.TYPE_CNAME){
					System.out.println("--It was CNAME when I looked for A / AAAA");
					// DNS CNAME_response = solveCNAME(answer.getData().toString(), rootServerIp, socket, recDNS);
					DNS CNAME_response = solveCNAME(answer.getData().toString(), IP, socket, recDNS);
					if (CNAME_response != null){
						return CNAME_response;
					}
				}
			}
			// didn't got an answer, loop through the Authority RR that we got.
			System.out.println("--Didn't got an answer, going over authorities");
			for (DNSResourceRecord authority : recDNS.getAuthorities()){
				boolean got_a_match = false;
				String name = authority.getData().toString();
				System.out.println("--Trying authority " + name);
				for (DNSResourceRecord additional : recDNS.getAdditional()){
					if (additional.getName().equals(name) && additional.getType() == DNS.TYPE_A){
						System.out.println("--Found additional that has IP for this authority.");
						got_a_match = true;
						DNS responseDNS = recursiveDNS(dns, additional.getData().toString(), socket);
						if  (responseDNS != null) return responseDNS;
					}
				}
				if (!got_a_match){
					/*	The assignment spec expects you to handle the case where you receive an Authoritative resource record, but no additional records, 
						in which case you will query the received Authoritative Name Server to first get it’s IP, and then continue your initial “recursive process” 
						and query the initially requested domain using the IP of the received Authoritative Name Server, to finally get the required IP. 
					*/
					question = new DNSQuestion(name, DNS.TYPE_A);
					DNS n_dns = new DNS();
					n_dns.addQuestion(question);
					n_dns.setQuery(true);
					DNS n_answer = recursiveDNS(n_dns, rootServerIp, socket);
					if (n_answer.getAnswers().size() > 0){
						got_a_match = true;
						List<DNSResourceRecord> n_answers = n_answer.getAnswers();
						DNS responseDNS = recursiveDNS(dns, n_answers.get(0).getData().toString(), socket);
						if (responseDNS.getAnswers().size() > 0){
							return responseDNS;
						}
					}
				}
			}
			return null;

		} catch (Exception e) {
			System.out.println("In recurssion: ");
			System.out.println(e);
			System.exit(0);
		}
		return null;
	}

	private static DNS solveCNAME(String name, String IP, DatagramSocket socket, DNS dns){
		DNSQuestion CNAME_question = new DNSQuestion(name, DNS.TYPE_A);
		DNS CNAME_query = new DNS();
		CNAME_query.setQuery(true);
		CNAME_query.addQuestion(CNAME_question);
		CNAME_query.setRecursionDesired(false);
		CNAME_query.setRecursionAvailable(false);
		// CNAME_query.setId((short) (dns.getId() + 1));
		DNS CNAME_response = recursiveDNS(CNAME_query, IP, socket);
		if (CNAME_response != null){
			for (DNSResourceRecord answer : CNAME_response.getAnswers()){
				if (answer.getType() == DNS.TYPE_A || answer.getType() == DNS.TYPE_AAAA){
					System.out.println("--CNAME solved: " + answer.toString());
					dns.addAnswer(answer);
					return dns;
				}
			}
		}
		return null;
	}

	private static DNSRdataString ec2match(DNSResourceRecord answer){
		try {
			int ip = stringToIntIp(answer.getData().toString());
			int bestMask = 0;
			EC2Entry bestMatch = null;
			for (EC2Entry ec2Entry : ec2Entries) {
				// need to convert string ip to int ip to uase mask
				int entryIp = stringToIntIp(ec2Entry.getIp());
				int mask = Integer.parseInt(ec2Entry.getMask());
				int maskedIp = ip & mask;
				int maskedEntryIp = entryIp & mask;
				if (maskedIp == maskedEntryIp)
				{
					if ((null == bestMatch) || (mask > bestMask))
					{
						bestMask = mask;
						bestMatch = ec2Entry;
					}
				}

			}
			DNSRdataString ec2Txt = new DNSRdataString(bestMatch.getLocation() + "-" + ip);
			return ec2Txt;
		} catch (Exception e) {
			System.out.println("In ec2 match: ");
			System.out.println(e);
			System.exit(0);
		}
		return null;
	}

	private static int stringToIntIp (String stringIp) {
		int intIp = 0;
		String[] ipParts = stringIp.split("\\.");
		for (String ipPart : ipParts) {
			intIp = intIp << 8;
			intIp |= Integer.parseInt(ipPart);
		}
		return intIp;
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

		} catch (Exception e) {
			System.out.println("--In non recursive DNS:");
			System.out.println(e);
			System.exit(0);
		}
		System.out.println("--Didn't got an answer:");
		return null;
	}
}
