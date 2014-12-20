#!/bin/bash

if [ "${GATE_HOME}" == "" ]
then
  echo Environment variable GATE_HOME not set
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
ROOTDIR=`cd "$SCRIPTDIR"/..; pwd -P`

java -cp $ROOTDIR:$ROOTDIR/Disambiguation.jar:$ROOTDIR/lib/'*':${GATE_HOME}/bin/gate.jar:${GATE_HOME}/lib/'*' gate.creole.disambiguation.AnalyzeStructSimLog "$@"
