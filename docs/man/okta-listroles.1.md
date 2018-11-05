# okta-listroles(1) -- Output a list of roles available to assume.

## SYNOPSIS

    okta-listroles

## DESCRIPTION

The okta-listroles tool allows you to authenticate to Okta and list the
roles that are available to be assumed.

## FIELDS

  "accountName"

    The AWS account name and number the role belongs to.

  "roleOptions"
  
    A list of roleName and AWS IAM roleArn pairs available without a given account.
    
  "roleName"
  
    The name of the AWS IAM Role.
    
  "roleARN"
  
    The ARN (Amazon Resource Name) of the AWS IAM Role.

## EXAMPLES

Running a command like this:

    okta-listroles
    
Will prompt for Okta credentials (or reuse your session) and output
something like this to standard output:


    [
        {
        "accountName": "Account: example-corp (123456789012)",
        "roleOptions": [
            {
            "roleName": "Admin",
            "roleArn": "arn:aws:iam::123456789012:role/Admin"
            }
        ]
        },
        {
        "accountName": "Account: example-research (654321234567)",
        "roleOptions": [
            {
            "roleName": "Admin",
            "roleArn": "arn:aws:iam::654321234567:role/Admin"
            }
            {
            "roleName": "ReadOnly",
            "roleArn": "arn:aws:iam::654321234567:role/ReadOnly"
            }
        ]
        }
    ]

# SEE ALSO

[okta-credential_process(1)](okta-credential_process.1.md)
