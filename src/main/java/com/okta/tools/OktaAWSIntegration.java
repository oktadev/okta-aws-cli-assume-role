/*!
 * Copyright (c) 2016, Okta, Inc. and/or its affiliates. All rights reserved.
 * The Okta software accompanied by this notice is provided pursuant to the Apache License, Version 2.0 (the "License.")
 *
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0.
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *
 * See the License for the specific language governing permissions and limitations under the License.
 */

package com.okta.tools;

import com.amazonaws.AmazonClientException;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.BasicSessionCredentials;
import com.amazonaws.auth.profile.ProfilesConfigFile;
import com.amazonaws.services.identitymanagement.model.GetPolicyRequest;
import com.amazonaws.services.identitymanagement.model.GetPolicyResult;
import com.amazonaws.services.identitymanagement.model.GetPolicyVersionRequest;
import com.amazonaws.services.identitymanagement.model.GetPolicyVersionResult;
import com.amazonaws.services.identitymanagement.model.GetRoleRequest;
import com.amazonaws.services.identitymanagement.model.GetRoleResult;
import com.amazonaws.services.identitymanagement.model.Policy;
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClient;
import com.amazonaws.services.securitytoken.model.AssumeRoleWithSAMLRequest;
import com.amazonaws.services.securitytoken.model.AssumeRoleWithSAMLResult;
import com.amazonaws.services.identitymanagement.*;
import com.amazonaws.services.identitymanagement.model.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.okta.sdk.clients.AuthApiClient;
import com.okta.sdk.clients.UserApiClient;
import com.okta.sdk.exceptions.ApiException;
import com.okta.sdk.framework.ApiClientConfiguration;
import com.okta.sdk.models.auth.AuthResult;
import com.okta.sdk.models.factors.Factor;
import org.apache.commons.codec.binary.Base64;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URLDecoder;
import java.net.UnknownHostException;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.nio.charset.*;
import java.util.function.Function;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

//Amazon SDK namespaces
//Okta SDK namespaces

public class OktaAWSIntegration {

    //User specific variables
    private static String oktaOrg = "";
    private static String oktaAWSAppURL = "";
    private static String awsIamKey = null;
    private static String awsIamSecret = null;
    private static AuthApiClient authClient;

    private static final String DefaultProfileName = "default";

    private static UserApiClient userClient;
    private static String userId;
    private static String crossAccountRoleName = null;
    private String roleToAssume; //the ARN of the role the user wants to eventually assume (not the cross-account role, the "real" role in the target account)
    private static final Logger logger = LogManager.getLogger(awscli.class);


    private UserChoiceSelect selector = new InputChoice();
    private CredRetriever credRetriever  = new StdinCredRetriever();



    public static void main(String[] args) throws Exception {

    }

    protected String authenticateAndSetupProfile() throws IOException {
        awsSetup();
        extractCredentials();

        String profileName = oktaAwsHandshake();
        return profileName;
    }


    private  String oktaAwsHandshake() throws IOException {
        String resultSAML = initiateOktaAuthentication();


        // Step #3: Assume an AWS role using the SAML Assertion from Okta
        AssumeRoleWithSAMLResult assumeResult = assumeAWSRole(resultSAML);

        com.amazonaws.services.securitytoken.model.AssumedRoleUser aru = assumeResult.getAssumedRoleUser();
        String arn = aru.getArn();


        // Step #4: Get the final role to assume and update the config file to add it to the user's profile
        GetRoleToAssume(crossAccountRoleName);
        logger.trace("Role to assume ARN: " + roleToAssume);

        // Step #5: Write the credentials to ~/.aws/credentials
        String profileName = setAWSCredentials(assumeResult, arn);

        UpdateConfigFile(profileName, roleToAssume);
        UpdateConfigFile(DefaultProfileName, roleToAssume);
        return profileName;
    }

