#!/usr/bin/env bash
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"

docker build --no-cache -t \
  --file "$SCRIPT_DIR/Dockerfile" \
  akmetiuk/akmetiuk.com:$(date +%F) .
docker login
docker push akmetiuk/akmetiuk.com:$(date +%F)
