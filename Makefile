JFLAGS = -g
JC = javac
JVM = java
.SUFFIXES: .java .class
.java.class:
		$(JC) $(JFLAGS) -cp src/ $*.java

ROOT = <REPLACE WITH ROOT IP>
CSV = ec2.csv
CLASSES = \
	src/edu/ut/cs/sdn/simpledns/packet/DNS.java \
	src/edu/ut/cs/sdn/simpledns/packet/DNSQuestion.java \
	src/edu/ut/cs/sdn/simpledns/packet/DNSRdata.java \
	src/edu/ut/cs/sdn/simpledns/packet/DNSRdataAddress.java \
	src/edu/ut/cs/sdn/simpledns/packet/DNSRdataBytes.java \
	src/edu/ut/cs/sdn/simpledns/packet/DNSRdataName.java \
	src/edu/ut/cs/sdn/simpledns/packet/DNSRdataString.java \
	src/edu/ut/cs/sdn/simpledns/packet/DNSResourceRecord.java \
	src/edu/ut/cs/sdn/simpledns/SimpleDNS.java

all: classes

run:
	java -jar SimpleDNS.jar -r ${ROOT} -e ${CSV}

classes: $(CLASSES:.java=.class)

clean:
		$(RM) src/edu/ut/cs/sdn/simpledns/packet/*.class
		$(RM) src/edu/ut/cs/sdn/simpledns/*.class