# Okta AWS CLI Assume Role tool

New to Amazon Web Services with Okta? **Start with the [Configuring AWS in Okta](#configuring-aws-in-okta).

This tool has been verified to work on macOS Sierra, High Sierra, Windows Server 2012 R2, Windows 10, and Ubuntu 16.04 LTS, and is expected to work on other Linux systems as well.

- [Installation](#installation)
- [Usage](#usage)
- [Compiling the application](#compiling-the-application)
    - [Prerequisites](#prerequisites)
    - [Building on the command line](#building-on-the-command-line)
    - [Opening the project with IntelliJ Idea](#opening-the-project-with-intellij-idea)
- [Configuring AWS in Okta](#configuring-aws-in-okta)
- [Configuring the application](#configuring-the-application)
- [History](#history)
    - [Wrap AWS CLI with session resumption (2017/12/5 update)](#wrap-aws-cli-with-session-resumption-2017125-update)
    - [Add config.properties parameters to support silent mode without prompt for user input (2017/6/8 update)](#add-configproperties-parameters-to-support-silent-mode-without-prompt-for-user-input-201768-update)
    - [AWS:reInvent Release (2016/11/29 update)](#awsreinvent-release-20161129-update)
- [Troubleshooting](#troubleshooting)
    - [I get "You have no factors enrolled"](#i-get-you-have-no-factors-enrolled)
    - [I have Duo, but I get "None of your factors are supported"](#i-have-duo-but-i-get-none-of-your-factors-are-supported)
    - [I can pick a role, but I get "not authorized to perform: sts:AssumeRole"](#i-can-pick-a-role-but-i-get-not-authorized-to-perform-stsassumerole)
- [Getting help](#getting-help)
- [License](#license)

## Installation

### Windows

1. Copy the following PowerShell bootstrap script into your clipboard:
    ```powershell
    Invoke-RestMethod https://raw.githubusercontent.com/oktadeveloper/okta-aws-cli-assume-role/master/bin/Install-OktaAwsCli.ps1 | clip.exe
    ```
2. Open a PowerShell console (regular user, NOT admin it will break)
3. Right-click once the paste the bootstrap script into the session
4. Right-click a second time to paste and run the install script
5. Customize **%userprofile%\\.okta\\config.properties** to reflect your Okta and Amazon Web Services setup

### macOS/Linux

1. Run the following in a Terminal:
    ```bash
    curl 'https://raw.githubusercontent.com/oktadeveloper/okta-aws-cli-assume-role/master/bin/install.sh' | bash
    ```
2. Customize **~/.okta/config.properties** to reflect your Okta and Amazon Web Services setup

### Manual install

Create a `.okta` directory in your home directory. For example, `~/.okta`.

Download the latest release JAR and put it in `.okta`:
https://github.com/oktadeveloper/okta-aws-cli-assume-role/releases

Copy `config.properties` to `.okta/config.properties` and set
**OKTA_ORG** and **OKTA_AWS_APP_URL** appropriately. For example,

```properties
OKTA_ORG=acmecorp.oktapreview.com
OKTA_AWS_APP_URL=https://acmecorp.oktapreview.com/home/amazon_aws/0oa5zrwfs815KJmVF0h7/137
```

Copy scripts from `.okta/bin` to somewhere on your PATH.

## Usage

Verify your setup with a simple command:

```bash
aws test sts get-caller-identity
```

This will prompt for Okta credentials, log you into AWS, let you pick a role, and store a session profile called **test** for you.

Run the program again to see session resumption (you won't be asked for Okta credentials until the session expires):

```bash
aws test sts get-caller-identity
```

## Compiling the application

The application was built and compiled with [JetBrains' IntelliJ IDEA](https://www.jetbrains.com/idea/). Note that you don't have to compile the application in order to be able to execute it, since the compiled executable (a JAR file) is available [on GitHub](https://github.com/oktadeveloper/okta-aws-cli-assume-role/releases).

### Prerequisites
First of all, it goes without saying that you will need to install the [Java SE 8x](http://www.oracle.com/technetwork/java/javase/downloads/index.html) or the [Java JDK 8x](http://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html).

Then you will need [Maven 2 or later](https://maven.apache.org/install.html) to run the build.

### Building on the command line

Get a single JAR with all dependencies:

Use ```git clone https://github.com/oktadeveloper/okta-aws-cli-assume-role.git``` to clone the repository locally. Then, build with Maven:

```bash
mvn package
cp target/okta-aws-cli-*.jar out/oktaawscli.jar
```

### Opening the project with IntelliJ Idea

- Open the IntelliJ Idea IDE and browse to the ```okta-aws-cli-assume-role``` folder you have cloned from GitHub inside the ```Projects``` folder.
- Go to ```File => Project Structure``` and in the Libraries menu, fix the Java references that don't match your local setup.
- Go to ```Build => Make Project``` in order to compile the project.
- The project also builds the JAR artifact, so if you browse to the ```out``` sub-folder, you will see the ```oktaawscli.jar``` JAR artifact.
- Make sure the ```awscli.command``` file is in the ```out``` sub-folder.

## Configuring AWS in Okta

[Integrating the Amazon Web Services Command Line Interface Using Okta](https://support.okta.com/help/Documentation/Knowledge_Article/Integrating-the-Amazon-Web-Services-Command-Line-Interface-Using-Okta). 

## Configuring the application
Here is the list of parameters that can be environment variables or settings in the ```~/.okta/config.properties``` file:
  
  - ```OKTA_ORG``` which is the url of your Okta org (starting with https://).
  - ```OKTA_AWS_APP_URL``` is the url link of your Okta AWS application url (see below for more info) 
  - ```OKTA_USERNAME``` is the username to use. If present will skip username input.
  - ```OKTA_PASSWORD``` is the password to use. If present will skip password input.
  - ```OKTA_BROWSER_AUTH``` set to **true** to use integrated web browser for authentication (default: **false**)
  - ```OKTA_COOKIES_PATH``` is directory path to store cookies.properties for Okta (default: ~/.okta)
  - ```OKTA_PROFILE``` is the name of the AWS profile to create/reuse.  May also be specified on the commandline by ```--profile```. (default: get AWS profile name based on per-session STS user name)  
  - ```OKTA_AWS_REGION``` is the default AWS region to store with the created profile.
  - ```OKTA_AWS_ROLE_TO_ASSUME``` is the role to use. If present will try to match okta account's retrieved role list and use it. Will still prompt if no match found.
  - ```OKTA_STS_DURATION``` is the duration the role will be assumed, in seconds. The maximum session duration allowed by AWS is 12 hours and this needs to be set on the role as well.  Defaults to 1hr.
  - ```OKTA_PROFILE_PREFIX``` is the text to prepend to the section name in ~/.aws/config for a named profile.  Defaults to "profile " (necessary for use with AWS CLI).
  - ```OKTA_CREDENTIALS_SUFFIX``` is the text to append to the section name in ~/.aws/credentals for a named profile.  Defaults to "_source" (necessary for use with AWS Tools for Windows PowerShell).
  
  - **Obtaining the AWS app url**
    - Navigate to the ```Admin Dashboard``` of you Okta organization
    - Select the ```Applications``` tab and click on your AWS Application 
    - Under the ```General``` menu, scroll down to find the ```App Embed Link``` section 
    - Your link is located under ```EMBED LINK``` 
  - Replace the example values in ```config.properties``` with your values

Note: environment variables take precedence over the config file.

## Troubleshooting

### I get "You have no factors enrolled"

This means that MFA is enforced, but you have no factors enrolled on your user.

You should enrol a CLI-supported factor (all except Duo as far as I know).

If you are using Duo Push, consider setting **OKTA_BROWSER_AUTH=true** in [the configuration](#configuring-the-application).

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

## Getting help
Have a question or see a bug? Post a question on the [Okta Dev Forums](https://devforum.okta.com/) or email developers@okta.com. For feature requests, feel free to open an issue on this repo.

If you find a security vulnerability, please follow our [Vulnerability Reporting Process](https://www.okta.com/vulnerability-reporting-policy/).

## License
Copyright 2017 Okta, Inc. All rights reserved.

Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0.

Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
