# Okta AWS-CLI Tool

This readme file is an excerpt of the [Okta-AWS Integration Guide](https://support.okta.com/help/articles/Knowledge_Article/Amazon-Web-Services-and-Okta-Integration-Guide). We highly recommend that you first read it to understand how Okta and AWS integrate with each other.

Important Note: This tool has been verified to work on Mac OS X El Capitan and Windows Server 2012 R2 and is expected to work on Linux and Unix as well.

## Installation

Create a **~/.okta/** directory and put the JAR in there.

Download the latest release JAR and put it in **~/.okta/**:
https://github.com/oktadeveloper/okta-aws-cli-assume-role/releases

Copy **config.properties** to **~/.okta/config.properties** and set
**OKTA_ORG** and **OKTA_AWS_APP_URL** appropriately.

Copy scripts from **bin** to somewhere on your **PATH**. 

Verify your setup with a simple command:

```bash
awscli sts get-caller-identity
```

You should see a username and password prompt and (optionally) an MFA
prompt if you require it.

If you have a single AWS role assigned in the Okta AWS app, the command
`aws sts get-caller-identity` will run with that role at this point.

If you multiple AWS roles assigned in the Okta AWS app, you will be
presented with a role-selection menu and then your command will run.

### Wrap AWS CLI with session resumption (2017/12/5 update)
Stored IAM credentials are no longer used: you no longer need to distribute or rotate these credentials.

Minimal config requires only your orgname and Amazon Web Services Okta app embed link:

```json
OKTA_ORG=acmecorp.oktapreview.com
OKTA_AWS_APP_URL=https://acmecorp.oktapreview.com/home/amazon_aws/0oa5zrwfs815KJmVF0h7/137
```

If the role you are assuming does not have sts:AssumeRole, you will need to attach an inline policy like this to it to
at least allow the role to assume itself:

```json
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Effect": "Allow",
            "Action": [
                "sts:AssumeRole"
            ],
            "Resource": [
                "arn:aws:iam::123456789012:role/ReadOnly"
            ]
        }
    ]
}
```

### Add config.properties parameters to support silent mode without prompt for user input (2017/6/8 update)
- OKTA_USERNAME
- OKTA_PASSWORD
- OKTA_AWS_ROLE_TO_ASSUME

Same parameters can be set as environment variable. config.properties parameter has priority over environment variable.

### AWS:reInvent Release (2016/11/29 update)
#### The Okta AWS-CLI tool finally supports multi-policy cross-account roles!
You can now use this tool with the cross-account role architecture recommended in the [Okta-AWS Integration Guide](https://support.okta.com/help/Documentation/Knowledge_Article/Integrating-the-Amazon-Web-Services-Command-Line-Interface-Using-Okta), i.e. you can assign multiple AssumeRole policies to a single cross-account role.

##### Important note

The AWS-CLI tool currently only supports single-statement policies, i.e. policies with one single AssumeRole action (pointing to a role in a separate account). Additionally, the tool only supports managed policies. Support for inline policies is planned in the near future.


The Okta AWS-CLI Tool allows Okta customers to take advantage of [Okta](www.okta.com) to use the [AWS Command Line Interface](https://aws.amazon.com/cli) without relying on permanent AWS keys. More specifically, it allows them to generate temporary 60-minute keys
that are stored for each profile in the ```~/.aws/credentials``` file. Additionally, if the AWS environment is leveraging cross-account roles and the AssumeRole AWS function, the tool will be able to detect that you choose
to assume a cross-account role and will be able to extract the actual role in the target account as well as add an additional profile line the ```~/.aws/config``` file.

__Important note__: please note that this tool only supports Okta MFA set up at the organization level (i.e. not at the sign-on policy level).

## Setup

- Use ```git clone https://github.com/oktadeveloper/okta-aws-cli-assume-role.git``` to clone the repository locally.

## Build and run

Get a single JAR with all dependencies:

```bash
mvn package
cp target/okta-aws-cli-*.jar out/oktaawscli.jar
```

Run the program:

```bash
./awscli sts get-caller-identity
```

This will prompt for Okta credentials, log you into AWS, let you pick a role, and store a session for you.

Run the program again to see session resumption (you won't be asked for Okta credentials until the session expires):

```bash
./awscli sts get-caller-identity
```

## Compiling the application

The application was built and compiled with [JetBrains' IntelliJ IDEA](https://www.jetbrains.com/idea/). The instructions below explain how to compile the application using IntelliJ IDEA. Note that you don't have to compile the application in order to be able to execute it, since the compiled executable (a jar file) is available on GitHub in the ```out``` sub-folder.

### Prerequisites
First of all, it goes without saying that you will need to install the [Java SE 8x](http://www.oracle.com/technetwork/java/javase/downloads/index.html) or the [Java JDK 8x](http://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html).

Next, in order to compile the application, you will need to download and install the [AWS Java SDK](https://aws.amazon.com/sdk-for-java/) as well as the [AWS Command Line Interface](https://aws.amazon.com/cli). Once you have downloaded the ```AWS Java SDK```, make sure you copy the latest AWS JAR file (currently ```aws-java-sdk-1.10.74.jar```) into the ```lib``` folder of this tool (otherwise, you will not be able to compile or run it).

You will also need the [Okta Java SDK](https://github.com/okta/oktasdk-java) but we provide a compiled version of it within this repository so there's nothing specific you need to do at this point. We used the following path structure to manage our IntelliJ project: ```$HOME/Projects/okta-aws-cli-assume-role``` and we recommend you do the same to avoid fixing too many references. We've also created a ```lib``` sub-folder inside the ```okta-aws-cli-assume-role``` root folder where we're storing the AWS Java SDK (```aws-java-sdk-1.10.74.jar```), the Okta Java SDK (```okta-sdk.jar```) and other dependencies. We strongly recommend that you do the same.

### Opening the project with IntelliJ Idea

- Open the IntelliJ Idea IDE and browse to the ```okta-aws-cli-assume-role``` folder you have cloned from GitHub inside the ```Projects``` folder.
- Go to ```File => Project Structure``` and in the Libraries menu, fix the Java references that don't match your local setup.
- Go to ```Build => Make Project``` in order to compile the project.
- The project also builds the JAR artifact, so if you browse to the ```out``` sub-folder, you will see the ```oktaawscli.jar``` JAR artifact.
- Make sure the ```awscli.command``` file is in the ```out``` sub-folder.

## Configuring AWS IAM

  Because this tool aims at facilitating your interaction with the AWS CLI, it must be able to look into the role you select to assume in order to figure out whether it's a role with direct permissions on the account, or a cross-account role that allows you to assume a role (with real permissions) in another account. To perform this introspection into the AWS roles, the tool must be granted AIM Read Only Access in AWS so that it can automatically add the proper role_arn and source_profile entries into the AWS config file.

In order to generate the key and secret to be configured in the ```config.properties``` file, please follow the steps highlighted in ```Appendix A: How to create a IAM user for role introspection``` in the [Okta-AWS Integration Guide](https://support.okta.com/help/articles/Knowledge_Article/Amazon-Web-Services-and-Okta-Integration-Guide) 

## Configuring the application
  It is essential that ```config.properties``` be in the same directory as the ```oktaawscli.jar``` file (i.e. in the ```out``` directory). The ```config.properties``` config file holds information specific to each Okta organization (or "tenant") and needs to be configured on an org to org basis. Please copy it from the ```src/com/okta/tools``` folder to the ```out``` folder.
  
Here is the list of parameters that must be maintained in the ```config.properties``` file:
  
  - ```OKTA_ORG``` which is the url of your Okta org (starting with https://).
  - ```OKTA_AWS_APP_URL``` is the url link of your Okta AWS application url (see below for more info) 
  - ```OKTA_USERNAME``` is the username to use. If present will skip username input.
  - ```OKTA_PASSWORD``` is the password to use. If present will skip password input.
  - ```OKTA_AWS_ROLE_TO_ASSUME``` is the role to use. If present will try to match okta account's retrieved role list and use it. Will still prompt if no match found.
  
  - **Obtaining the AWS app url**
    - Navigate to the ```Admin Dashboard``` of you Okta organization
    - Select the ```Applications``` tab and click on your AWS Application 
    - Under the ```General``` menu, scroll down to find the ```App Embed Link``` section 
    - Your link is located under ```EMBED LINK``` 
  - Replace the example values in ```config.properties``` with your values

## Troubleshooting

### I get "You have no factors enrolled"

This means that MFA is enforced, but you have no factors enrolled on your user.

You should enrol a CLI-supported factor (all except Duo as far as I know).

### I have Duo, but I get "None of your factors are supported"

This means that MFA is enforced, but none of the factors you have enrolled are supported.

Okta's integration with Duo requires an iframe which isn't practical to interact with from a CLI context. 

### I can pick a role, but I get "not authorized to perform: sts:AssumeRole"

If you see an error like this:
```plain
User arn:aws:iam::123456789012:assumed-role/RoleName/user.name@example.com is not authorized to perform:
sts:AssumeRole on resource: arn:aws:iam::123456789012:role/RoleName 
```

Then you will need to attach an inline policy like this to the role to allow the role to assume itself:

```json
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Effect": "Allow",
            "Action": [
                "sts:AssumeRole"
            ],
            "Resource": [
                "arn:aws:iam::123456789012:role/RoleName"
            ]
        }
    ]
}
```

## Support 
Have a question or see a bug? Email developers@okta.com. For feature requests, feel free to open an issue on this repo. If you find a security vulnerability, please follow our [Vulnerability Reporting Process](https://www.okta.com/vulnerability-reporting-policy/).

## License
Copyright 2017 Okta, Inc. All rights reserved.

Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0.

Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
