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
LOGDIR="$SCRIPTDIR/logs"

totalret=0

ts=`date +%Y%m%d_%H%M%S`

mkdir $LOGDIR
log=$LOGDIR/runUnitTests-${ts}.log
sum=$LOGDIR/runUnitTests-${ts}.summary
echo Running unit tests on `date` > $log

## create the output directories if not already there and empty them
mkdir $SCRIPTDIR/aida-a-tuning-sample1.out >& /dev/null
rm $SCRIPTDIR/aida-a-tuning-sample1.out/* >& /dev/null
mkdir $SCRIPTDIR/aida-ee-sample1.out >& /dev/null
rm $SCRIPTDIR/aida-ee-sample1.out/* >& /dev/null
mkdir $SCRIPTDIR/en-tweets-training-sample1.out >& /dev/null
rm $SCRIPTDIR/en-tweets-training-sample1.out/* >& /dev/null

outDir=$LOGDIR
outUrl=file://${outDir}

evalId=runUnitTests-aida-a-tuning-${ts}
$ROOTDIR/../yodie-tools/bin/runPipeline.sh -DmaxRecall.evalId=$evalId -Dmodularpipelines.prrun.lookupinfo.Java:copyListAnns=true -Dmodularpipelines.prparm.compareAndEvaluate.Evaluate.evaluationId=$evalId -Dmodularpipelines.prparm.compareAndEvaluate.Evaluate.outputDirectoryUrl=$outUrl -c $SCRIPTDIR/aida-a-tuning-sample1.config.yaml -nl -d -P $SCRIPTDIR/compareAndEvaluate.xgapp $ROOTDIR/main/main.xgapp $SCRIPTDIR/aida-a-tuning-sample1 $SCRIPTDIR/aida-a-tuning-sample1.out |& tee -a $log
grep -q "=== UNIT TEST:" $log | grep -v -q "=== UNIT TEST: DIFFERENCE" 
ret=$?
totalret=$ret

evalId=runUnitTests-aida-ee-${ts}
$ROOTDIR/../yodie-tools/bin/runPipeline.sh -DmaxRecall.evalId=$evalId -Dmodularpipelines.prrun.lookupinfo.Java:copyListAnns=true -Dmodularpipelines.prparm.compareAndEvaluate.Evaluate.evaluationId=$evalId -Dmodularpipelines.prparm.compareAndEvaluate.Evaluate.outputDirectoryUrl=$outUrl -c $SCRIPTDIR/aida-ee-sample1.config.yaml -nl -d -P $SCRIPTDIR/compareAndEvaluate.xgapp $ROOTDIR/main/main.xgapp $SCRIPTDIR/aida-ee-sample1 $SCRIPTDIR/aida-ee-sample1.out |& tee -a $log
grep -q "=== UNIT TEST:" $log | grep -v -q "=== UNIT TEST: DIFFERENCE"
ret=$?
totalret=$((totalret + ret))

evalId=runUnitTests-en-tweets-training-sample1-${ts}
$ROOTDIR/../yodie-tools/bin/runPipeline.sh -DmaxRecall.evalId=$evalId -Dmodularpipelines.prrun.lookupinfo.Java:copyListAnns=true -Dmodularpipelines.prparm.compareAndEvaluate.Evaluate.evaluationId=$evalId -Dmodularpipelines.prparm.compareAndEvaluate.Evaluate.outputDirectoryUrl=$outUrl -c $SCRIPTDIR/en-tweets-training-sample1.config.yaml -nl -d -P $SCRIPTDIR/compareAndEvaluate.xgapp $ROOTDIR/main/main.xgapp $SCRIPTDIR/en-tweets-training-sample1 $SCRIPTDIR/en-tweets-training-sample1.out |& tee -a $log

grep "=== UNIT TEST:" $log | grep -q "=== UNIT TEST: DIFFERENCE"
ret=$?

function summary() {
  ts=$1 
  touch $sum
  for ds in aida-a-tuning aida-ee en-tweets-training-sample1
  do 
    grep $ts $log | grep $ds | grep 'th=none' | grep 'F1.0' | sed -e "s/.\+\.log://" -e "s/, type=Mention, th=none,//" | tee -a $sum
    grep $ts $log | grep $ds | grep 'MaxRecall F1.0'  | tee -a $sum
    grep $ts $log | grep $ds | grep 'th=none' | grep 'Precision' | sed -e "s/.\+\.log://" -e "s/, type=Mention, th=none,//" | tee -a $sum
    grep $ts $log | grep $ds | grep 'MaxRecall Precision' | tee -a $sum
    grep $ts $log | grep $ds | grep 'th=none' | grep 'Recall' | sed -e "s/.\+\.log://" -e "s/, type=Mention, th=none,//" | tee -a $sum 
    grep $ts $log | grep $ds | grep 'MaxRecall Recall' | tee -a $sum
  done
}

if [ $ret == 1 ]
then
  echo 'UNIT TEST DIFFERENCES!' Log is in $LOGDIR/runUnitTest-$ts.log, data files are in $LOGDIR/EvaluateTagging-runUnitTests-*-$ts.tsv
  grep "=== UNIT TEST: DIFFERENCE" $log
  summary $ts
  echo 'UNIT TEST DIFFERENCES!' Log is in $LOGDIR/runUnitTest-$ts.log
  exit 1
else 
  summary $ts
  echo 'UNIT TEST COMPLETED WITHOUT DIFFERENCES! Log is in' $LOGDIR/runUnitTest-$ts.log, data files are in $LOGDIR/EvaluateTagging-runUnitTests-*-$ts.tsv
  rm $log
  exit 0
fi


