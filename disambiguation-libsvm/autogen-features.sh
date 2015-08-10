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

if [[ "$YODIE_PIPELINE" == "" ]]
then
  export YODIE_PIPELINE=`cd "$SCRIPTDIR"; cd ../../yodie-pipeline; pwd -P`
fi

groovy -cp "${GATE_HOME}/bin/gate.jar":"${GATE_HOME}/"'lib/*':"$YODIE_PIPELINE"/plugins/LodiePlugin/LodiePlugin.jar "$SCRIPTDIR"/groovy/autogenerate-ranking-jape.groovy "$SCRIPTDIR"/jape/create-rank-features.jape "$SCRIPTDIR"/feature-spec.auto "$@"

