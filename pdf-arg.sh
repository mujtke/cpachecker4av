#!/bin/bash

# searching directory.
SearchDir="./output"
# convert file extension.
ConvertExt="dot"
# target file extension.
TargetExt="pdf"
# exclude file list.
ExcludeFileList="ARGSimplified Counterexample ARGRefinements"

# change current directory to the search dir
cd $SearchDir

for f in `find . -name "*.$ConvertExt"`
do
	# get the file name without extension.
	fname=${f%.*}

	# check whether this file is in the exclude file list.
	Matched="false"
	for ef in $ExcludeFileList
	do
		# exclude this file if matched.
		if [[ `echo $fname | grep -i $ef` != "" ]]; then
			Matched="true"
			break;
		fi
	done

	if [[ $Matched == "true" ]]; then
		continue
	fi

	# delete the old version.
	if [ -f "$fname.$TargetExt" ]; then
		echo "delete old version: $fname.$TargetExt"
		rm -f "$fname.$TargetExt"
	fi

	# generate a new version.
	echo "generating new version: $fname.$TargetExt"
	dot -Tpdf "$fname.$ConvertExt" -o "$fname.$TargetExt"
done

