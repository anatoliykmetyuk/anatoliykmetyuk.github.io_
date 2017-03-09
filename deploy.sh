#!/usr/bin/env bash

rm -r -- !(_site|deploy.sh)
mv _site/* .
