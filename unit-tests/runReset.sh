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

$ROOTDIR/../yodie-tools/bin/runPipeline.sh -c $SCRIPTDIR/aida-a-tuning-sample1.config.yaml -nl -d -P transferShef2Ref.xgapp ../main/main.xgapp aida-a-tuning-sample1

$ROOTDIR/../yodie-tools/bin/runPipeline.sh -c $SCRIPTDIR/aida-ee-sample1.config.yaml -nl -d -P transferShef2Ref.xgapp ../main/main.xgapp aida-ee-sample1

$ROOTDIR/../yodie-tools/bin/runPipeline.sh -c $SCRIPTDIR/en-tweets-training-sample1.config.yaml -nl -d -P transferShef2Ref.xgapp ../main/main.xgapp en-tweets-training-sample1
