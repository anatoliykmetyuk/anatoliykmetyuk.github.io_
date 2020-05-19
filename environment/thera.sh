#!/usr/bin/env bash

HELP="Usage: thera <command>, where <command> is one of:
  start - Start Thera
  stop  - Stop Thera
  restart - Restart Thera
  build - Run build.sc script on Thera under Ammonite
  help  - Show this message
  any other command - run that command on Thera Docker container (provided the container is running)."

    SELF_DIR=`pwd`
PROJECT_NAME=akmetiuk
  IMAGE_NAME="$PROJECT_NAME:latest"

function start_thera {
  echo "Building image $IMAGE_NAME"
  docker build -t $IMAGE_NAME .

  echo "Starting $IMAGE_NAME"
  docker run -td \
    -v "$SELF_DIR/_volumes/home:/root" \
    -v "$SELF_DIR:/root/$PROJECT_NAME" \
    -p 8888:8888 \
    --name "$PROJECT_NAME" \
    --rm \
    --workdir "/root/$PROJECT_NAME" \
    "$IMAGE_NAME"
}

function stop_thera {
  docker stop "$PROJECT_NAME"
}

function run_on_thera {
  docker exec -ti "$PROJECT_NAME" $@
}

function build_thera {
  run_on_thera amm build.sc
}

function restart_thera {
  stop_thera; start_thera
}

case $1 in
    start) start_thera;;
     stop) stop_thera;;
  restart) restart_thera;;
    build) build_thera;;

  '' | help) echo -e "$HELP";;

  *) run_on_thera $@;;
esac
