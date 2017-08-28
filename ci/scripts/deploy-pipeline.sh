#!/bin/bash
set -e
#set -x

if ! which fly > /dev/null; then
    echo "Fly CLI is not available."
    exit 1
fi

# Readlink for Nix/MacOS
OS=$(uname)
if [[ "$OS" == "Darwin" ]]; then
  READLINK="greadlink"
else
  READLINK="readlink"
fi

# Set relative script variables
SCRIPT_DIR="$(cd "$(dirname $($READLINK -f ${BASH_SOURCE[0]}))" && pwd)"
CI_DIR="$(cd ${SCRIPT_DIR}/../ && pwd)"
REPO_DIR="$(cd ${CI_DIR}/../ && pwd)"
DEFAULTS_FILE="$REPO_DIR/.pipeline-defaults"

# Check for existence of defaults file and query user
if [[ -f $DEFAULTS_FILE ]]; then
  printf "\nExisting Defaults File Found:\n\n"
  cat $DEFAULTS_FILE
  echo
  while true; do
    read -p "Use existing defaults? [Y/N/Q] " yn
    case $yn in
      [Yy]* ) source $DEFAULTS_FILE; break;;
      [Nn]* ) break;;
      [Qq]* ) exit;;
      * ) echo "Yes, No or Quit";;
    esac
  done
fi
echo

# Set the Concoruse URL
CONCOURSE_URL_DEFAULT=https://concourse.corp.code42.com
if [[ -z $CONCOURSE_URL ]]; then
  read -p "Concourse URL: [$CONCOURSE_URL_DEFAULT] " CONCOURSE_URL
  [ -z $CONCOURSE_URL ] && CONCOURSE_URL=$CONCOURSE_URL_DEFAULT
fi

# Set the Concourse Team name
CONCOURSE_TEAM_DEFAULT=myteam
if [[ -z $CONCOURSE_TEAM ]]; then
  read -p "Concourse team: [$CONCOURSE_TEAM_DEFAULT] " CONCOURSE_TEAM
  [ -z $CONCOURSE_TEAM ] && CONCOURSE_TEAM=$CONCOURSE_TEAM_DEFAULT
fi

# Set the target
CONCOURSE_TARGET_DEFAULT=$CONCOURSE_TEAM
if [[ -z $CONCOURSE_TARGET ]]; then
  read -p "Concourse target: [$CONCOURSE_TARGET_DEFAULT] " CONCOURSE_TARGET
  [ -z $CONCOURSE_TARGET ] && CONCOURSE_TARGET=$CONCOURSE_TARGET_DEFAULT
fi

# Set the default Concourse Username
if [[ -z $CONCOURSE_USERNAME ]]; then
  read -p "Concourse username: " CONCOURSE_USERNAME
fi

# Get the Concourse Password
if [[ -z $CONCOURSE_PASSWORD ]]; then
  read -sp "Concourse password: " CONCOURSE_PASSWORD
  echo
fi

# Set the default Bitbucket host
BITBUCKET_HOST_DEFAULT=stash.corp.code42.com
if [[ -z $BITBUCKET_HOST ]]; then
  read -p "Bitbucket host: [$BITBUCKET_HOST_DEFAULT] " BITBUCKET_HOST
  [ -z $BITBUCKET_HOST ] && BITBUCKET_HOST=$BITBUCKET_HOST_DEFAULT
fi

# Set the default docker registry host
DOCKER_REG_HOST_DEFAULT=artifactory.corp.code42.com
if [[ -z $DOCKER_REG_HOST ]]; then
  read -p "Artifactory host: [$DOCKER_REG_HOST_DEFAULT] " DOCKER_REG_HOST
  [ -z $DOCKER_REG_HOST ] && DOCKER_REG_HOST=$DOCKER_REG_HOST_DEFAULT
fi

# Slugify the Stash repo name
GIT_STASH_REPOSITORY_SLUG=$(git config --get remote.origin.url | sed -e 's/^.*7999\///')
if [[ -z $GIT_STASH_REPOSITORY_SLUG ]]; then
  echo "You do not appear to be in a git repository cloned from Bitbucket. Exiting."
  exit 1
fi

# Retrieve Stash project and repo short names
GIT_STASH_REPOSITORY_PROJECT=$(echo $GIT_STASH_REPOSITORY_SLUG | sed -e 's/\/.*$//')
GIT_STASH_REPOSITORY_NAME=$(echo $GIT_STASH_REPOSITORY_SLUG | sed -e 's/^.*\/\(.*\)\.git/\1/')
GIT_BRANCH_DEFAULT=$(git rev-parse --abbrev-ref HEAD)

# Retrieve the git branch
if [[ -z $GIT_BRANCH ]]; then
  read -p "Git branch: [$GIT_BRANCH_DEFAULT] " GIT_BRANCH
  [ -z $GIT_BRANCH ] && GIT_BRANCH=$GIT_BRANCH_DEFAULT
fi

# Slugify the Git branch
if [[ "$OS" == "Darwin" ]]; then
    GIT_BRANCH_SLUG="$(echo ${GIT_BRANCH} | sed -E s/[~\^]+//g | sed -E s/[^a-zA-Z0-9]+/-/g | sed -E s/^-+\|-+$//g | tr A-Z a-z)"
