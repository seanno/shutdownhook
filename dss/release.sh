
cd release

VERSION=`head -1 versions.txt | awk 'NR=1 { print $1 }'`
ZIP=sqlhammer-v$VERSION.zip

rm -f $ZIP
zip -j $ZIP *.sh *.bat ../server/target/dss-server-1.0-SNAPSHOT.jar

\cp -f $ZIP sqlhammer-latest.zip

cd ..



