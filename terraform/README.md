This is an attempt to automate as much as possible of

http://saml-doc.okta.com/SAML_Docs/How-to-Configure-SAML-2.0-for-Amazon-Web-Service.html

Right now, if you add in a new role, that is not automatically detected, and you have to do some hackery in the UI


In order to reconcile the new role from AWS into Okta go to AWS (DEV) -> Provisioning.  Hit Edit, then uncheck and re-check the “Enable” button next to Create Users.  Then hit the “Test API Credentials” button


[2:06] 
Then go to Assignments, Groups and update the respective role to map to the newly imported role from AWS