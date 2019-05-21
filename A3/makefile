JC = javac
.SUFFIXES: .java .class
.java.class:
	$(JC) $*.java

CLASSES = \
	packet.java \
	link_cost.java \
	router.java

default: classes

classes: $(CLASSES:.java=.class)

clean:
	$(RM) *.class
