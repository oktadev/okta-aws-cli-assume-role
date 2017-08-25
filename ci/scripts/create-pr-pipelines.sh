#!/bin/bash

set -e

concourse_url="$1"
concourse_team="$2"
concourse_username="$3"
concourse_password="$4"
bitbucket_host="$5"
git_stash_repository_slug="$6"
code42_username="$7"
code42_password="$8"
docker_host="$9"
pipeline_name="${10}"
container_namespace="${11}"
container_name="${12}"
pr_metadata_file="${13}"
source_path="${14}"

if ! which fly >> /dev/null; then
    echo "Downloading Fly..."
    curl -sSL -o /tmp/fly "$concourse_url/api/v1/cli?arch=amd64&platform=linux"
    install /tmp/fly /usr/bin/fly
    rm /tmp/fly
fi

slugify() {
    # may have difficulty with non-ANSI characters
    cat | sed -r s/[~\^]+//g | sed -r s/[^a-zA-Z0-9]+/-/g | sed -r s/^-+\|-+$//g | tr A-Z a-z
}

echo "Logging into Concourse..."
fly -t concourse login --team-name "$concourse_team" -u "$concourse_username" -p "$concourse_password" -c "$concourse_url" >> /dev/null

echo "Creating pipeline for pull-requests..."
cat "$pr_metadata_file" | jq -r 'map(.id | tostring) | join("\n")' | while read -r id
do
    branch=$(jq -r "map(select((.id | tostring) == \"$id\")) | .[0].fromRef.displayId" "$pr_metadata_file")
    slug="$pipeline_name-pr-$(jq -r "map(select((.id | tostring) == \"$id\")) | .[0].fromRef.displayId" "$pr_metadata_file" | slugify)"

    echo "Creating pipeline '$slug' for PR# $id..."

    fly --target concourse set-pipeline \
      --non-interactive \
      --pipeline $slug \
      --config $source_path/ci/pipelines/pipeline_pr.yml \
      --var pr-slug=$slug \
      --var git-branch=$branch \
      --var bitbucket-host=$bitbucket_host \
      --var git-stash-repository-slug=$git_stash_repository_slug \
      --var code42-username=$code42_username \
      --var code42-password=$code42_password \
      --var docker-host=$docker_host \
      --var container-namespace=$container_namespace \
      --var container-name=$container_name > /dev/null

    fly --target concourse unpause-pipeline --pipeline $slug
    fly --target concourse expose-pipeline --pipeline $slug
done
