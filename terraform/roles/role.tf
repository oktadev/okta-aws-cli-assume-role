


variable "managed_policy_names" {
    default = []
}
variable "custom_policy_names" {
    default = []
}


variable "provider_arn" {
    description = "the arn of the saml provoider"
}
variable "role_name" {}
data "aws_caller_identity" "current" {}

data "aws_iam_policy_document" "assume-role-policy" {
  statement {
    actions = ["sts:AssumeRoleWithSAML"]
    principals {
      type        = "Federated"
      identifiers = ["${var.provider_arn}"]
    }
    condition {
        test = "StringEquals" 
        variable = "SAML:aud" 
        values = ["https://signin.aws.amazon.com/saml"]
    }
  }
}

// Terraform doesn't allow us to use the length of policy arns to create
// multiple resources
variable "num_policies" {
    default = 1
}


resource "aws_iam_role" "policy_role" {
    name = "${var.role_name}"
    assume_role_policy = "${data.aws_iam_policy_document.assume-role-policy.json}"
}

resource "aws_iam_role_policy_attachment" "attach_managed" {
    count = "${length(var.managed_policy_names)}"
    role       = "${aws_iam_role.policy_role.name}"
    policy_arn = "arn:aws:iam::aws:policy/${element(var.managed_policy_names, count.index)}"
}


resource "aws_iam_role_policy_attachment" "attach_custom" {
    count = "${length(var.custom_policy_names)}"
    role       = "${aws_iam_role.policy_role.name}"
    policy_arn = "arn:aws:iam::${data.aws_caller_identity.current.account_id}:policy/${element(var.managed_policy_names, count.index)}"
}

