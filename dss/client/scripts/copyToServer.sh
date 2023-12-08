
# make a copy of build output
rm -rf build-zip
cp -r build build-zip
cd build-zip

# update config to self-reference
sed -i.bak -e 's/window.serverBase[^;]\+/window.serverBase = "\/"/g' env.js

# zip it up into the resources directory
rm -f ../../server/src/main/resources/clientSite.zip
zip -r ../../server/src/main/resources/clientSite.zip *

# done
cd ..

