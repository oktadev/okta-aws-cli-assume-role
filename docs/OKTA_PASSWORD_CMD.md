# OKTA_PASSWORD_CMD Documentation

## Help wanted!

Please contribute additional examples for your favored platform or password manager.

## Example: macOS KeyChain

1. Create password entry `security add-generic-password -a $OKTA_USERNAME -s okta-aws-cli -T /usr/bin/security -U`
2. Launch KeyChain Access and search for **okta-aws-cli**
3. Set OKTA_PASSWORD_CMD to `security find-generic-password -a $OKTA_USERNAME -s okta-aws-cli -w`

## Example: GNU/Linux [GNOME Keyring](https://wiki.gnome.org/Projects/GnomeKeyring)

1. Check if you have installed the `secret-tool` command. In Debian is included in the `libsecret-tools` package, so you can install it with `apt`:
   ```bash
   sudo apt install libsecret-tools
   ```
2. Create a new entry in your **Login Keyring** (you will be asked for your password):
   ```bash
   secret-tool store --label='Okta Credentials' okta:username $OKTA_USERNAME
   ```
3. Set `OKTA_PASSWORD_CMD` to:
   ```bash
   secret-tool lookup okta:username $OKTA_USERNAME
   ```
