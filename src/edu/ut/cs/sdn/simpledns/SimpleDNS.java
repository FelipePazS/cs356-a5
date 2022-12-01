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
					dnsResponse = recursiveDNS(dns, rootServerIp, socket);
				}
				else {
					System.out.println("--Is non recursive.");
					dnsResponse = nonrecursiveDNS(dns, socket);
				}

				for (DNSResourceRecord answer : dnsResponse.getAnswers()) {
					System.out.println("--Going over EC2");
					//handle EC2
					if (DNS.TYPE_A == queryType && DNS.TYPE_CNAME == answer.getType()) {
						// longest match
						DNSRdataString ec2String = ec2match(answer);
						if (ec2String != null){
							DNSResourceRecord txtRecord = new DNSResourceRecord(answer.getName(), DNS_TXT, ec2String);
							dnsResponse.addAnswer(txtRecord);
						}
					}
				}
				
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

	private static DNS recursiveDNS(DNS dns, String IP,  DatagramSocket socket) {
		try {

			InetAddress inet = InetAddress.getByName(IP);
			DatagramPacket sendPacket = new DatagramPacket(dns.serialize(), dns.getLength(), inet, SEND_PORT);
			DatagramPacket receivePacket = new DatagramPacket(new byte[MAX_PACKET_SIZE], MAX_PACKET_SIZE);

			socket.send(sendPacket);
			socket.receive(receivePacket);

			DNS recDNS = DNS.deserialize(receivePacket.getData(), receivePacket.getLength());
			List<DNSResourceRecord> answers = recDNS.getAnswers();

			for (DNSResourceRecord answer : answers){
				if (answer.getName().equals(dns.getQuestions().get(0).getName())){
					return recDNS;
				}
			}
			// didn't got an answer, loop through the Authority RR that we got.
			for (DNSResourceRecord authority : recDNS.getAuthorities()){
				boolean got_a_match = false;
				String name = authority.getData().toString();
				System.out.println("--Trying authority " + name);
				for (DNSResourceRecord additional : recDNS.getAdditional()){
					if (additional.getName().equals(name)){
						got_a_match = true;
						DNS responseDNS = recursiveDNS(dns, additional.getData().toString(), socket);
						List<DNSResourceRecord> answers2 = responseDNS.getAnswers();
						if  (answers2.size() > 0){
							DNSResourceRecord answer2 = answers2.get(0);
							System.out.println("--Got answer from " + name);
							System.out.println("--Answer: " + answer2.toString());
							if (answer2.getType() == DNS.TYPE_CNAME){
								//solve for CNAME
								System.out.println("--Solving for CNAME");
								DNS CNAME_response = recursiveDNS(responseDNS, rootServerIp, socket);
								if (CNAME_response == null){
									System.out.println("--CNAME did not solved");
									continue;
								}
								else {
									List<DNSResourceRecord> CNAME_answers = CNAME_response.getAnswers();
									if (CNAME_answers.size() > 0){
										DNSResourceRecord CNAME_answer = CNAME_answers.get(0);
										System.out.println("--CNAME solved: " + CNAME_answer.toString());
										responseDNS.addAnswer(CNAME_answer);
										return responseDNS;
									}
									else {
										System.out.println("--CNAME did not solved");
									}
								}
							}
							else {
								return responseDNS;
							}
						}
					}
				}
				if (!got_a_match){
					/*	The assignment spec expects you to handle the case where you receive an Authoritative resource record, but no additional records, 
						in which case you will query the received Authoritative Name Server to first get it’s IP, and then continue your initial “recursive process” 
						and query the initially requested domain using the IP of the received Authoritative Name Server, to finally get the required IP. 
					*/
					DNSQuestion question = new DNSQuestion(name, DNS.TYPE_A);
					DNS n_dns = new DNS();
					n_dns.addQuestion(question);
					question.setType(DNS.TYPE_AAAA);
					n_dns.addQuestion(question);
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



		} catch (Exception e) {
			System.out.println(e);
			System.exit(0);
		}
		return null;
	}

	private static DNSRdataString ec2match(DNSResourceRecord answer){
		DNSRdataString ans = new DNSRdataString();
		String looking_for = answer.getData().toString();
		String best_name = null;
		String best_ip = null;
		try{
			Scanner sc = new Scanner(new File("./ec2.csv"));
			sc.useDelimiter(",");     
			while (sc.hasNext()){ 
				String address = sc.next();
				if (!sc.hasNext()){
					continue;
				}
				String name = sc.next();
				// longest match
			}   
			sc.close(); 
			return ans;
		} catch (Exception e) {
			System.out.println(e);
			System.exit(0);
		}
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

		} catch (Exception e) {
			System.out.println("--In non recursive DNS:");
			System.out.println(e);
			System.exit(0);
		}
		System.out.println("--Didn't got an answer:");
		return null;
	}
}
