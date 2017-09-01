

image: build/distributions/okta-aws-cli-assume-role.tar
	docker build -t okta .

build/distributions/okta-aws-cli-assume-role.tar: $(wildcard src/**)
	gradle distTar



dockerbuild:
	docker run -v $(shell pwd):/workspace -w /workspace \
	 gradle:jdk8-alpine gradle test distTar 
