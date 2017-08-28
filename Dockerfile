FROM  docker.artifactory.corp.code42.com/c42/cloud-workstation


ARG AWS_SDK_VERSION=1.11.180
ARG OKTA_DIR=/opt/okta

WORKDIR $OKTA_DIR
RUN apk --no-cache add openjdk8-jre-base

# This was uploaded manually because aws only publishes the latest version, and doesn't include that version info in the file name until you unzip
# or you can build the source in an uber complex project
RUN curl -o aws-java-sdk.jar https://artifactory.corp.code42.com/artifactory/ext-release-local/com/amazonaws/aws-java-sdk-osgi/$AWS_SDK_VERSION/aws-java-sdk-osgi-$AWS_SDK_VERSION.jar  



# TODO cleanup
COPY scripts/* $OKTA_DIR/

RUN ln -sf $OKTA_DIR/okta_launch /usr/local/bin/okta_launch
ENV OKTA_DIR=$OKTA_DIR
ENV OKTA_ORG code42.okta.com
# These keys are just to allow okta to make requests to 
# be authenticated through their SAML provider.  
# as such they are safe to be redistributed
ENV OKTA_AWS_IAM_KEY AKIAIZ7FY3ZURWWBXI4Q
ENV OKTA_AWS_IAM_SECRET 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
ENV OKTA_AWS_APP_URL https://code42.okta.com/home/amazon_aws/0oafeti2fsEVtYdVR0x7/272


RUN ln -sf $OKTA_DIR/okta_launch /usr/local/bin/

COPY build/distributions/okta-aws-cli-assume-role.tar okta-aws-cli-assume-role.tar
RUN tar xf okta-aws-cli-assume-role.tar

# Change back 
WORKDIR /workspace