
# make a copy of build output
rm -rf dist-zip
cp -r dist dist-zip
cd dist-zip

# update config to self-reference
sed -i.bak -e 's/window.serverBase[^;]\+/window.serverBase = "\/"/g' env.js

# zip it up into the resources directory
rm -f ../../server/src/main/resources/clientSite.zip
zip -r ../../server/src/main/resources/clientSite.zip *

# done
cd ..

