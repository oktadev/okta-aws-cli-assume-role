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

java -version > /dev/null 2>&1
if [ $? -ne 0 ];
then
    echo 'Warning: Java is not installed. Make sure to install that'
fi
aws --version > /dev/null 2>&1
if [ $? -ne 0 ];
then
    echo 'Warning: AWS CLI is not installed. Make sure to install that'
fi

mkdir -p ${HOME}/.okta
releaseUrl=$(curl --head --silent https://github.com/oktadeveloper/okta-aws-cli-assume-role/releases/latest | grep 'Location:' | cut -c11-)
releaseTag=$(echo $releaseUrl | awk 'BEGIN{FS="/"}{print $8}' | tr -d '\r')
curl -L "https://github.com/oktadeveloper/okta-aws-cli-assume-role/releases/download/${releaseTag}/okta-aws-cli-${releaseTag:1}.jar" --output "${HOME}/.okta/okta-aws-cli.jar"

# bash functions
bash_functions="${HOME}/.okta/bash_functions"
grep '^#OktaAWSCLI' "${bash_functions}" > /dev/null 2>&1
if [ $? -ne 0 ]
then
echo '
function okta-aws {
    withokta "aws --profile $1" $@
}
function okta-sls {
    withokta "sls --stage $1" $@
}
' >> "${bash_functions}"
fi

# Conditionally update fish profile
fishConfig="${HOME}/.config/fish/config.fish"
mkdir -p $(dirname "${fishConfig}")
touch "${fishConfig}"
grep '^#OktaAWSCLI' "${fishConfig}" > /dev/null 2>&1
if [ $? -ne 0 ]
then
echo '
#OktaAWSCLI
export PATH="$HOME/bin:$PATH"
function withokta
    java -Djava.net.useSystemProxies com.okta.tools.WithOkta $argv
end
function okta-aws
    withokta "aws --profile $argv[1]" $argv
end
function okta-sls
    withokta "sls --stage $argv[1]" $argv
end
' >> "${fishConfig}"
fi

# Conditionally update bash profile
bashProfile="${HOME}/.bash_profile"
grep '^#OktaAWSCLI' "${bashProfile}" > /dev/null 2>&1
if [ $? -ne 0 ]
then
echo "
#OktaAWSCLI
if [ -f \"${bash_functions}\" ]; then
    . \"${bash_functions}\"
fi
" >> "${bashProfile}"
fi

# Create withokta command
echo '#!/bin/bash
command="$1"
profile=$2
shift;
shift;
env OKTA_PROFILE=$profile java -classpath ~/.okta/okta-aws-cli.jar com.okta.tools.WithOkta $command $@
' > "$PREFIX/bin/withokta"
chmod +x "$PREFIX/bin/withokta"

# Configure Okta AWS CLI
oktaConfig="${HOME}/.okta/config.properties"
grep '^#OktaAWSCLI' "${oktaConfig}" > /dev/null 2>&1
if [ $? -ne 0 ]
then
echo "
#OktaAWSCLI
OKTA_ORG=acmecorp.okta.com.changeme.local
OKTA_AWS_APP_URL=https://acmecorp.oktapreview.com.changeme.local/home/amazon_aws/0oa5zrwfs815KJmVF0h7/137
OKTA_USERNAME=$env:USERNAME
OKTA_BROWSER_AUTH=true
" > "${oktaConfig}"
fi
