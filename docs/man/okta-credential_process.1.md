# okta-credential_process(1) -- Output AWS credentials in JSON format.

## SYNOPSIS

    okta-credential_process <role ARN>

## DESCRIPTION

The okta-credential_process tool allows you to authenticate to Okta and
assume an AWS IAM Role.

## FIELDS

  "Version"

    The version of the credential_process interface defined by the AWS
    CLI docs here:
    https://docs.aws.amazon.com/cli/latest/topic/config-vars.html

  "AccessKeyId"
  
    The AWS IAM (Identity and Access Management) access key ID.
    
  "SecretAccessKey"
  
    The AWS IAM secret access key.
    
  "SessionToken"
  
    The AWS STS (Security Token Service) session token.
    
  "Expiration"
  
    The timestamp at which the credentials will expire in ISO8601
    format. 

## EXAMPLES

Add something like the following to ~/.aws/config:
```ini
[profile dev]
credential_process = okta-credential_process arn:aws:iam::123456789012:role/ExampleRole
```
* Replace arn:aws:iam::123456789012:role/ExampleRole with the real IAM
  Role ARN (use okta-listroles to see available role ARNs)

Use the profile as follows:
```bash
aws --profile dev sts get-caller-identity
```

This also works with scripts using the AWS SDK for Python (aka boto3).

```python
import boto3
dev = boto3.session.Session(profile_name='dev')
s3 = dev.resource('s3')
for bucket in s3.buckets.all():
    print(bucket.name)
```

## NOTES

AWS CLI doesn't cache the credentials, neither does okta-aws-cli.
Credentials will be fetched by AWS CLI every time it runs (irregardless
of expiry)

# SEE ALSO

[okta-listroles(1)](okta-listroles.1.md)
