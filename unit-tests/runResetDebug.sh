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
mkdir $SCRIPTDIR/debug1
rm $SCRIPTDIR/debug1/*
cp $SCRIPTDIR/debug1.in/* $SCRIPTDIR/debug1
$ROOTDIR/../yodie-tools/bin/runPipeline.sh -c $SCRIPTDIR/aida-a-tuning-sample1.config.yaml -nl -d -P transferShef2Ref.xgapp ../main/main.xgapp debug1

