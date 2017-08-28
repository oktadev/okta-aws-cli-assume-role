#!/bin/bash

# Override the image name used for local development 
IMAGE_NAME=okta

docker run -it --rm \
    -v $(pwd):/workspace \
    -v /var/run/docker.sock:/var/run/docker.sock \
    -e DIRENV_ALLOW=$DIRENV_ALLOW \
    -e AWS_ROLE_NAME=$AWS_ROLE_NAME \
    -e AWS_DEFAULT_REGION=$AWS_DEFAULT_REGION \
    -e CUR_DIR_NAME=$(basename $PWD) \
    $IMAGE_NAME $@
