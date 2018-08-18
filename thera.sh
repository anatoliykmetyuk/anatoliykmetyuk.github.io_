IMAGE_NAME="thera-akmetiuk"

function start_thera {
  if [ -a $IMAGE_OVERRIDE ]; then
    if ! [ -z ${IMAGE_NAME+x} ]; then  # https://stackoverflow.com/a/13864829
      echo "Dockerfile override active. Building image with name $IMAGE_NAME"
      docker build -t $IMAGE_NAME .
    else
      echo "Dockerfile detected, but IMAGE_NAME variable is not set.
Using standard image, thera:latest. Please set IMAGE_NAME environment variable in thera.sh file
to name your image and use it instead of the standard one."
      IMAGE_NAME=$DEFAULT_IMAGE_NAME
    fi
  else
    IMAGE_NAME=$DEFAULT_IMAGE_NAME
  fi

  echo "Starting $IMAGE_NAME"
  docker run -td \
    -v $SELF_DIR/_volumes/home:/root \
    -v $SELF_DIR:/root/thera \
    -v /Users/anatolii/.ivy2:/root/.ivy2 \
    -p 8888:8888 \
    --name thera \
    --rm \
    --workdir /root/thera \
    $IMAGE_NAME
}