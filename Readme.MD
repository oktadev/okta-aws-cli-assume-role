# Okta AWS-CLI Tool

This readme file is an excerpt of the [Okta-AWS Integration Guide](https://support.okta.com/help/articles/Knowledge_Article/Amazon-Web-Services-and-Okta-Integration-Guide). We highly recommend that you first read it to understand how Okta and AWS integrate with each other.

Important Note: This tool has been verified to work on Mac OS X El Capitan and Windows Server 2012 R2 and is expected to work on Linux and Unix as well.

The Okta AWS-CLI Tool allows Okta customers to take advantage of [Okta](www.okta.com) to use the [AWS Command Line Interface](https://aws.amazon.com/cli) without relying on permanent AWS keys. More specifically, it allows them to generate temporary 60-minute keys
that are stored for each profile in the ```~/.aws/credentials``` file. Additionally, if the AWS environment is leveraging cross-account roles and the AssumeRole AWS function, the tool will be able to detect that you choose
to assume a cross-account role and will be able to extract the actual role in the target account as well as add an additional profile line the ```~/.aws/config``` file.

__Important note__: please note that this tool only supports Okta MFA set up at the organization level (i.e. not at the sign-on policy level).

## Setup

### Prerequisites

Download and install [Java JDK 8](http://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html). OpenJDK or any other compatible release will do.  
You will also need the [Okta Java SDK](https://github.com/okta/oktasdk-java) but we provide a compiled version of it within this repository so there's nothing specific you need to do at this point.

### Get and build

```
git clone https://github.com/oktadeveloper/okta-aws-cli-assume-role.git
cd okta-aws-cli-assume-role
mvn clean package
```

### Configure the application

```
cp src/main/resources/reference.conf config/okta.conf
```

Edit `config/okta.conf` in your preferred editor, set:

 - `okta.app_url` is the url link of your Okta AWS application url (see below for more info); no default, required, needs to be a valid URI
 - `okta.role` is the IAM role you want to get the credentials for; default: `Undefined-Role`
 - `okta.credentials_profile` the profile name used for `~/.aws/credentials` and `~/.aws/config` files; default: consistent identifiable string `<aws-account-id>/<role>/<okta-username>`
 - `aws.access_key` (optional, leave empty if not used): your AWS access key
 - `aws.secret_key` (optional, leave empty if not used): your AWS secret key

#### Obtaining the AWS app_url
 - Navigate to the `Admin Dashboard` of you Okta organization
 - Select `Applications` tab and click on your AWS Application 
 - Under the `General` menu, scroll down to find the `App Embed Link` section 
 - Your link is located under `EMBED LINK` 
 - Replace the example values in `okta.config` with your values

## Running the application

```
./bin/okta-login
```
