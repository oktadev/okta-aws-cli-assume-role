# Okta AWS-CLI Tool

Important Note: This tool has been verified to work on Mac OS X El Capitan and Windows Server 2012 R2 and is expected to work on Linux and Unix as well.

The Okta AWS-CLI Tool allows Okta customers to take advantage of [Okta](www.okta.com) to use the [AWS Command Line Interface](https://aws.amazon.com/cli) without relying on permanent AWS keys. More specifically, it allows them to generate temporary 60-minute keys
that are stored for each profile in the ```~/.aws/credentials``` file. Additionally, if the AWS environment is leveraging cross-account roles and the AssumeRole AWS function, the tool will be able to detect that you choose
to assume a cross-account role and will be able to extract the actual role in the target account as well as add an additional profile line the ```~/.aws/config``` file.

##Setup

- Use ```git clone https://github.com/raphaellondner-okta/okta-aws-cli.git``` to clone the repository locally.
- In Terminal, run ```defaults write com.apple.finder AppleShowAllFiles YES``` if you want to be able to inspect the ```~/.aws/credentials``` and ```~/.aws/config``` files.

##Compiling the application
The application was built and compiled with [JetBrains' IntelliJ IDEA](https://www.jetbrains.com/idea/). The instructions below explain how to compile the application using IntelliJ IDEA. Note that you don't have to compile the application in order to be able to execute it, since the compiled executable (a jar file) is available on GitHub in the ```out``` sub-folder.

###Prerequisites
First of all, it goes without saying that you will need to install the [Java SE 8x](http://www.oracle.com/technetwork/java/javase/downloads/index.html) or the [Java JDK 8x](http://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html).

Next, in order to compile the application, you will need to download and install the [AWS Java SDK](https://aws.amazon.com/sdk-for-java/) as well as the [AWS Command Line Interface](https://aws.amazon.com/cli). Once you have downloaded the ```AWS Java SDK```, make sure you copy the latest AWS JAR file (currently ```aws-java-sdk-1.10.71.jar```) into the ```lib``` folder of this tool (otherwise, you will not be able to compile or run it).

You will also need the [Okta Java SDK](https://github.com/okta/oktasdk-java) but we provide a compiled version of it within this repository so there's nothing specific you need to do at this point. We used the following path structure to manage our IntelliJ project: ```$HOME/Projects/okta-aws-cli``` and we recommend you do the same to avoid fixing too many references. We've also created a ```lib``` sub-folder inside the ```okta-aws-cli``` root folder where we're storing the AWS Java SDK (```aws-java-sdk-1.10.71.jar```), the Okta Java SDK (```okta-sdk.jar```) and other dependencies. We strongly recommend that you do the same.

###Opening the project with IntelliJ Idea

- Open the IntelliJ Idea IDE and browse to the ```okta-aws-cli``` folder you have cloned from GitHub inside the ```Projects``` folder.
- Go to ```File => Project Structure``` and in the Libraries menu, fix the Java references that don't match your local setup.
- Go to ```Build => Make Project``` in order to compile the project.
- The project also builds the JAR artifact, so if you browse to the ```out``` sub-folder, you will see the ```oktaawscli.jar``` JAR artifact.
- Make sure the ```awscli.command``` file is in the ```out``` sub-folder.

##Configuring AWS IAM

  Because this tool aims at facilitating your interaction with the AWS CLI, it must be able to look into the role you select to assume in order to figure out whether it's a role with direct permissions on the account, or a cross-account role that allows you to assume a role (with real permissions) in another account. To perform this introspection into the AWS roles, the tool must be granted AIM Read Only Access in AWS so that it can automatically add the proper role_arn and source_profile entries into the AWS config file.

In order to generate the key and secret to be configured in the ```config.properties``` file, log in to your AWS identity account with the root admin account. Navigate to the ```Identity & Access Management``` section of your dashboard and go the ```Users``` menu.

- Create a new user and click on ```Close``` (do not download the security credentials).
- Select the user you just created and navigate to the ```Permissions``` tab
- Under ```Managed Policies```, click on the ```Attach Policy``` button, select the ```IAMReadOnlyAccess``` policy and click on the ```Attach Policy``` button.
- Navigate to the ```Security Credentials``` tab, and click the ```Create Access Key``` button
- Click on the ```Show User Security Credentials``` link and copy/paste the ```Access Key ID``` and ```Secret Access Key``` values into a safe location. You will need them below.


If you do not want to grant the tool AIM Read Only Access in AWS, you will have to use the ```aws sts assume-role``` CLI command as documented in the [AWS CLI documentation](http://docs.aws.amazon.com/IAM/latest/UserGuide/id_roles_use_switch-role-cli.html).

##Configuring the application
  It is essential that ```config.properties``` be in the same directory as the ```oktaawscli.jar``` file (i.e. in the ```out``` directory). The ```config.properties``` config file holds information specific to each Okta organization (or "tenant") and needs to be configured on an org to org basis. Please copy it from the ```src/com/okta/tools``` folder to the ```out``` folder.
  
Here is the list of parameters that must be maintained in the ```config.properties``` file:
  
  - ```OKTA_ORG``` which is the url of your Okta org (starting with https://).
  - ```OKTA_AWS_APP_URL``` is the url link of your Okta AWS application url (see below for more info)
  - ```AWS_IAM_KEY```: paste the ```Access Key ID``` value you retrieved in the  previous section (or leave it blank if you'd rather use the ```aws sts assume-role``` CLI command)
  -  ```AWS_IAM_SECRET```: paste the ```Secret Access Key``` value you retrieved in the previous section (or leave it blank if you'd rather use the ```aws sts assume-role``` CLI command)
  
  - **Obtaining the AWS app url**
    - Navigate to the ```Admin Dashboard``` of you Okta organization
    - Select the ```Applications``` tab and click on your AWS Application 
    - Under the ```General``` menu, scroll down to find the ```App Embed Link``` section 
    - Your link is located under ```EMBED LINK``` 
  - Replace the example values in ```config properties``` with your values

##Running the application###
  - To run the application, you can use  the ```awscli.command``` file you will find in the ```out``` folder. You may need to edit to fix the references to the AWS Java SDK, which we remind you to put into the ```lib``` directory.
  - Navigate to the ```out``` sub-folder with Terminal and run ```./awscli.command``` 
  - The default command is the following: ```java -classpath oktaawscli.jar:$HOME/Projects/okta-aws-cli/lib/aws-java-sdk-1.10.71.jar:$HOME/Projects/okta-aws-cli/lib/okta-sdk.jar com.okta.tools.awscli```
  
