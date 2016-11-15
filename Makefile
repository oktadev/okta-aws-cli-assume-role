.PHONY: deps build

deps:
	@which gradle >/dev/null || brew install gradle

build:
	@gradle build