elif [[ "$OS" == "Linux" ]]; then
    GIT_BRANCH_SLUG="$(echo ${GIT_BRANCH} | sed -r s/[~\^]+//g | sed -r s/[^a-zA-Z0-9]+/-/g | sed -r s/^-+\|-+$//g | tr A-Z a-z)"
else
    echo "OS '$OS' not supported!" && exit 1
fi

# Set the default pipeline name
PIPELINE_NAME_DEFAULT="${GIT_STASH_REPOSITORY_NAME}$([[ $GIT_BRANCH == "master" ]] || echo -$GIT_BRANCH_SLUG)"
if [[ -z $PIPELINE_NAME ]]; then
  read -p "Pipeline name: [$PIPELINE_NAME_DEFAULT] " PIPELINE_NAME
  [ -z $PIPELINE_NAME ] && PIPELINE_NAME=$PIPELINE_NAME_DEFAULT
fi

# Set the container namespace
CONTAINER_NAMESPACE_DEFAULT=c42
if [[ -z $CONTAINER_NAMESPACE ]]; then
  read -p "Container namespace: [$CONTAINER_NAMESPACE_DEFAULT] " CONTAINER_NAMESPACE
  [ -z $CONTAINER_NAMESPACE ] && CONTAINER_NAMESPACE=$CONTAINER_NAMESPACE_DEFAULT
fi

# Set the default container name
if [[ -z $CONTAINER_NAME ]]; then
  read -p "Container name: [$PIPELINE_NAME] " CONTAINER_NAME
  [ -z $CONTAINER_NAME ] && CONTAINER_NAME=$PIPELINE_NAME
fi

# WRite defaults file
echo "Writing defaults file"
cat <<EOF > $DEFAULTS_FILE
PIPELINE_NAME="${PIPELINE_NAME}"
GIT_BRANCH="${GIT_BRANCH}"
CONTAINER_NAME="${CONTAINER_NAME}"
CONTAINER_NAMESPACE="${CONTAINER_NAMESPACE}"
CONCOURSE_TARGET="${CONCOURSE_TARGET}"
CONCOURSE_URL="${CONCOURSE_URL}"
CONCOURSE_TEAM="${CONCOURSE_TEAM}"
CONCOURSE_USERNAME="${CONCOURSE_USERNAME}"
CONCOURSE_PASSWORD="${CONCOURSE_PASSWORD}"
BITBUCKET_HOST="${BITBUCKET_HOST}"
DOCKER_REG_HOST="${DOCKER_REG_HOST}"
EOF

# Default the username and password for Stash and Artifactory to Vault team-based token
CODE42_USERNAME="((${CONCOURSE_TEAM}-username))"
CODE42_PASSWORD="((${CONCOURSE_TEAM}-password))"

# Set pre-tag based on branch name
if [[ "$GIT_BRANCH" = "master" ]]; then
  TAG_PREFIX=v
else
  TAG_PREFIX="${GIT_BRANCH_SLUG}_v"
fi

echo "Logging in to Concourse: $CONCOURSE_URL"
fly --target $CONCOURSE_TARGET login \
    --team-name $CONCOURSE_TEAM \
    --concourse-url $CONCOURSE_URL \
    -u $CONCOURSE_USERNAME \
    -p $CONCOURSE_PASSWORD

echo "Syncing fly binary"
fly --target $CONCOURSE_TARGET sync

echo "Setting pipeline: $PIPELINE_NAME"
fly -t $CONCOURSE_TARGET sp \
  -p $PIPELINE_NAME \
  -c $CI_DIR/pipelines/pipeline.yml \
  -v pipeline-name=$PIPELINE_NAME \
  -v git-branch=$GIT_BRANCH \
  -v git-branch-slug=$GIT_BRANCH_SLUG \
  -v tag-prefix=$TAG_PREFIX \
  -v container-name=$CONTAINER_NAME \
  -v container-namespace=$CONTAINER_NAMESPACE \
  -v concourse-url=$CONCOURSE_URL \
  -v concourse-team=$CONCOURSE_TEAM \
  -v concourse-username=$CONCOURSE_USERNAME \
  -v concourse-password=$CONCOURSE_PASSWORD \
  -v bitbucket-host=$BITBUCKET_HOST \
  -v code42-username=$CODE42_USERNAME \
  -v code42-password=$CODE42_PASSWORD \
  -v docker-host=$DOCKER_REG_HOST \
  -v git-stash-repository-slug=$GIT_STASH_REPOSITORY_SLUG \
  -v git-stash-repository-project=$GIT_STASH_REPOSITORY_PROJECT \
  -v git-stash-repository-name=$GIT_STASH_REPOSITORY_NAME

if [[ ! -z $(fly --target $CONCOURSE_TARGET pipelines | grep "$PIPELINE_NAME") ]]; then
  echo "Exposing Pipeline: $PIPELINE_NAME"
  fly --target $CONCOURSE_TARGET expose-pipeline --pipeline $PIPELINE_NAME
fi


