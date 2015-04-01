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

totalret=0

ts=`date +%Y%m%d%H%M%S`

log=/tmp/runUnitTests$$.log
echo Running unit tests on `date` > $log

## create the output directories if not already there and empty them
mkdir $SCRIPTDIR/debug1.out >& /dev/null
rm $SCRIPTDIR/debug1.out/* >& /dev/null

$ROOTDIR/../yodie-tools/bin/runPipeline.sh -c $SCRIPTDIR/aida-a-tuning-sample1.config.yaml -nl -d -P $SCRIPTDIR/compareAndEvaluate.xgapp $ROOTDIR/main/main.xgapp $SCRIPTDIR/debug1 $SCRIPTDIR/debug1.out |& tee -a $log
grep -q "=== UNIT TEST: DIFFERENCE" $log | grep -v -q "=== UNIT TEST: DIFFERENCE" 
ret=$?
totalret=$ret


if [ $totalret != 0 ]
then
  cp $log $ROOTDIR/runUnitTest-$ts.log
  echo 'UNIT TEST DIFFERENCES!' Log is in $ROOTDIR/runUnitTest-$ts.log
  grep "=== UNIT TEST: DIFFERENCE" $log
  echo 'UNIT TEST DIFFERENCES!' Log is in $ROOTDIR/runUnitTest-$ts.log
  rm $log
  exit 1
else 
  rm $log
fi
