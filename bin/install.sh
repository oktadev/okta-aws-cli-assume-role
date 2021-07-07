#!/usr/bin/env bash
#
# Copyright 2019 Okta
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
repo_url="https://github.com/oktadeveloper/okta-aws-cli-assume-role"
dotokta="${HOME}/.okta"

printusage() {
    cat <<EOF >&2
usage: $(basename $0) [-h | -i]
       install Okta AWS CLI Assume Role tool
EOF
}

printhelp() {
    cat <<EOF | sed "s#$HOME#~#g"
Installation script for Okta AWS CLI Assume Role
================================================

To execute:

    $(basename $0) -i

This command

1. Installs files into a filesystem location that can be configured
   with the PREFIX environment variable (default: ${dotokta}) and
2. Prints instructions for setting up shell functions and scripts.

This script checks for (and installs if necessary) the file
~/.okta/config.properties regardless of the value of PREFIX.

For details, see ${repo_url}.
EOF
}

while getopts ":ih" opt; do
    case ${opt} in
        h)
            printhelp
            exit
            ;;
        i)
            install=1
            ;;
        \?)
            printusage
            exit 64
            ;;
    esac
done
shift $((OPTIND -1))
if [[ -z "$install" || "$#" -gt 0 ]]; then
    printusage
    exit 64
fi

if ! java -version &>/dev/null; then
    echo "Warning: Java is not installed. Make sure to install that" >&2
fi
if ! aws --version &>/dev/null; then
    echo "Warning: AWS CLI is not installed. Make sure to install that" >&2
fi

PREFIX="${PREFIX:=$dotokta}"
mkdir -p "${PREFIX}"
PREFIX="$(cd -P -- "${PREFIX}" && pwd)"
echo "Installing into ${PREFIX}" | sed "s#$HOME#~#g"

mkdir -p ${PREFIX}
releaseUrl=$(curl -sLI ${repo_url}/releases/latest | grep -e "location:.*tag" | cut -c11-)
releaseTag=$(echo $releaseUrl | awk 'BEGIN{FS="/"}{print $8}' | tr -d '\r')
url=${repo_url}/releases/download/${releaseTag}/okta-aws-cli-${releaseTag:1}.jar
dest=${PREFIX}/$(basename ${url})
echo "Latest release JAR file: ${url}"
echo "Fetching JAR file → ${dest}" | sed "s#$HOME#~#g"
curl -Ls -o "${dest}" "${url}"

jarpath="${PREFIX}/okta-aws-cli.jar"
echo "Symlinking ${jarpath} → $(basename ${dest})" | sed "s#$HOME#~#g"
ln -sf $(basename ${dest}) "${jarpath}"

# bash functions
bash_functions="${PREFIX}/bash_functions"
if ! grep '^#OktaAWSCLI' "${bash_functions}" &>/dev/null; then
    cat <<'EOF' >>"${bash_functions}"
#OktaAWSCLI
function okta-aws {
    withokta "aws --profile $1" "$@"
}
function okta-sls {
    withokta "sls --stage $1" "$@"
}
EOF
fi

# Create fish shell functions
fishFunctionsDir="${PREFIX}/fish_functions"
mkdir -p "${fishFunctionsDir}"
cat <<'EOF' >"${fishFunctionsDir}/okta-aws.fish"
function okta-aws
    withokta "aws --profile $argv[1]" $argv
end
EOF
cat <<'EOF' >"${fishFunctionsDir}/okta-sls.fish"
function okta-sls
    withokta "sls --stage $argv[1]" $argv
end
EOF

# Suppress "Your profile name includes a 'profile ' prefix" warnings
# from AWS Java SDK (Resolves #233)
loggingProperties="${PREFIX}/logging.properties"
cat <<EOF >"${loggingProperties}"
com.amazonaws.auth.profile.internal.BasicProfileConfigLoader = NONE
EOF

mkdir -p "${PREFIX}/bin"

# Create withokta command
cat <<EOF >"${PREFIX}/bin/withokta"
#!/bin/bash
if [ -n "\$https_proxy" ]; then
    readonly URI_REGEX='^(([^:/?#]+):)?(//((([^:/?#]+)@)?([^:/?#]+)(:([0-9]+))?))?(/([^?#]*))(\?([^#]*))?(#(.*))?'
    [[ \$https_proxy =~ \${URI_REGEX} ]] && PROXY_CONFIG="-Dhttps.proxyHost=\${BASH_REMATCH[7]} -Dhttps.proxyPort=\${BASH_REMATCH[9]}"
