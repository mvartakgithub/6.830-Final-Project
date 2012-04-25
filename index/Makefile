JAVA=java
JAVAC=javac
JFLAGS=-Xlint:deprecation
CLASSPATH=.:/home/aksarkar/lucene-3.6.0/lucene-core-3.6.0.jar

.SUFFIXES: .java .class
.java.class:
	$(JAVAC) -classpath $(CLASSPATH) $(JFLAGS) $*.java

index: Indexer.class
	$(JAVA) -classpath $(CLASSPATH) Indexer /home/mvartak/acmdatafiles/acmdl/proceeding/

clean:
	rm -f *.class

.PHONY: clean
