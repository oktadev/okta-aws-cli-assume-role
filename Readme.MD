# Okta AWS CLI Assume Role tool

 :information_source: **Disclaimer:** This tool is community-supported and is maintained by members of the Okta team for developers and IT professionals. This tool is not an official Okta product and does not qualify for any Okta support. Anyone who chooses to use this tool must ensure that their implementation meets any applicable legal obligations including any Okta terms and conditions.

New to Amazon Web Services with Okta? **Start with the [Configuring AWS in Okta](#configuring-aws-in-okta)**.

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
- [Getting help](#getting-help)
- [License](#license)

## Installation

### Windows

1. Run the following in a [PowerShell console](http://technet.microsoft.com/library/hh831491.aspx#start-windows-powershell)
    ```pwsh
    Set-ExecutionPolicy -Scope Process -ExecutionPolicy unrestricted -Force; Invoke-Expression ((New-Object Net.WebClient).DownloadString('https://raw.githubusercontent.com/oktadev/okta-aws-cli-assume-role/master/bin/Install-OktaAwsCli.ps1')); .$profile
    ```
2. Customize **%userprofile%\\.okta\\config.properties** and set **OKTA_ORG** and **OKTA_AWS_APP_URL** appropriately. For example,
   ```properties
   OKTA_ORG=acmecorp.oktapreview.com
   OKTA_AWS_APP_URL=https://acmecorp.oktapreview.com/home/amazon_aws/0oa5zrwfs815KJmVF0h7/137
   ```

### macOS/Linux

1. Run the following in a Terminal, optionally setting a custom
   `PREFIX` value (default: `~/.okta`):

    ```bash
    PREFIX=~/.okta bash <(curl -fsSL https://raw.githubusercontent.com/oktadev/okta-aws-cli-assume-role/master/bin/install.sh) -i
    ```

2. Customize `~/.okta/config.properties` and set **OKTA_ORG** and **OKTA_AWS_APP_URL** appropriately. For example,
   
   ```properties
   OKTA_ORG=acmecorp.oktapreview.com
   OKTA_AWS_APP_URL=https://acmecorp.oktapreview.com/home/amazon_aws/0oa5zrwfs815KJmVF0h7/137
   ```
3. Make sure `/usr/local/bin` (or whatever `$PREFIX/bin` is) is in your **PATH**

### Docker

1. Create `~/.okta/config.properties` and set **OKTA_ORG** and **OKTA_AWS_APP_URL** appropriately. For example,
   
   ```properties
   OKTA_ORG=acmecorp.oktapreview.com
   OKTA_AWS_APP_URL=https://acmecorp.oktapreview.com/home/amazon_aws/0oa5zrwfs815KJmVF0h7/137
   ```
2. Run this command:
   ```shell
   docker run -v ~/.okta/config.properties:/root/.okta/config.properties -it tomsmithokta/okta-awscli-java
   ```

Read more at [@tom-smith-okta's okta-awscli-java Docker repo](https://hub.docker.com/r/tomsmithokta/okta-awscli-java/).

### Manual install

Create a `.okta` directory in your home directory. For example, `~/.okta`.

Download the latest release JAR and put it in `.okta`:
https://github.com/oktadev/okta-aws-cli-assume-role/releases

Create `~/.okta/config.properties` and set
**OKTA_ORG** and **OKTA_AWS_APP_URL** appropriately. For example,

```properties
OKTA_ORG=acmecorp.oktapreview.com
OKTA_AWS_APP_URL=https://acmecorp.oktapreview.com/home/amazon_aws/0oa5zrwfs815KJmVF0h7/137
```

Create `~/.okta/logging.properties` with the following content,

```properties
com.amazonaws.auth.profile.internal.BasicProfileConfigLoader = NONE
```

Copy scripts from `.okta/bin` to somewhere on your **PATH**.

## Usage

Verify your setup with a simple command:

```bash
okta-aws test sts get-caller-identity
```

This will prompt for Okta credentials, log you into AWS, let you pick a role, and store a session profile called **test** for you.

Run the program again to see session resumption (you won't be asked for Okta credentials until the session expires):

```bash
okta-aws test sts get-caller-identity
```

NOTE: **okta-aws** is a function loaded from your shell profile, not a typical program or command stored in a file.

NOTE: On a *nix platform the `withokta` wrapper script will attempt to parse `$https_proxy` as a URI. If successful the host and port values will be passed to the JVM. User credentials in the proxy configuration are not currently used. This allows the `okta-aws` tool to be used in an environment where internet access for the servers is mediate via a proxy, e.g an EC2 instance inside a restricted VPC.

The proxy URI _must_ be of the form `http://host:port/`. Both the host and port are mandatory.

## Reference
* [okta-listroles(1)](docs/man/okta-listroles.1.md)
* [okta-credential_process(1)](docs/man/okta-credential_process.1.md)

## Compiling the application

The application was built and compiled with [JetBrains' IntelliJ IDEA](https://www.jetbrains.com/idea/). Note that you don't have to compile the application in order to be able to execute it, since the compiled executable (a JAR file) is available [on GitHub](https://github.com/oktadev/okta-aws-cli-assume-role/releases).

### Prerequisites
First of all, it goes without saying that you will need to install the [Java SE 11x](http://www.oracle.com/technetwork/java/javase/downloads/index.html) or the [Java JDK 11x](https://www.oracle.com/technetwork/java/javase/downloads/jdk11-downloads-5066655.html).

Then you will need [Maven 2 or later](https://maven.apache.org/install.html) to run the build.

### Building on the command line

Get a single JAR with all dependencies:

Use ```git clone https://github.com/oktadev/okta-aws-cli-assume-role.git``` to clone the repository locally. Then, build with Maven:

```bash
mvn package
cp target/okta-aws-cli-*.jar ~/.okta/okta-aws-cli.jar
```

## Configuring AWS in Okta

See for details on setting up [Amazon Web Services Account Federation](https://help.okta.com/en/prod/Content/Topics/DeploymentGuides/AWS/aws-deployment.htm) to allow logging into AWS through Okta and this tool.

An alternative integration can be found in [Integrating the Amazon Web Services Command Line Interface Using Okta](https://support.okta.com/help/Documentation/Knowledge_Article/Integrating-the-Amazon-Web-Services-Command-Line-Interface-Using-Okta), which allows logging into AWS / Okta through the [AWS CLI](https://docs.aws.amazon.com/cli/latest/userguide/cli-configure-sso.html). The okta-aws-cli is _not_ compatible with this integration.

## Configuring the application
Here is the list of parameters that can be environment variables or settings in the ```~/.okta/config.properties``` file:
  
  - ```OKTA_ORG``` which is the url of your Okta org (starting with https://).
  - ```OKTA_AWS_APP_URL``` is the url link of your Okta AWS application url (see below for more info) 
  - ```OKTA_USERNAME``` is the username to use. If present will skip username input.
  - ```OKTA_PASSWORD_CMD``` is the command to fetch your password instead of showing a password prompt. [Read more...](docs/OKTA_PASSWORD_CMD.md)
  - ```OKTA_ENV_MODE``` set to **true** to run sub-command with **AWS_ACCESS_KEY_ID**, **AWS_SECRET_ACCESS_KEY**, and **AWS_SESSION_TOKEN** env vars set. Temporary credentials are shared in memory and kept off disk in this mode. (default: **false**)
  - ```OKTA_BROWSER_AUTH``` set to **true** to use integrated web browser for authentication (default: **false**)
  - ```OKTA_COOKIES_PATH``` is directory path to store cookies.properties for Okta. This is particularly useful when running this tool in many concurrent processes like you might with **OKTA_ENV_MODE** (default: ~/.okta)
  - ```OKTA_PROFILE``` is the name of the AWS profile to create/reuse. (default: get AWS profile name based on per-session STS user name)  
  - ```OKTA_AWS_REGION``` is the default AWS region to store with the created profile.
  - ```OKTA_AWS_ROLE_TO_ASSUME``` is the IAM Role ARN to use. If present will try to match okta account's retrieved role list and use it. Will still prompt if no match found. (ex: **arn:aws:iam::123456789012:role/EC2-Admins**)
  - ```OKTA_STS_DURATION``` is the duration the role will be assumed, in seconds. The maximum session duration allowed by AWS is 12 hours and this needs to be set on the role as well.  Defaults to 1hr.
  - ```OKTA_MFA_CHOICE``` is the provider and factor type to use if prompted for MFA.  Example: ```OKTA.push```. See [Factors documentation](https://developer.okta.com/docs/api/resources/factors#factor-type) for values. (default: use single factor or prompt user to select from usable factors).
  - ```OKTA_IGNORE_SAML_REQ_CONTAIN``` is an optional parameter that allow users to ignore parsing SAML requests, where the URL contains a specific string . This is relevant when using the custom IdP factor, which adds additional SAML requests to users authentication flow. See [Custom IdP Factor Authentication
](https://help.okta.com/en/prod/Content/Topics/Security/MFA_Custom_Factor.htm) for more details. Defaults to an empty string.
  
  - **Obtaining the AWS app url**
    - Navigate to the ```Admin Dashboard``` of you Okta organization
    - Select the ```Applications``` tab and click on your AWS Application 
    - Under the ```General``` menu, scroll down to find the ```App Embed Link``` section 
    - Your link is located under ```EMBED LINK``` 
  - Replace the example values in ```~/.okta/config.properties``` with your values

You can specify configuration overrides for each profile by creating a ```~/.okta/config.{profilename}.properties``` file. The base settings
will be loaded first and the profile-specific settings will be loaded after, allowing you to only override specific settings that need to be different.
For example, if you want the ```prod``` profile to connect to a different Okta org, create a ```~/.okta/config.prod.properties```file and set 
```OKTA_ORG``` to something different.

Note: environment variables take precedence over any config file.

## Troubleshooting

### I get "You have no factors enrolled"

This means that MFA is enforced, but you have no factors enrolled on your user.

You should enrol a CLI-supported factor (all except Duo as far as I know).

If you are using Duo Push, consider setting **OKTA_BROWSER_AUTH=true** in [the configuration](#configuring-the-application).

### I have Duo, but I get "None of your factors are supported"

This means that MFA is enforced, but none of the factors you have enrolled are supported.

Okta's integration with Duo requires an iframe which isn't practical to interact with from a CLI context. 

## Getting help
Have a question or see a bug? Post a question on the [Okta Dev Forums](https://devforum.okta.com/) or email developers@okta.com. For feature requests, feel free to open an issue on this repo.

If you find a security vulnerability, please follow our [Vulnerability Reporting Process](https://www.okta.com/vulnerability-reporting-policy/).

## License
Copyright 2017 Okta, Inc. All rights reserved.

Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0.

Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
