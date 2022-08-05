#!/bin/bash

count=1

while [ $count -le 5 ]
do
	./scripts/cpa.sh -config config/myAnalysis-concurrent-interrupt-benchmark.properties  ../benchmark-racebench/2.1_remarks/svp_simple_003_001.i
	count=$[$count+1]
	trap "break" SIGINT
done

trap "echo 'exit'" SIGINT
