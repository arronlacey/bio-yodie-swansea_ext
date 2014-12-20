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

java -Dat.ofai.gate.modularpipelines.configFile="$1" \
-cp ${SCRIPTDIR}/evaluate.jar:${GATE_HOME}/bin/gate.jar:${GATE_HOME}/lib/* \
gate.eval.CrossValidate -t ${SCRIPTDIR}/disambiguation-ml-training.xgapp -n -1

rm -rf ${SCRIPTDIR}/temp-data-store0000

