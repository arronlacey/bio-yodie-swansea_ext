#!/bin/bash

if [ x"${GATE_HOME}" == x ]
then
  echo environment variable GATE_HOME not set
  exit 1
fi

branch=`git branch | grep '*' | cut -c3-`
if [ xxx"$branch" != xxxmaster ]
then
  echo You are not on branch 'master' but on branch $branch
  echo Before updating, you should checkout master
  exit 1
fi

## we are on branch master, so lets first fetch any remote updates ...
git fetch --recurse-submodules=on-demand

## try and merge the changes into the branch
git merge origin/master | grep 'Already up-to-date' && exit 0

./plugins/compilePlugins.sh | tee compilePlugins.log | grep 'Build of plugin' 
