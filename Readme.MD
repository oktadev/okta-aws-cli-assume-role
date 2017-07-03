# Okta AWS-CLI Tool

This readme file is an excerpt of the [Okta-AWS Integration Guide](https://support.okta.com/help/articles/Knowledge_Article/Amazon-Web-Services-and-Okta-Integration-Guide). We highly recommend that you first read it to understand how Okta and AWS integrate with each other.

*Important Note*: This tool has been verified to work on Mac OS X El Capitan and Windows Server 2012 R2 and is expected to work on Linux and Unix as well.

## AWS:reInvent Release (2016/11/29 update)

**The Okta AWS-CLI tool finally supports multi-policy cross-account roles!**

You can now use this tool with the cross-account role architecture recommended in the [Okta-AWS Integration Guide](https://support.okta.com/help/articles/Knowledge_Article/Amazon-Web-Services-and-Okta-Integration-Guide), i.e. you can assign multiple AssumeRole policies to a single cross-account role.

##### Important note

The AWS-CLI tool currently only supports single-statement policies, i.e. policies with one single AssumeRole action (pointing to a role in a separate account). Additionally, the tool only supports managed policies. Support for inline policies is planned in the near future.


The Okta AWS-CLI Tool allows Okta customers to take advantage of [Okta](www.okta.com) to use the [AWS Command Line Interface](https://aws.amazon.com/cli) without relying on permanent AWS keys. More specifically, it allows them to generate temporary 60-minute keys
that are stored for each profile in the `~/.aws/credentials` file. Additionally, if the AWS environment is leveraging cross-account roles and the AssumeRole AWS function, the tool will be able to detect that you choose
to assume a cross-account role and will be able to extract the actual role in the target account as well as add an additional profile line the `~/.aws/config` file.

**Important note**: Please note that this tool only supports Okta MFA set up at the organization level (i.e. not at the sign-on policy level).

## Setup

- Use `git clone https://github.com/oktadeveloper/okta-aws-cli-assume-role.git` to clone the repository locally.
- In Terminal, run `defaults write com.apple.finder AppleShowAllFiles YES` if you want to be able to inspect the `~/.aws/credentials` and `~/.aws/config` files.

## Compiling the application

### Prerequisites

First of all, it goes without saying that you will need to install the [Java 8 JDK](http://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html).

Next, in order to compile the application, you will need to install [Apache Maven](https://maven.apache.org/).

### Compiling the Project

Run `mvn install` at the base level of the project to download the dependencies from Maven Central and to compile the project. This will also create the `awscli.command` and `awscli.bat` scripts in the `bin` directory, which are used to run the program.

## Configuring AWS IAM

Because this tool aims at facilitating your interaction with the AWS CLI, it must be able to look into the role you select to assume in order to figure out whether it's a role with direct permissions on the account, or a cross-account role that allows you to assume a role (with real permissions) in another account. To perform this introspection into the AWS roles, the tool must be granted AIM Read Only Access in AWS so that it can automatically add the proper `role_arn` and `source_profile` entries into the AWS config file.

In order to generate the key and secret to be configured in the `config.properties` file, please follow the steps highlighted in `Appendix A: How to create a IAM user for role introspection` in the [Okta-AWS Integration Guide](https://support.okta.com/help/articles/Knowledge_Article/Amazon-Web-Services-and-Okta-Integration-Guide)

## Configuring the application

Before you run the program, you will need to modify the `bin/config.properties` file. This file must be in the directory that you run the program from--in this case, the `bin` directory. It holds information specific to each Okta organization (or "tenant") and needs to be configured on an org to org basis.

Here is the list of parameters that must be maintained in the `config.properties` file:

- `OKTA_ORG` which is the url of your Okta org (starting with `https://`).
- `OKTA_AWS_APP_URL` is the URL link of your Okta AWS application URL (see below for more info)
- `AWS_IAM_KEY`: paste the Access Key ID value you retrieved in the  previous section
-  `AWS_IAM_SECRET`: paste the Secret Access Key value you retrieved in the previous section

### Obtaining the AWS app url

- Navigate to the `Admin Dashboard` of you Okta organization
- Select the `Applications` tab and click on your AWS Application
- Under the `General` menu, scroll down to find the `App Embed Link` section
- Your link is located under `EMBED LINK`
- Replace the example values in `config.properties` with your values

## Running the application

- Run the script `bin/awscli.command`

## Support

Have a question or see a bug? Email developers@okta.com. For feature requests, feel free to open an issue on this repo. If you find a security vulnerability, please follow our [Vulnerability Reporting Process](https://www.okta.com/vulnerability-reporting-policy/).

## License

Copyright 2017 Okta, Inc. All rights reserved.

Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0.

Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
