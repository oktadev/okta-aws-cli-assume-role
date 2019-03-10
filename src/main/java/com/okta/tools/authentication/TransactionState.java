/*
 * Copyright 2019 Okta
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.okta.tools.authentication;

/**
 * Okta transaction state codes. See:
 *   https://developer.okta.com/docs/api/resources/authn#transaction-state
 */
public enum TransactionState {
  // So that default value is invalid
  INVALID("NOT A VALID TRANSACTIONSTATE"),

  UNAUTHENTICATED("Early Access User tried to access protected resource (ex: an app) but user is not " +
      "authenticated POST to the next link relation to authenticate user credentials."),

  PASSWORD_WARN("The user’s password was successfully validated but is about to expire and should be changed. POST " +
      "to the next link relation to change the user’s password."),

  PASSWORD_EXPIRED("The user’s password was successfully validated but is expired. POST to the next link relation to" +
      " change the user’s expired password."),

  RECOVERY("The user has requested a recovery token to reset their password or unlock their account. POST to the " +
      "next link relation to answer the user’s recovery question."),

  RECOVERY_CHALLENGE("The user must verify the factor-specific recovery challenge. " +
      "POST to the verify link relation to verify the recovery factor."),

  PASSWORD_RESET("The user successfully answered their recovery question and must to set a new password. POST to the" +
      " next link relation to reset the user’s password."),

  LOCKED_OUT("The user account is locked; self-service unlock or administrator unlock is required. POST to the " +
      "unlock link relation to perform a self-service unlock."),

  MFA_ENROLL("The user must select and enroll an available factor for additional verification. POST to the enroll " +
      "link relation for a specific factor to enroll the factor."),

  MFA_ENROLL_ACTIVATE("The user must activate the factor to complete enrollment. POST to the next link relation to " +
      "activate the factor."),

  MFA_REQUIRED("The user must provide additional verification with a previously enrolled factor. POST to the verify " +
      "link relation for a specific factor to provide additional verification."),

  MFA_CHALLENGE("The user must verify the factor-specific challenge. POST to the verify link relation to verify the " +
      "factor."),

  SUCCESS("Transaction completed successfully.");

  private final String description;

  TransactionState(String description) {
    this.description = description;
  }

  public String getDescription() { return this.description; }
}
