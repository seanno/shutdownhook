#~/bin/sh

rm -f *~
rm -f *.class
rm -f radio.jar

if [ ! -f gson.2.8.6.jar ]
then
	curl --location \
		 --output gson.2.8.6.jar \
		 https://search.maven.org/remotecontent?filepath=com/google/code/gson/gson/2.8.6/gson-2.8.6.jar
fi

javac -cp '*':. *.java

if [ $? -eq 0 ]
then
	jar cvfe radio.jar Main *.class *.html
	rm -f *.class
fi



