#!/usr/bin/env bash
docker build --no-cache -t akmetiuk/akmetiuk.com:$(date +%F) .
docker login
docker push akmetiuk/akmetiuk.com:$(date +%F)
