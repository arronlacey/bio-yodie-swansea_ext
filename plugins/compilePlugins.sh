#!/bin/bash
trap "exit" INT
if [ "${GATE_HOME}" == "" ]
then
  echo Environment variable GATE_HOME not set
  exit 1
fi
compile=1
if [ "$1" == "clean" ]
then
  compile=0
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
pushd "$SCRIPTDIR"
tmpout=/tmp/`whoami`-compilePlugins$$.out
for file in *
do
  if [ "$file" == ANNIE ] || [ ! -d "$file" ]
  then
    echo skipping $file
  else 
    pushd "$file"
    if [[ -f build.xml ]]
    then
      ant clean
      if [ "$compile" == 1 ]
      then 
        ant | tee $tmpout
        grep -q "BUILD SUCCESSFUL" $tmpout 
        if [ "$?" != 0 ]
        then
          echo Build failed for $file compilation script aborted
          exit
        fi
      fi
    fi
    popd
  fi
done
popd
