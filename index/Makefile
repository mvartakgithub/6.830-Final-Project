JAVA=java
JAVAC=javac
JFLAGS=-Xlint:deprecation
CLASSPATH=.:$(HOME)/lucene-3.6.0/lucene-core-3.6.0.jar:$(HOME)/postgresql-9.1-902.jdbc4.jar
DOCS=/home/mvartak/acmdatafiles/acmdl/
OUT=$(HOME)/index

.SUFFIXES: .java .class
.java.class:
	$(JAVAC) -classpath $(CLASSPATH) $(JFLAGS) $*.java

$(OUT): Indexer.class
	./index.sh $(CLASSPATH) $(DOCS) $(OUT)

clean:
	rm -f *.class

.PHONY: clean
