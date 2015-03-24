#!/bin/bash

# sync data from the server where it was prepared.
# There is a default location on a default server, but it is possible
# to override both by specifying as arguments
#   [fullpath [server [username]]]
# To be able to do that a ssh login should be possible, preferably by
# key, on the target server with the same userid as the current userid 
# or the userid given as the third argument

path=/home/johann/yodie/yodie-preparation/output
server=gateservice8.dcs.shef.ac.uk
user=`whoami`

PRG="$0"
CURDIR="`pwd`"
## find the location of the script
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

echo SCRIPTDIR=$SCRIPTDIR
echo ROOTDIR=$ROOTDIR
dest="$ROOTDIR"/resources

mkdir $dest 2>/dev/null
if [[ ! -d $dest ]]
then
  echo Directory $dest could not be created
  exit 1
fi

## process any command line parameters
while test "$1" != "";
do
  case "$1" in
  -h)
    echo "usage: download-resources.sh [-u userid] [-s server] [-p path]"
    echo default user is $user
    echo default server is $server
    echo default path is $path
    echo destination is $dest
    exit 0
    ;;
  -u)
    shift
    user=$1
    if [[ "${user}" == "" ]] ; then echo "no user id specified"; exit 1; fi
    ;;
  -s)
    shift
    server=$1
    if [[ "${server}" == "" ]] ; then echo "no server specified"; exit 1; fi
    ;;
  -p)
    shift
    path=$1
    if [[ "${path}" == "" ]] ; then echo "no path specified"; exit 1; fi
    ;;
  *)
    echo "Invalid command line parameter: $1"
    echo use -h for usage
    exit 1
    ;;
  esac
  shift
done

echo user $user
echo server $server
echo path $path

## make sure the path has a trailing slash
case "$path" in
*/)
  ## do not need to do anything!
  ;;
*)
  path="$path"/
  ;;
esac

OPTS='-O -avz --partial --delete --force --progress --cvs-exclude -e ssh'
FROMPREFIX=${user}@${server}:${path}

chmod a+r $dest/databases/*
echo SYNCING everything from $FROMPREFIX to $dest
rsync $OPTS $FROMPREFIX $dest
date=`date +%Y-%m-%d,%H:%M:%S`
echo "Last sync from $FROMPREFIX on $date" > $dest/last-sync.txt
chmod a-r $dest/databases/*
echo "NOTE: all databases have been write-protected!"
