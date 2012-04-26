JAVA=java
JAVAC=javac
JFLAGS=-Xlint:deprecation
CLASSPATH=.:/home/aksarkar/lucene-3.6.0/lucene-core-3.6.0.jar
DOCS=/home/mvartak/acmdatafiles/acmdl/
OUT=$(HOME)/index

.SUFFIXES: .java .class
.java.class:
	$(JAVAC) -classpath $(CLASSPATH) $(JFLAGS) $*.java

$(OUT): Indexer.class
	$(JAVA) -classpath $(CLASSPATH) Indexer $(DOCS) $(OUT)

clean:
	rm -f *.class

.PHONY: clean
