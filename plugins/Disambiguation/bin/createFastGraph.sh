#!/bin/bash

if [ "`which scala`" == "" ]
then 
  echo scala must be installed and mist be on the binary path
  exit 1
fi

tsv1="$1"
tsv2="$2"
graph="$3"

if [ "$graph" == "" ]
then 
  echo "need three parameters: tsvFileSortedByFromUri.tsv.gz tsvFileSortedByToUri.tsv.gz graphFile.gz"
  exit 1
fi


PRG="$0"
CURDIR="`pwd`"
# need this for relative symlinks
while [ -h "$PRG" ] ; do
  ls=`ls -ld "$PRG"`
  link=`expr "$ls" : '.*-> \(.*\)$'`
  if expr "$link" : '/.*' > /dev/null; then
    PRG="$link"
  else
    PRG=`dirname "$PRG"`"/$link"
  fi
done
SCRIPTDIR=`dirname "$PRG"`
SCRIPTDIR=`cd "$SCRIPTDIR"; pwd -P`
ROOTDIR=`cd "$SCRIPTDIR/.."; pwd -P`


time scala -cp "${ROOTDIR}"/lib/'*':"${ROOTDIR}"/Disambiguation.jar "${ROOTDIR}"/scala/createFastGraph.scala "${tsv1}" "${tsv2}" "${graph}" 
