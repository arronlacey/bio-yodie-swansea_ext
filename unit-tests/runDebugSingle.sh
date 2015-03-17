#!/bin/bash

rm d1.afterMain1/*
rm d1.afterTransfer/*
rm d1.afterMain2/*
rm d1.afterEval1/*

runPipeline.sh -nl -c aida-a-tuning-sample1.config.yaml ../main/main.xgapp debug1 d1.afterMain1/

runPipeline.sh -nl -c aida-a-tuning-sample1.config.yaml transferShef2Ref.xgapp  d1.afterMain1/ d1.afterTransfer/

runPipeline.sh -nl -c aida-a-tuning-sample1.config.yaml ../main/main.xgapp   d1.afterTransfer/ d1.afterMain2/

runPipeline.sh -nl -c aida-a-tuning-sample1.config.yaml compareAndEvaluate.xgapp   d1.afterMain2/ d1.afterEval1/
