#!/bin/bash 

if [ "$#" -ne 3 ]; then
  echo "Usage: run_overlays.sh <analysis results folder> <starting color> <subfolders [0/1]>"
  exit 1;
fi

python /home/QuIPutils/python/quip_overlays.py --quip /data/results/$1 --images /data/images --overlays /data/overlays --color $2 --subfolders $3

exit 0;
