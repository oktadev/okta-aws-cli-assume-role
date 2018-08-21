# OKTA_PASSWORD_CMD Documentation

## Help wanted!

Please contribute additional examples for your favored platform or password manager.

## Example: macOS KeyChain

1. Create password entry `security add-generic-password -a $OKTA_USERNAME -s okta-aws-cli -T /usr/bin/security -U`
2. Launch KeyChain Access and search for **okta-aws-cli**
3. Set OKTA_PASSWORD_CMD to `security find-generic-password -a $OKTA_USERNAME -s okta-aws-cli -w`
