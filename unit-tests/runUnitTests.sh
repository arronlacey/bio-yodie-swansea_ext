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

ts=`date +%Y%m%d_%H%M%S`

log=/tmp/runUnitTests-${ts}.log
echo Running unit tests on `date` > $log

## create the output directories if not already there and empty them
mkdir $SCRIPTDIR/aida-a-tuning-sample1.out >& /dev/null
rm $SCRIPTDIR/aida-a-tuning-sample1.out/* >& /dev/null
mkdir $SCRIPTDIR/aida-ee-sample1.out >& /dev/null
rm $SCRIPTDIR/aida-ee-sample1.out/* >& /dev/null
mkdir $SCRIPTDIR/en-tweets-training-sample1.out >& /dev/null
rm $SCRIPTDIR/en-tweets-training-sample1.out/* >& /dev/null

outDir=/tmp
outUrl=file://${outDir}

evalId=runUnitTests-aida-a-tuning-${ts}
$ROOTDIR/../yodie-tools/bin/runPipeline.sh -Dmodularpipelines.prparm.compareAndEvaluate.Evaluate.evaluationId=$evalId -Dmodularpipelines.prparm.compareAndEvaluate.Evaluate.outputDirectoryUrl=$outUrl -c $SCRIPTDIR/aida-a-tuning-sample1.config.yaml -nl -d -P $SCRIPTDIR/compareAndEvaluate.xgapp $ROOTDIR/main/main.xgapp $SCRIPTDIR/aida-a-tuning-sample1 $SCRIPTDIR/aida-a-tuning-sample1.out |& tee -a $log
grep -q "=== UNIT TEST:" $log | grep -v -q "=== UNIT TEST: DIFFERENCE" 
ret=$?
totalret=$ret

evalId=runUnitTests-aida-ee-${ts}
$ROOTDIR/../yodie-tools/bin/runPipeline.sh -Dmodularpipelines.prparm.compareAndEvaluate.Evaluate.evaluationId=$evalId -Dmodularpipelines.prparm.compareAndEvaluate.Evaluate.outputDirectoryUrl=$outUrl -c $SCRIPTDIR/aida-ee-sample1.config.yaml -nl -d -P $SCRIPTDIR/compareAndEvaluate.xgapp $ROOTDIR/main/main.xgapp $SCRIPTDIR/aida-ee-sample1 $SCRIPTDIR/aida-ee-sample1.out |& tee -a $log
grep -q "=== UNIT TEST:" $log | grep -v -q "=== UNIT TEST: DIFFERENCE"
ret=$?
totalret=$((totalret + ret))

evalId=runUnitTests-en-tweets-training-sample1-${ts}
$ROOTDIR/../yodie-tools/bin/runPipeline.sh -Dmodularpipelines.prparm.compareAndEvaluate.Evaluate.evaluationId=$evalId -Dmodularpipelines.prparm.compareAndEvaluate.Evaluate.outputDirectoryUrl=$outUrl -c $SCRIPTDIR/en-tweets-training-sample1.config.yaml -nl -d -P $SCRIPTDIR/compareAndEvaluate.xgapp $ROOTDIR/main/main.xgapp $SCRIPTDIR/en-tweets-training-sample1 $SCRIPTDIR/en-tweets-training-sample1.out |& tee -a $log
grep -q "=== UNIT TEST:" $log | grep -v -q "=== UNIT TEST: DIFFERENCE"
ret=$?
totalret=$((totalret + ret))

if [ $totalret != 0 ]
then
  cp $log $ROOTDIR/runUnitTest-$ts.log
  cp $outDir/EvaluateTagging-runUnitTests-*-$ts.tsv $ROOTDIR/
  echo 'UNIT TEST DIFFERENCES!' Log is in $ROOTDIR/runUnitTest-$ts.log, data files are in $ROOTDIR/EvaluateTagging-runUnitTests-*-$ts.tsv
  grep "=== UNIT TEST: DIFFERENCE" $log
  grep $ts EvaluateTagging-runUnitTests-*-$ts.tsv | grep 'th=none' | grep 'F1.0' | sed -e "s/.\+\.log://" -e "s/, type=Mention, th=none,//"
  echo 'UNIT TEST DIFFERENCES!' Log is in $ROOTDIR/runUnitTest-$ts.log
  rm $log
  rm $outFile
  exit 1
else 
  cp $log $ROOTDIR/runUnitTest-$ts.log
  cp $outFile/EvaluateTagging-runUnitTests-*-$ts.tsv $ROOTDIR/
  grep $ts EvaluateTagging-runUnitTests-*-$ts.tsv | grep 'th=none' | grep 'F1.0' | sed -e "s/.\+\.log://" -e "s/, type=Mention, th=none,//"
  echo 'UNIT TEST COMPLETED WITHOUT DIFFERENCES! Log is in' $ROOTDIR/runUnitTest-$ts.log, data files are in $ROOT/EvaluateTagging-runUnitTests-*-$ts.tsv
  rm $log
  rm $outFile
fi
