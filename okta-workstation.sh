#!/bin/bash

# Override the image name used for local development 
IMAGE_NAME=okta

docker run -it --rm -v $(pwd):/workspace \
    -v $(pwd)/.aws:/root/.aws \
    -e OKTA_AWS_APP_URL=$OKTA_AWS_APP_URL \
    -e OKTA_AWS_IAM_KEY=$OKTA_AWS_IAM_KEY \
    -e OKTA_AWS_IAM_SECRET=$OKTA_AWS_IAM_SECRET \
    $IMAGE_NAME okta_launch $@

