JC = javac
.SUFFIXES: .java .class
.java.class:
	$(JC) $*.java

CLASSES = \
	sender.java \
	receiver.java \
	packet.java

default: classes

classes: $(CLASSES:.java=.class)

clean:
	$(RM) *.class
