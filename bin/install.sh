#!/usr/bin/env bash
#
# Copyright 2018 Okta
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
PREFIX=${PREFIX:=/usr/local}

dotokta=${HOME}/.okta
repo_url="https://github.com/oktadeveloper/okta-aws-cli-assume-role"

if ! java -version &>/dev/null; then
    echo "Warning: Java is not installed. Make sure to install that" >&2
fi
if ! aws --version &>/dev/null; then
    echo "Warning: AWS CLI is not installed. Make sure to install that" >&2
fi

mkdir -p ${dotokta}
releaseUrl=$(curl --head --silent ${repo_url}/releases/latest | grep "Location:" | cut -c11-)
releaseTag=$(echo $releaseUrl | awk 'BEGIN{FS="/"}{print $8}' | tr -d '\r')
curl -L "${repo_url}/releases/download/${releaseTag}/okta-aws-cli-${releaseTag:1}.jar" \
     --output "${dotokta}/okta-aws-cli.jar"

# bash functions
bash_functions="${dotokta}/bash_functions"
if ! grep '^#OktaAWSCLI' "${bash_functions}" &>/dev/null; then
    cat <<'EOF' >>"${bash_functions}"
#OktaAWSCLI
function okta-aws {
    withokta "aws --profile $1" $@
}
function okta-sls {
    withokta "sls --stage $1" $@
}
EOF
fi

# Print advice for ~/.bash_profile
echo "Add the following to ~/.bash_profile or ~/.profile:"
echo
cat <<EOF
#OktaAWSCLI
if [ -f "${bash_functions}" ]; then
    . "${bash_functions}"
fi
EOF

# Create fish shell functions
fishFunctionsDir="${dotokta}/fish_functions"
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
loggingProperties="${dotokta}/logging.properties"
cat <<EOF >"${loggingProperties}"
com.amazonaws.auth.profile.internal.BasicProfileConfigLoader = NONE
EOF

# Create withokta command
cat <<'EOF' >"${PREFIX}/bin/withokta"
#!/bin/bash
command="$1"
profile=$2
shift;
shift;
env OKTA_PROFILE=$profile java \
    -Djava.util.logging.config.file=~/.okta/logging.properties \
    -classpath ~/.okta/okta-aws-cli.jar \
    com.okta.tools.WithOkta $command $@
EOF
chmod +x "${PREFIX}/bin/withokta"

# Create okta-credential_process command
cat <<'EOF' >"${PREFIX}/bin/okta-credential_process"
#!/bin/bash
roleARN="$1"
shift;
env OKTA_AWS_ROLE_TO_ASSUME="$roleARN" \
    java -classpath ~/.okta/okta-aws-cli.jar com.okta.tools.CredentialProcess
EOF
chmod +x "${PREFIX}/bin/okta-credential_process"

# Create okta-listroles command
cat <<EOF >"${PREFIX}/bin/okta-listroles"
#!/bin/bash
java -classpath ~/.okta/okta-aws-cli.jar com.okta.tools.ListRoles
EOF
chmod +x "${PREFIX}/bin/okta-listroles"

# Configure Okta AWS CLI
oktaConfig="${dotokta}/config.properties"
if ! grep '^#OktaAWSCLI' "${oktaConfig}" &>/dev/null; then
    cat <<EOF >"${oktaConfig}"
#OktaAWSCLI
OKTA_ORG=acmecorp.okta.com.changeme.local
OKTA_AWS_APP_URL=https://acmecorp.oktapreview.com.changeme.local/home/amazon_aws/0oa5zrwfs815KJmVF0h7/137
OKTA_USERNAME=$env:USERNAME
OKTA_BROWSER_AUTH=true
EOF
fi
