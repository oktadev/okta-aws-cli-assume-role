#!/bin/bash

dockerize -template $OKTA_DIR/okta.tpl.properties:./config.properties 
exec $@