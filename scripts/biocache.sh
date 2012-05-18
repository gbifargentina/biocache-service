#!/bin/bash
# Make sure only tomcat can run our script
if [ $(whoami) != "root" ]
then
   echo "This script must be run as the root user."
   exit 1
fi

BIOCACHE_INSTALL=/usr/local/biocache

cd $BIOCACHE_INSTALL
rm -Rf lib
rm biocache.jar
wget http://maven.ala.org.au/repository/au/org/ala/biocache-store/1.0-SNAPSHOT/biocache-store-1.0-SNAPSHOT-assembly.jar
mv biocache-store-1.0-SNAPSHOT-assembly.jar biocache.jar
jar xf biocache.jar lib lib
echo 'Upgrade complete.'