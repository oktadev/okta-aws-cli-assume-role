FROM  docker.artifactory.corp.code42.com/java:8-jre

ARG OKTA_DIR=/opt/okta

WORKDIR $OKTA_DIR

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