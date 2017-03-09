#!/usr/bin/env bash

shopt -s extglob  # http://unix.stackexchange.com/a/297867
rm -r -- !(_site|deploy.sh)
mv _site/* .
