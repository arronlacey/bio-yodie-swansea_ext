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
ROOTDIR=`cd "$SCRIPTDIR/.."; pwd -P`

log=/tmp/runUnitTests$$.log
$ROOTDIR/../yodie-tools/bin/runPipeline.sh -nl -r -d -P compareAndEvaluate.xgapp ../main/main.xgapp aida-a-tuning-sample/ |& tee $log
grep -q "=== UNIT TEST: DIFFERENCE" $log
ret=$?
if [ $ret == 0 ]
then
  echo UNIT TEST DIFFERENCES, log is in $log
  exit 1
else 
  rm $log
fi
