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

mkdir $SCRIPTDIR/aida-a-tuning-sample1 >& /dev/null
mkdir $SCRIPTDIR/aida-ee-sample1 >& /dev/null
mkdir $SCRIPTDIR/en-tweets-training-sample1 >& /dev/null

rm $SCRIPTDIR/aida-a-tuning-sample1/*
rm $SCRIPTDIR/aida-ee-sample1/*
rm $SCRIPTDIR/en-tweets-training-sample1/*

$ROOTDIR/../yodie-tools/bin/cp-random 20 1 $ROOTDIR/../yodie-corpora/corpora/aida-a-tuning-fix01 $SCRIPTDIR/aida-a-tuning-sample1
$ROOTDIR/../yodie-tools/bin/cp-random 20 1 $ROOTDIR/../yodie-corpora/corpora/aida-ee-fix01 $SCRIPTDIR/aida-ee-sample1
$ROOTDIR/../yodie-tools/bin/cp-random 100 1 $ROOTDIR/../yodie-corpora/corpora/en-tweets-training $SCRIPTDIR/en-tweets-training-sample1

echo Documents copied, now run runReset.sh