    private String mfa(JSONObject authResponse) {

        try {
            //User selects which factor to use
            JSONObject factor = selectFactor(authResponse);
            String factorType = factor.getString("factorType");
            String stateToken = authResponse.getString("stateToken");

            //factor selection handler
            switch (factorType) {
                case ("question"): {
                    //question factor handler
                    String sessionToken = questionFactor(factor, stateToken);
                    if (sessionToken.equals("change factor")) {
                        System.err.println("Factor Change Initiated");
                        return  mfa(authResponse);
                    }
                    return sessionToken;
                }
                case ("sms"): {
                    //sms factor handler
                    String sessionToken = smsFactor(factor, stateToken);
                    if (sessionToken.equals("change factor")) {
                        System.err.println("Factor Change Initiated");
                        return mfa(authResponse);
                    }
                    return sessionToken;

                }
                case ("token:software:totp"): {
                    //token factor handler
                    String sessionToken = totpFactor(factor, stateToken);
                    if (sessionToken.equals("change factor")) {
                        System.err.println("Factor Change Initiated");
                        return mfa(authResponse);
                    }
                    return sessionToken;
                }
                case ("push"): {
                    //push factor handles
                    String result = pushFactor(factor, stateToken);
                    if (result.equals("timeout") || result.equals("change factor")) {
                        return mfa(authResponse);
                    }
                    return result;
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        } catch (ClientProtocolException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        return "";
    }

    /* Assumes SAML role selected by the user based on authorized Okta AWS roles given in SAML assertion result SAML
         * Precondition: String resultSAML
         * Postcondition: returns type AssumeRoleWithSAMLResult
         */
    private AssumeRoleWithSAMLResult assumeAWSRole(String resultSAML) {
        // Decode SAML response
        resultSAML = resultSAML.replace("&#x2b;", "+").replace("&#x3d;", "=");
        String resultSAMLDecoded = new String(Base64.decodeBase64(resultSAML));

        ArrayList<String> principalArns = new ArrayList<String>();
        ArrayList<String> roleArns = new ArrayList<String>();

        //When the app is not assigned to you no assertion is returned
        if (!resultSAMLDecoded.contains("arn:aws")) {
            logger.error("\nYou do not have access to AWS through Okta. \nPlease contact your administrator.");
            System.exit(0);
        }


        //Gather list of applicable AWS roles
        List<roleInfo> roles = new ArrayList<roleInfo>();
        while (resultSAMLDecoded.indexOf("arn:aws") != -1) {
            /*Trying to parse the value of the Role SAML Assertion that typically looks like this:
            <saml2:AttributeValue xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:type="xs:string">
            arn:aws:iam::[AWS-ACCOUNT-ID]:saml-provider/Okta,arn:aws:iam::[AWS-ACCOUNT-ID]:role/[ROLE_NAME]
            </saml2:AttributeValue>
      </saml2:Attribute>
            */
            int start = resultSAMLDecoded.indexOf("arn:aws");
            int end = resultSAMLDecoded.indexOf("</saml2:", start);
            String resultSAMLRole = resultSAMLDecoded.substring(start, end);
            String[] parts = resultSAMLRole.split(",");
            principalArns.add(parts[0]);
            roleArns.add(parts[1]);
            roles.add(new roleInfo(parts[0],parts[1]));

            resultSAMLDecoded = (resultSAMLDecoded.substring(resultSAMLDecoded.indexOf("</saml2:AttributeValue") + 1));
        }


        roleInfo role = selector.select("role", "Please choose the role you would like to assume", roles, r -> r.roleArn);
        String principalArn = role.principalArn;
        String roleArn = role.roleArn;
        crossAccountRoleName = roleArn.substring(roleArn.indexOf("/") + 1);
        logger.debug("Cross-account role is " + crossAccountRoleName);


        //creates empty AWS credentials to prevent the AWSSecurityTokenServiceClient object from unintentionally loading the previous profile we just created
        BasicAWSCredentials awsCreds = new BasicAWSCredentials("", "");

        //use user credentials to assume AWS role
        AWSSecurityTokenServiceClient stsClient = new AWSSecurityTokenServiceClient(awsCreds);

        AssumeRoleWithSAMLRequest assumeRequest = new AssumeRoleWithSAMLRequest()
                .withPrincipalArn(principalArn)
                .withRoleArn(roleArn)
                .withSAMLAssertion(resultSAML)
                .withDurationSeconds(3600); //default token duration to 12 hours

        return stsClient.assumeRoleWithSAML(assumeRequest);
    }

    private  String initiateOktaAuthentication() {
        // Step #1: Initiate the authentication and capture the SAML assertion.
        String resultSAML = "";
        try {

            String strOktaSessionToken = oktaAuthntication();
            if (!strOktaSessionToken.equalsIgnoreCase(""))
                //Step #2 get SAML assertion from Okta
                resultSAML = awsSamlHandler(strOktaSessionToken);
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (UnknownHostException e) {
            logger.error("\nUnable to establish a connection with AWS. \nPlease verify that your OKTA_AWS_APP_URL parameter is correct and try again");
            System.exit(0);
        } catch (ClientProtocolException e) {
            logger.error("\nNo Org found, please specify an OKTA_ORG parameter in your config.properties file");
            System.exit(0);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return resultSAML;
    }






    /* Authenticates users credentials via Okta, return Okta session token
     * Postcondition: returns String oktaSessionToken
     * */
    private String oktaAuthntication() throws ClientProtocolException, JSONException, IOException {
        CloseableHttpResponse responseAuthenticate = null;
        int requestStatus = 0;

        //Redo sequence if response from AWS doesn't return 200 Status
        while (requestStatus != 200) {

            Creds creds = credRetriever.getCreds();
            responseAuthenticate = authnticateCredentials(creds.username,creds.password);
            requestStatus = responseAuthenticate.getStatusLine().getStatusCode();
            authnFailHandler(requestStatus, responseAuthenticate);
        }

        //Retrieve and parse the Okta response for session token
        BufferedReader br = new BufferedReader(new InputStreamReader(
                (responseAuthenticate.getEntity().getContent())));

        String outputAuthenticate = br.readLine();
        JSONObject jsonObjResponse = new JSONObject(outputAuthenticate);

        responseAuthenticate.close();

        if (jsonObjResponse.getString("status").equals("MFA_REQUIRED")) {
            return mfa(jsonObjResponse);
        } else {
            return jsonObjResponse.getString("sessionToken");
        }
    }

    /*Uses user's credentials to obtain Okta session Token */
    private static CloseableHttpResponse authnticateCredentials(String username, String password) throws JSONException, ClientProtocolException, IOException {
        HttpPost httpost = null;
        CloseableHttpClient httpClient = HttpClients.createDefault();

        //HTTP Post request to Okta API for session token
        httpost = new HttpPost("https://" + oktaOrg + "/api/v1/authn");
        httpost.addHeader("Accept", "application/json");
        httpost.addHeader("Content-Type", "application/json");
        httpost.addHeader("Cache-Control", "no-cache");

        //construction of request JSON
        JSONObject jsonObjRequest = new JSONObject();
        jsonObjRequest.put("username", username);
        jsonObjRequest.put("password", password);

        StringEntity entity = new StringEntity(jsonObjRequest.toString(), StandardCharsets.UTF_8);
        entity.setContentType("application/json");
        httpost.setEntity(entity);

        return httpClient.execute(httpost);
    }

    /* creates required AWS credential file if necessary" */
    private static void awsSetup() throws FileNotFoundException, UnsupportedEncodingException {
        //check if credentials file has been created
        File f = new File(System.getProperty("user.home") + "/.aws/credentials");
        //creates credentials file if it doesn't exist yet
        if (!f.exists()) {
            f.getParentFile().mkdirs();

            PrintWriter writer = new PrintWriter(f, "UTF-8");
            writer.println("[default]");
            writer.println("aws_access_key_id=");
            writer.println("aws_secret_access_key=");
            writer.close();
        }

        f = new File(System.getProperty("user.home") + "/.aws/config");
        //creates credentials file if it doesn't exist yet
        if (!f.exists()) {
            f.getParentFile().mkdirs();

            PrintWriter writer = new PrintWriter(f, "UTF-8");
            writer.println("[profile default]");
            writer.println("output = json");
            writer.println("region = us-east-1");
            writer.close();
        }
    }

    /* Parses application's config file for app URL and Okta Org */
    private static void extractCredentials() throws IOException {
        //BufferedReader oktaBr = new BufferedReader(new FileReader(new File (System.getProperty("user.dir")) +"/oktaAWSCLI.config"));
        //RL, 2016-02-25, moving to properties file
        String strLocalFolder = System.getProperty("user.dir");
        File propertiesFile = new File("config.properties");
        FileReader reader = new FileReader(propertiesFile);
        Properties props = new Properties();
        props.load(reader);

        //extract oktaOrg and oktaAWSAppURL from Okta settings file
        oktaOrg = props.getProperty("OKTA_ORG");
        oktaAWSAppURL = props.getProperty("OKTA_AWS_APP_URL");
        awsIamKey = props.getProperty("AWS_IAM_KEY");
        awsIamSecret = props.getProperty("AWS_IAM_SECRET");
    }

    /*Uses user's credentials to obtain Okta session Token */
    private static AuthResult authenticateCredentials(String username, String password) throws ApiException, JSONException, ClientProtocolException, IOException {

        ApiClientConfiguration oktaSettings = new ApiClientConfiguration("https://" + oktaOrg, "");
        AuthResult result = null;
        authClient = new AuthApiClient(oktaSettings);
        userClient = new UserApiClient(oktaSettings);

        // Check if the user credentials are valid
        result = authClient.authenticate(username, password, "");
        // The result has a getStatus method which is a string of status of the request.
        // Example - SUCCESS for successful authentication
        String status = result.getStatus();
        return result;
    }

    /*Handles possible authentication failures */
    private static void authnFailHandler(int requestStatus, CloseableHttpResponse response) {
        //invalid creds
        if (requestStatus == 400 || requestStatus == 401) {
            logger.error("You provided invalid credentials, please run this program again.");
        } else if (requestStatus == 500) {
            //failed connection establishment
            logger.error("\nUnable to establish connection with: " +
                    oktaOrg + " \nPlease verify that your Okta org url is correct and try again");
            System.exit(0);
        } else if (requestStatus != 200) {
            //other
            throw new RuntimeException("Failed : HTTP error code : "
                    + response.getStatusLine().getStatusCode());
        }
    }

    /*Handles possible AWS assertion retrieval errors */
    private static void samlFailHandler(int requestStatus, CloseableHttpResponse responseSAML) throws UnknownHostException {
        if (responseSAML.getStatusLine().getStatusCode() == 500) {
            //incorrectly formatted app url
            throw new UnknownHostException();
        } else if (responseSAML.getStatusLine().getStatusCode() != 200) {
            //other
            throw new RuntimeException("Failed : HTTP error code : "
                    + responseSAML.getStatusLine().getStatusCode());
        }
    }

    /* Handles user selection prompts */
    private static int numSelection(int max) {
        Scanner scanner = new Scanner(System.in);

        int selection = -1;
        while (selection == -1) {
            //prompt user for selection
            System.err.print("Selection: ");
            String selectInput = scanner.nextLine();
            try {
                selection = Integer.parseInt(selectInput) - 1;
                if (selection >= max) {
                    InputMismatchException e = new InputMismatchException();
                    throw e;
                }
            } catch (InputMismatchException e) {
                //raised by something other than a number entered
                logger.error("Invalid input: Please enter a number corresponding to a role \n");
                selection = -1;
            } catch (NumberFormatException e) {
                //raised by number too high or low selected
                logger.error("Invalid input: Please enter in a number \n");
                selection = -1;
            }
        }
        return selection;
    }

    /* Retrieves SAML assertion from Okta containing AWS roles */
    private static String awsSamlHandler(String oktaSessionToken) throws ClientProtocolException, IOException {
        HttpGet httpget = null;
        CloseableHttpResponse responseSAML = null;
        CloseableHttpClient httpClient = HttpClients.createDefault();
        String resultSAML = "";
        String outputSAML = "";

        // Part 2: Get the Identity Provider and Role ARNs.
        // Request for AWS SAML response containing roles
        httpget = new HttpGet(oktaAWSAppURL + "?onetimetoken=" + oktaSessionToken);
        responseSAML = httpClient.execute(httpget);
        samlFailHandler(responseSAML.getStatusLine().getStatusCode(), responseSAML);

        //Parse SAML response
        BufferedReader brSAML = new BufferedReader(new InputStreamReader(
                (responseSAML.getEntity().getContent())));
        //responseSAML.close();

        while ((outputSAML = brSAML.readLine()) != null) {
            if (outputSAML.contains("SAMLResponse")) {
                resultSAML = outputSAML.substring(outputSAML.indexOf("value=") + 7, outputSAML.indexOf("/>") - 1);
                break;
            }
        }
        httpClient.close();
        return resultSAML;
    }


    private void GetRoleToAssume(String roleName) {

        if (roleName != null && !roleName.equals("") && awsIamKey != null && awsIamSecret != null && !awsIamKey.equals("") && !awsIamSecret.equals("")) {

            logger.debug("Creating the AWS Identity Management client");
            AmazonIdentityManagementClient identityManagementClient
                    = new AmazonIdentityManagementClient(new BasicAWSCredentials(awsIamKey, awsIamSecret));

            logger.debug("Getting role: " + roleName);
            GetRoleResult roleresult = identityManagementClient.getRole(new GetRoleRequest().withRoleName(roleName));
            logger.debug("GetRoleResult: " + roleresult.toString());
            Role role = roleresult.getRole();
            logger.debug("getRole: " + role.toString());



            ListAttachedRolePoliciesResult arpr = identityManagementClient.listAttachedRolePolicies(new ListAttachedRolePoliciesRequest().withRoleName(roleName));
            logger.debug("ListAttachedRolePoliciesResult: " + arpr.toString());
            ListRolePoliciesResult lrpr = identityManagementClient.listRolePolicies(new ListRolePoliciesRequest().withRoleName(roleName));
            logger.debug("ListRolePoliciesResult: " + lrpr.toString());

            List<String> inlinePolicies = lrpr.getPolicyNames();
            if (inlinePolicies.size() == 0) {
                logger.debug("There are no inlines policies");
            }
            List<AttachedPolicy> managedPolicies = arpr.getAttachedPolicies();
            if (managedPolicies.size() == 0) {
                logger.debug("There are no managed policies");
            }


            if (managedPolicies.size() >= 1) //we prioritize managed policies over inline policies
            {
                AttachedPolicy attachedPolicy = selector.select("policy", "Managed Policies", managedPolicies, p -> p.toString());

                logger.debug("Selected policy " + attachedPolicy.toString());
                GetPolicyRequest gpr = new GetPolicyRequest().withPolicyArn(attachedPolicy.getPolicyArn());

                GetPolicyResult rpr = identityManagementClient.getPolicy(gpr);
                logger.debug("GetPolicyResult: " + attachedPolicy.toString());
                Policy policy = rpr.getPolicy();

                GetPolicyVersionResult pvr = identityManagementClient.getPolicyVersion(new GetPolicyVersionRequest().withPolicyArn(policy.getArn()).withVersionId(policy.getDefaultVersionId()));
                logger.debug("GetPolicyVersionResult: " + pvr.toString());

                String policyDoc = pvr.getPolicyVersion().getDocument();

                roleToAssume = ProcessPolicyDocument(policyDoc);
            } else if (inlinePolicies.size() >= 1) //processing inline policies if we have no managed policies
            {
                 String inlinePolicyName = selector.select("inline-policy","Inline Policies:", inlinePolicies, s-> s);
                logger.debug("Inline Policies " + inlinePolicies.toString());

                //Have to set the role name and the policy name (both are mandatory fields
                //TODO: handle more than 1 policy (ask the user to choose it?)
                GetRolePolicyRequest grpr = new GetRolePolicyRequest().withRoleName(roleName).withPolicyName(inlinePolicyName);
                GetRolePolicyResult rpr = identityManagementClient.getRolePolicy(grpr);
                String policyDoc = rpr.getPolicyDocument();
                roleToAssume = ProcessPolicyDocument(policyDoc);
            }
        }
    }


    private String ProcessPolicyDocument(String policyDoc) {

        String strRoleToAssume = null;
        try {
            String policyDocClean = URLDecoder.decode(policyDoc, "UTF-8");
            logger.debug("Clean Policy Document: " + policyDocClean);
            ObjectMapper objectMapper = new ObjectMapper();

            try {
                JsonNode rootNode = objectMapper.readTree(policyDocClean);
                JsonNode statement = rootNode.path("Statement");
                logger.debug("Statement node: " + statement.toString());
                JsonNode resource = null;
                if (statement.isArray()) {
                    logger.debug("Statement is array");
                    for (int i = 0; i < statement.size(); i++) {
                        String action = statement.get(i).path("Action").textValue();
                        if (action != null && action.equals("sts:AssumeRole")) {
                            resource = statement.get(i).path("Resource");
                            logger.debug("Resource node: " + resource.toString());
                            break;
                        }
                    }
                } else {
                    logger.debug("Statement is NOT array");
                    if (statement.get("Action").textValue().equals("sts:AssumeRole")) {
                        resource = statement.path("Resource");
                        logger.debug("Resource node: " + resource.toString());
                    }
                }
                if (resource != null) {
                    if (resource.isArray()) { //if we're handling a policy with an array of AssumeRole attributes
                        ArrayList<String> lstRoles = new ArrayList<String>();
                        for (final JsonNode node : resource) {
                            lstRoles.add(node.asText());
                        }
                        strRoleToAssume = SelectRole(lstRoles);
                    } else {
                        strRoleToAssume = resource.textValue();
                        logger.debug("Role to assume: " + roleToAssume);
                    }
                }
            } catch (IOException ioe) {
            }
        } catch (UnsupportedEncodingException uee) {

        }
        return strRoleToAssume;
    }

    /* Prompts the user to select a role in case the role policy contains an array of roles instead of a single role
    */
    private  String SelectRole(List<String> lstRoles) {
        return selector.select("role","Please select the role you wish to assume:", lstRoles, s->s);
    }

    /* Retrieves AWS credentials from AWS's assumedRoleResult and write the to aws credential file
     * Precondition :  AssumeRoleWithSAMLResult assumeResult
     */
    private String setAWSCredentials(AssumeRoleWithSAMLResult assumeResult, String credentialsProfileName) throws FileNotFoundException, UnsupportedEncodingException, IOException {
        BasicSessionCredentials temporaryCredentials =
                new BasicSessionCredentials(
                        assumeResult.getCredentials().getAccessKeyId(),
                        assumeResult.getCredentials().getSecretAccessKey(),
                        assumeResult.getCredentials().getSessionToken());

        String awsAccessKey = temporaryCredentials.getAWSAccessKeyId();
        String awsSecretKey = temporaryCredentials.getAWSSecretKey();
        String awsSessionToken = temporaryCredentials.getSessionToken();

        if (credentialsProfileName.startsWith("arn:aws:sts::")) {
            credentialsProfileName = credentialsProfileName.substring(13);
        }
        if (credentialsProfileName.contains(":assumed-role")) {
            credentialsProfileName = credentialsProfileName.replaceAll(":assumed-role", "");
        }

        //update the credentials file with the unique profile name
        UpdateCredentialsFile(credentialsProfileName, awsAccessKey, awsSecretKey, awsSessionToken);
        //also override the default profile
        UpdateCredentialsFile(DefaultProfileName, awsAccessKey, awsSecretKey, awsSessionToken);

        return credentialsProfileName;
    }

    private void UpdateCredentialsFile(String profileName, String awsAccessKey, String awsSecretKey, String awsSessionToken)
            throws IOException {

        ProfilesConfigFile profilesConfigFile = null;
        Object[] args = {new String(profileName)};
        MessageFormat profileNameFormatWithBrackets = new MessageFormat("[{0}]");
        String profileNameWithBrackets = profileNameFormatWithBrackets.format(args);

        try {
            profilesConfigFile = new ProfilesConfigFile();
        } catch (AmazonClientException ace) {
            PopulateCredentialsFile(profileNameWithBrackets, awsAccessKey, awsSecretKey, awsSessionToken);
        }

        try {
            if (profilesConfigFile != null && profilesConfigFile.getCredentials(profileName) != null) {
                //if we end up here, it means we were  able to find a matching profile
                PopulateCredentialsFile(profileNameWithBrackets, awsAccessKey, awsSecretKey, awsSessionToken);
            }
        } catch (AmazonClientException ace) {
            //this could happen if the default profile doesn't have a valid AWS Access Key ID
            //in this case, error would be "Unable to load credentials into profile [default]: AWS Access Key ID is not specified."
            PopulateCredentialsFile(profileNameWithBrackets, awsAccessKey, awsSecretKey, awsSessionToken);
        } catch (IllegalArgumentException iae) {
            //if we end up here, it means we were not able to find a matching profile so we need to append one
            PopulateCredentialsFile(profileNameWithBrackets, awsAccessKey, awsSecretKey, awsSessionToken);
            //FileWriter fileWriter = new FileWriter(System.getProperty("user.home") + "/.aws/credentials", true);
            //TODO: need to be updated to work with Windows
            //PrintWriter writer = new PrintWriter(fileWriter);
            //WriteNewProfile(writer, profileNameWithBrackets, awsAccessKey, awsSecretKey, awsSessionToken);
            //fileWriter.close();
        }
    }

    private void PopulateCredentialsFile(String profileNameLine, String awsAccessKey, String awsSecretKey, String awsSessionToken)
            throws IOException {

        File inFile = new File(System.getProperty("user.home") + "/.aws/credentials");
        FileInputStream fis = new FileInputStream(inFile);
        BufferedReader br = new BufferedReader(new InputStreamReader(fis));
        File tempFile = new File(inFile.getAbsolutePath() + ".tmp");
        PrintWriter pw = new PrintWriter(new FileWriter(tempFile));

        //first, we add our refreshed profile
        WriteNewProfile(pw, profileNameLine, awsAccessKey, awsSecretKey, awsSessionToken);

        String line = null;
        int lineCounter = 0;
        boolean bFileStart = true;

        //second, we're copying all the other profile from the original credentials file
        while ((line = br.readLine()) != null) {

            if (line.equalsIgnoreCase(profileNameLine) || (lineCounter > 0 && lineCounter < 4)) {
                //we found the line we must replace and we will skip 3 additional lines
                ++lineCounter;
            } else {
                if ((!line.equalsIgnoreCase("") && !line.equalsIgnoreCase("\n"))) {
                    if (line.startsWith("[")) {
                        //this is the start of a new profile, so we're adding a separator line
                        pw.println();
                    }
                    pw.println(line);
                }
            }
        }

        pw.flush();
        pw.close();
        br.close();

        //delete the original credentials file
        if (!inFile.delete()) {
            System.out.println("Could not delete original credentials file");
        }

        // Rename the new file to the filename the original file had.
        if (!tempFile.renameTo(inFile)) {
            System.out.println("Could not rename file");
        }
    }

    private void UpdateConfigFile(String profileName, String roleToAssume) throws IOException {

        File inFile = new File(System.getProperty("user.home") + "/.aws/config");

        FileInputStream fis = new FileInputStream(inFile);
        BufferedReader br = new BufferedReader(new InputStreamReader(fis));
        File tempFile = new File(inFile.getAbsolutePath() + ".tmp");
        PrintWriter pw = new PrintWriter(new FileWriter(tempFile));

        //first, we add our refreshed profile
        WriteNewRoleToAssume(pw, profileName, roleToAssume);

        String line = null;
        int lineCounter = 0;
        boolean bFileStart = true;

        //second, we're copying all the other profiles from the original config file
        while ((line = br.readLine()) != null) {

            if (line.contains(profileName)) {
                //we found the section we must replace but we don't necessarily know how many lines we need to skip
                while ((line = br.readLine()) != null) {
                    if (line.startsWith("[")) {
                        pw.println(line); //this is a new profile line, so we're copying it
                        break;
                    }
                }
            } else {
                if ((!line.contains(profileName) && !line.equalsIgnoreCase("\n"))) {
                    pw.println(line);
                    logger.debug(line);
                }
            }


        }

        pw.flush();
        pw.close();
        br.close();

        //delete the original credentials file
        if (!inFile.delete()) {
            System.out.println("Could not delete original config file");
        } else {
            // Rename the new file to the filename the original file had.
            if (!tempFile.renameTo(inFile))
                System.out.println("Could not rename file");
        }
    }

    public  void WriteNewProfile(PrintWriter pw, String profileNameLine, String awsAccessKey, String awsSecretKey, String awsSessionToken) {

        pw.println(profileNameLine);
        pw.println("aws_access_key_id=" + awsAccessKey);
        pw.println("aws_secret_access_key=" + awsSecretKey);
        pw.println("aws_session_token=" + awsSessionToken);
    }

    public static void WriteNewRoleToAssume(PrintWriter pw, String profileName, String roleToAssume) {

        pw.println("[profile " + profileName + "]");
        if (roleToAssume != null && !roleToAssume.equals(""))
            pw.println("role_arn=" + roleToAssume);
        pw.println("source_profile=" + profileName);
        pw.println("region=us-east-1");
    }

    /*Handles factor selection based on factors found in parameter authResponse, returns the selected factor
     * Precondition: JSINObject authResponse
     * Postcondition: return session token as String sessionToken
     */
    public  JSONObject selectFactor(JSONObject authResponse) throws JSONException {
        JSONArray factors = authResponse.getJSONObject("_embedded").getJSONArray("factors");
        List<JSONObject> jsonFactors = new ArrayList<JSONObject>(factors.length());
        for (int i = 0; i < factors.length(); i++) {
            jsonFactors.add(factors.getJSONObject(i));
        }

        return selector.select("factor",
                "Multi-Factor authentication is required. Please select a factor to use. Pretty please?", jsonFactors, new factorDescriber());
    }

    private static class factorDescriber implements Function<JSONObject,String> {

        @Override
        public String apply(JSONObject factor) {
            String factorType = factor.getString("factorType");
            if (factorType.equals("question")) {
                factorType = "Security Question";
            } else if (factorType.equals("sms")) {
                factorType = "SMS Authentication";
            } else if (factorType.equals("token:software:totp")) {
                String provider = factor.getString("provider");
                if (provider.equals("GOOGLE")) {
                    factorType = "Google Authenticator";
                } else {
                    factorType = "Okta Verify";
                }
            }

            return factorType;
        }
    }


    private static String questionFactor(JSONObject factor, String stateToken) throws JSONException, ClientProtocolException, IOException {
        String question = factor.getJSONObject("profile").getString("questionText");
        Scanner scanner = new Scanner(System.in);
        String sessionToken = "";
        String answer = "";

        //prompt user for answer
        System.out.println("\nSecurity Question Factor Authentication\nEnter 'change factor' to use a different factor\n");
        while (sessionToken == "") {
            if (answer != "") {
                System.out.println("Incorrect answer, please try again");
            }
            System.out.println(question);
            System.out.println("Answer: ");
            answer = scanner.nextLine();
            //verify answer is correct
            if (answer.toLowerCase().equals("change factor")) {
                return answer;
            }
            sessionToken = verifyAnswer(answer, factor, stateToken, "question");
        }
        return sessionToken;
    }


    /*Handles sms factor authentication
     * Precondition: question factor as JSONObject factor, current state token stateToken
     * Postcondition: return session token as String sessionToken
     */
    private static String smsFactor(JSONObject factor, String stateToken) throws ClientProtocolException, JSONException, IOException {
        Scanner scanner = new Scanner(System.in);
        String answer = "";
        String sessionToken = "";

        //prompt for sms verification
        System.out.println("\nSMS Factor Authentication \nEnter 'change factor' to use a different factor");
        while (sessionToken == "") {
            if (answer != "") {
                System.out.println("Incorrect passcode, please try again or type 'new code' to be sent a new sms token");
            } else {
                //send initial code to user
                sessionToken = verifyAnswer("", factor, stateToken, "sms");
            }
            System.out.println("SMS Code: ");
            answer = scanner.nextLine();
            //resends code
            if (answer.equals("new code")) {
                answer = "";
                System.out.println("New code sent! \n");
            } else if (answer.toLowerCase().equals("change factor")) {
                return answer;
            }
            //verifies code
            sessionToken = verifyAnswer(answer, factor, stateToken, "sms");
        }
        return sessionToken;
    }


    /*Handles token factor authentication, i.e: Google Authenticator or Okta Verify
     * Precondition: question factor as JSONObject factor, current state token stateToken
     * Postcondition: return session token as String sessionToken
     */
    private static String totpFactor(JSONObject factor, String stateToken) throws ClientProtocolException, JSONException, IOException {
        Scanner scanner = new Scanner(System.in);
        String sessionToken = "";
        String answer = "";

        //prompt for token
        System.out.println("\n" + factor.getString("provider") + " Token Factor Authentication\nEnter 'change factor' to use a different factor");
        while (sessionToken == "") {
            if (answer != "") {
                System.out.println("Invalid token, please try again");
            }

            System.out.println("Token: ");
            answer = scanner.nextLine();
            //verify auth Token
            if (answer.toLowerCase().equals("change factor")) {
                return answer;
            }
            sessionToken = verifyAnswer(answer, factor, stateToken, "token:software:totp");
        }
        return sessionToken;
    }


    /*Handles push factor authentication
     *
     *
     */
    private static String pushFactor(JSONObject factor, String stateToken) throws ClientProtocolException, JSONException, IOException {
        Calendar newTime = null;
        Calendar time = Calendar.getInstance();
        String sessionToken = "";

        System.out.println("\nPush Factor Authentication");
        while (sessionToken == "") {
            //System.out.println("Token: ");
            //prints waiting tick marks
            //if( time.compareTo(newTime) > 4000){
            //    System.out.println("...");
            //}
            //Verify if Okta Push has been pushed
            sessionToken = verifyAnswer(null, factor, stateToken, "push");
            System.out.println(sessionToken);
            if (sessionToken.equals("Timeout")) {
                System.out.println("Session has timed out");
                return "timeout";
            }
            time = newTime;
            newTime = Calendar.getInstance();
        }
        return sessionToken;
    }


    /*Handles verification for all Factor types
     * Precondition: question factor as JSONObject factor, current state token stateToken
     * Postcondition: return session token as String sessionToken
     */
    private static String verifyAnswer(String answer, JSONObject factor, String stateToken, String factorType)
            throws JSONException, ClientProtocolException, IOException {

        String sessionToken = null;

        JSONObject profile = new JSONObject();
        String verifyPoint = factor.getJSONObject("_links").getJSONObject("verify").getString("href");

        profile.put("stateToken", stateToken);

        JSONObject jsonObjResponse = null;

        //if (factorType.equals("question")) {

        if (answer != null && answer != "") {
            profile.put("answer", answer);
        }

        //create post request
        CloseableHttpResponse responseAuthenticate = null;
        CloseableHttpClient httpClient = HttpClients.createDefault();

        HttpPost httpost = new HttpPost(verifyPoint);
        httpost.addHeader("Accept", "application/json");
        httpost.addHeader("Content-Type", "application/json");
        httpost.addHeader("Cache-Control", "no-cache");

        StringEntity entity = new StringEntity(profile.toString(), StandardCharsets.UTF_8);
        entity.setContentType("application/json");
        httpost.setEntity(entity);
        responseAuthenticate = httpClient.execute(httpost);

        BufferedReader br = new BufferedReader(new InputStreamReader((responseAuthenticate.getEntity().getContent())));

        String outputAuthenticate = br.readLine();
        jsonObjResponse = new JSONObject(outputAuthenticate);

        if (jsonObjResponse.has("errorCode")) {
            String errorSummary = jsonObjResponse.getString("errorSummary");
            System.out.println(errorSummary);
            System.out.println("Please try again");
            if (factorType.equals("question")) {
                questionFactor(factor, stateToken);
            }

            if (factorType.equals("token:software:totp")) {
                totpFactor(factor, stateToken);
            }
        }
        //}

        if (jsonObjResponse != null && jsonObjResponse.has("sessionToken"))
            sessionToken = jsonObjResponse.getString("sessionToken");

        String pushResult = null;
        if (factorType.equals("push")) {
            if (jsonObjResponse.has("_links")) {
                JSONObject linksObj = jsonObjResponse.getJSONObject("_links");

                //JSONObject pollLink = links.getJSONObject("poll");
                JSONArray names = linksObj.names();
                JSONArray links = linksObj.toJSONArray(names);
                String pollUrl = "";
                for (int i = 0; i < links.length(); i++) {
                    JSONObject link = links.getJSONObject(i);
                    String linkName = link.getString("name");
                    if (linkName.equals("poll")) {
                        pollUrl = link.getString("href");
                        break;
                        //System.out.println("[ " + (i+1) + " ] :" + factorType );
                    }
                }


                while (pushResult == null || pushResult.equals("WAITING")) {
                    pushResult = null;
                    CloseableHttpResponse responsePush = null;
                    httpClient = HttpClients.createDefault();

                    HttpPost pollReq = new HttpPost(pollUrl);
                    pollReq.addHeader("Accept", "application/json");
                    pollReq.addHeader("Content-Type", "application/json");
                    pollReq.addHeader("Cache-Control", "no-cache");

                    entity = new StringEntity(profile.toString(), StandardCharsets.UTF_8);
                    entity.setContentType("application/json");
                    pollReq.setEntity(entity);

                    responsePush = httpClient.execute(pollReq);

                    br = new BufferedReader(new InputStreamReader((responsePush.getEntity().getContent())));

                    String outputTransaction = br.readLine();
                    JSONObject jsonTransaction = new JSONObject(outputTransaction);


                    if (jsonTransaction.has("factorResult")) {
                        pushResult = jsonTransaction.getString("factorResult");
                    }

                    if (pushResult == null && jsonTransaction.has("status")) {
                        pushResult = jsonTransaction.getString("status");
                    }

                    System.out.println("Waiting for you to approve the Okta push notification on your device...");
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException iex) {

                    }

                    //if(pushResult.equals("SUCCESS")) {
                    if (jsonTransaction.has("sessionToken")) {
                        sessionToken = jsonTransaction.getString("sessionToken");
                    }
                    //}
                    /*
                    if(pushResult.equals("TIMEOUT")) {
                        sessionToken = "timeout";
                    }
*/
                }
            }

        }


        if (sessionToken != null)
            return sessionToken;
        else
            return pushResult;
    }


    /*Handles token factor authentication, i.e: Google Authenticator or Okta Verify
    * Precondition: question factor as JSONObject factor, current state token stateToken
    * Postcondition: return session token as String sessionToken
    */
    private static String totpFactor(Factor factor, String stateToken) throws IOException {
        Scanner scanner = new Scanner(System.in);
        String sessionToken = "";
        String answer = "";

        //prompt for token
        System.out.println("\n" + factor.getProvider() + " Token Factor Authentication\nEnter 'change factor' to use a different factor");
        while (sessionToken == "") {
            if (answer != "") {
                System.out.println("Invalid token, please try again");
            }

            System.out.println("Token: ");
            answer = scanner.nextLine();
            //verify auth Token
            if (answer.toLowerCase().equals("change factor")) {
                return answer;
            }

            sessionToken = verifyAnswer(answer, factor, stateToken);
        }
        return sessionToken;
    }


    /*Handles verification for all Factor types
    * Precondition: question factor as JSONObject factor, current state token stateToken
    * Postcondition: return session token as String sessionToken
    */
    private static String verifyAnswer(String answer, Factor factor, String stateToken) throws IOException {

        AuthResult authResult = authClient.authenticateWithFactor(stateToken, factor.getId(), answer);

        if (!authResult.getStatus().equals("SUCCESS")) {
            System.out.println("\nThe second-factor verification failed.");
        } else {
            return authResult.getSessionToken();
        }

        return "";
    }


}



class roleInfo {
    String principalArn;
    String roleArn;

    public roleInfo(String principalArn, String roleArn) {
        this.principalArn = principalArn;
        this.roleArn = roleArn;
    }


}