

image: build/distributions/okta-aws-cli-assume-role.tar
	docker build -t okta .

build/distributions/okta-aws-cli-assume-role.tar: $(wildcard src/**)
	gradle distTar
