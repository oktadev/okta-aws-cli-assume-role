variable "policy_arns" {
    type = "list"
}
// Terraform doesn't allow us to use the length of policy arns to create
// multiple resources
variable "num_policies" {
    default = 1
}
variable "user" {
	default = "policy_user"
}

resource "aws_iam_user" "policy_user" {
    name = "${var.user}"
}
resource "aws_iam_access_key" "policy_user" {
    user = "${aws_iam_user.policy_user.name}"
}
resource "aws_iam_user_policy_attachment" "policy-attach" {
    count = "${var.num_policies}"
    user = "${aws_iam_user.policy_user.name}"
    policy_arn = "${element(var.policy_arns,count.index)}"
}

output "aws_access_id" {
	value = "${aws_iam_access_key.policy_user.id}"
}
output "aws_secret_key" {
	value = "${aws_iam_access_key.policy_user.secret}"
}


