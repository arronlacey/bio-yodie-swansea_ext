#!/bin/bash

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

echo $SCRIPTDIR

java -Dat.ofai.gate.modularpipelines.configFile="$2" \
-cp ${SCRIPTDIR}/evaluate.jar:${GATE_HOME}/bin/gate.jar:${GATE_HOME}/lib/* \
gate.eval.CrossValidate -t ${SCRIPTDIR}/disambiguation-ml-export.xgapp \
-n -1 -d "$1" -v ${SCRIPTDIR}/../plugins/VirtualCorpus

rm -rf ${SCRIPTDIR}/temp-data-store0000

