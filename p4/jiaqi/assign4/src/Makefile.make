all: TCPend

TCPend: TCPend.java
	javac *.java

clean:
	rm *.class