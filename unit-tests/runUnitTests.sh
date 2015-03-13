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
mkdir $SCRIPTDIR/aida-a-tuning-sample1.out >& /dev/null
rm $SCRIPTDIR/aida-a-tuning-sample1.out/* >& /dev/null
mkdir $SCRIPTDIR/aida-ee-sample1.out >& /dev/null
rm $SCRIPTDIR/aida-ee-sample1.out/* >& /dev/null
mkdir $SCRIPTDIR/en-tweets-training-sample1.out >& /dev/null
rm $SCRIPTDIR/en-tweets-training-sample1.out/* >& /dev/null

$ROOTDIR/../yodie-tools/bin/runPipeline.sh -c $SCRIPTDIR/aida-a-tuning-sample1.config.yaml -nl -d -P $SCRIPTDIR/compareAndEvaluate.xgapp $ROOTDIR/main/main.xgapp $SCRIPTDIR/aida-a-tuning-sample1 $SCRIPTDIR/aida-a-tuning-sample1.out |& tee -a $log
grep -q "=== UNIT TEST: DIFFERENCE" $log | grep -v -q "=== UNIT TEST: DIFFERENCE" 
ret=$?
totalret=$ret

$ROOTDIR/../yodie-tools/bin/runPipeline.sh -c $SCRIPTDIR/aida-ee-sample1.config.yaml -nl -d -P $SCRIPTDIR/compareAndEvaluate.xgapp $ROOTDIR/main/main.xgapp $SCRIPTDIR/aida-ee-sample1 $SCRIPTDIR/aida-ee-sample1.out |& tee -a $log
grep -q "=== UNIT TEST: DIFFERENCE" $log | grep -v -q "=== UNIT TEST: DIFFERENCE"
ret=$?
totalret=$((totalret + ret))

$ROOTDIR/../yodie-tools/bin/runPipeline.sh -c $SCRIPTDIR/en-tweets-training-sample1.config.yaml -nl -d -P $SCRIPTDIR/compareAndEvaluate.xgapp $ROOTDIR/main/main.xgapp $SCRIPTDIR/en-tweets-training-sample1 $SCRIPTDIR/en-tweets-training-sample1.out |& tee -a $log
grep -q "=== UNIT TEST: DIFFERENCE" $log | grep -v -q "=== UNIT TEST: DIFFERENCE"
ret=$?
totalret=$((totalret + ret))

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