fi
java \${PROXY_CONFIG} \\
    -Djava.util.logging.config.file=${PREFIX}/logging.properties \\
    -classpath ${PREFIX}/okta-aws-cli.jar \\
    com.okta.tools.WithOkta \$@
EOF
chmod +x "${PREFIX}/bin/withokta"

# Create okta-credential_process command
cat <<EOF >"${PREFIX}/bin/okta-credential_process"
#!/bin/bash
roleARN="\$1"
shift;
if [ -n "\$https_proxy" ]; then
    readonly URI_REGEX='^(([^:/?#]+):)?(//((([^:/?#]+)@)?([^:/?#]+)(:([0-9]+))?))?(/([^?#]*))(\?([^#]*))?(#(.*))?'
    [[ \$https_proxy =~ \${URI_REGEX} ]] && PROXY_CONFIG="-Dhttps.proxyHost=\${BASH_REMATCH[7]} -Dhttps.proxyPort=\${BASH_REMATCH[9]}"
fi
env OKTA_AWS_ROLE_TO_ASSUME="\$roleARN" \
    java \${PROXY_CONFIG} \
      -Djava.util.logging.config.file=${PREFIX}/logging.properties \
      -classpath ${PREFIX}/okta-aws-cli.jar \
      com.okta.tools.CredentialProcess
EOF
chmod +x "${PREFIX}/bin/okta-credential_process"

# Create okta-listroles command
cat <<EOF >"${PREFIX}/bin/okta-listroles"
#!/bin/bash
if [ -n "\$https_proxy" ]; then
    readonly URI_REGEX='^(([^:/?#]+):)?(//((([^:/?#]+)@)?([^:/?#]+)(:([0-9]+))?))?(/([^?#]*))(\?([^#]*))?(#(.*))?'
    [[ \$https_proxy =~ \${URI_REGEX} ]] && PROXY_CONFIG="-Dhttps.proxyHost=\${BASH_REMATCH[7]} -Dhttps.proxyPort=\${BASH_REMATCH[9]}"
fi
java \${PROXY_CONFIG} \
  -Djava.util.logging.config.file=${PREFIX}/logging.properties \
  -classpath ${PREFIX}/okta-aws-cli.jar \
  com.okta.tools.ListRoles
EOF
chmod +x "${PREFIX}/bin/okta-listroles"

# awscli
cat <<'EOF' >"${PREFIX}/bin/awscli"
#!/bin/bash
withokta aws default "$@"
EOF
chmod +x "${PREFIX}/bin/awscli"

# Configure Okta AWS CLI
mkdir -p ${HOME}/.okta                       # `config.properties` must
oktaConfig="${HOME}/.okta/config.properties" # reside in ~/.okta.
if [[ -e "${oktaConfig}" ]]; then
    echo "Found $(echo ${oktaConfig} | sed "s#$HOME#~#g")"
else
    echo "Creating example $(echo ${oktaConfig} | sed "s#$HOME#~#g")"
    cat <<EOF >"${oktaConfig}"
#OktaAWSCLI
OKTA_ORG=acmecorp.okta.com.changeme.local
OKTA_AWS_APP_URL=https://acmecorp.oktapreview.com.changeme.local/home/amazon_aws/0oa5zrwfs815KJmVF0h7/137
OKTA_USERNAME=\$env:USERNAME
OKTA_BROWSER_AUTH=true
EOF
fi

# Print advice for ~/.bash_profile
shellstmt=$(cat <<EOF | sed "s#$HOME#\$HOME#g"
#OktaAWSCLI
if [[ -f "${bash_functions}" ]]; then
    . "${bash_functions}"
fi
if [[ -d "${PREFIX}/bin" && ":\$PATH:" != *":${PREFIX}/bin:"* ]]; then
    PATH="${PREFIX}/bin:\$PATH"
fi
EOF
)
echo
echo "Add the following to ~/.bash_profile or ~/.profile:"
echo
echo "$shellstmt"
