

image: build/distributions/okta-aws-cli-assume-role.tar
	docker build -t okta .

build/distributions/okta-aws-cli-assume-role.tar: $(wildcard src/**)
	gradle distTar

dockerbuild:
	docker run -v $(shell pwd):/workspace -w /workspace \
	-v $(HOME)/.gradle/caches:/home/gradle/.gradle/caches gradle:jdk8-alpine gradle test distTar 
