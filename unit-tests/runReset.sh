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

## NOTE: all scripts use the main.config.yaml config now but those parts which need to be
## set to different values are specified via the command line (so far, only twitter
## related settings)

$ROOTDIR/../yodie-tools/bin/runPipeline.sh -c $ROOTDIR/main/main.config.yaml -nl -d -P transferShef2Ref.xgapp $ROOTDIR/main/main.xgapp aida-a-tuning-sample1

$ROOTDIR/../yodie-tools/bin/runPipeline.sh -c $ROOTDIR/main/main.config.yaml -nl -d -P transferShef2Ref.xgapp $ROOTDIR/main/main.xgapp aida-ee-sample1

## NOTE: this overrides the following two settings from the main.config.yaml file:
##- set: docfeature
##  name: docType
##  value: tweet
##- set: prrun
##  controller: filter-prescoring
##  prname: Java:projectTwitterUserID
##  value: true

$ROOTDIR/../yodie-tools/bin/runPipeline.sh -c $ROOTDIR/main/main.config.yaml -Dmodularpipelines.docfeature.docType=tweet -Dmodularpipelines.prrun.filter-prescoring.Java:projectTwitterUserID=true -nl -d -P transferShef2Ref.xgapp $ROOTDIR/main/main.xgapp en-tweets-training-sample1
