FROM ubuntu:latest as builder

RUN apt-get update && \
    apt-get install -y openjdk-8-jdk openjfx && \
    apt-get install -y maven

ADD . okta-aws-cli

RUN cd okta-aws-cli && \
    mvn package && \
    cp target/okta-aws-cli-*.jar out/oktaawscli.jar

FROM openjdk:8-jre-alpine

# Versions: https://pypi.python.org/pypi/awscli#downloads
ENV AWS_CLI_VERSION 1.16.7

RUN apk --no-cache add python py-pip ca-certificates groff less && \
    pip --no-cache-dir install awscli==${AWS_CLI_VERSION}

COPY --from=builder /okta-aws-cli/out/ /okta

WORKDIR /okta
ENTRYPOINT ["sh", "awscli"]