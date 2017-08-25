package com.okta.tools;

import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class UserChoiceSelectTest {


    List<roleInfo> roles = new ArrayList<roleInfo>();
    UserChoiceSelect chooser;

    @Test
    public void testConfigChoiceRegex() {
        Map<String,String> choices = new HashMap();
        roles.add(new roleInfo("arn:1", "role1"));
        roles.add(new roleInfo("arn:2", "role2"));
        roles.add(new roleInfo("arn:3", "arn://role3"));


        choices.put("role", ".*role3");

        ScanFactory scanFactory = mock(ScanFactory.class);
        when(scanFactory.getScanner()).thenReturn(new Scanner("2"));

        chooser = new ConfigThenInput(new ConfigChoice(choices), new InputChoice(scanFactory));

        roleInfo roleInfo = chooser.select("role", "select your role", roles,r->r.roleArn);
        // In this case the value is in config, so we should never ask for input
        verify(scanFactory, times(0)).getScanner();
        assertEquals("arn:3", roleInfo.principalArn);

        List<String> policyNames = new ArrayList<String>();
        policyNames.add("policy1");
        policyNames.add("policyB");

        String policy = chooser.select("policy","choose your policy", policyNames, s->s);
        verify(scanFactory, times(1)).getScanner();
        assertEquals("policyB", policy);

    }


    @Test
    public void testInputChoice() {

        roles.add(new roleInfo("arn:1", "role1"));
        roles.add(new roleInfo("arn:2", "role2"));

        //User is presented with choices by number.
        chooser = new InputChoice(mockScanData("2"));
        roleInfo chosen = chooser.select("role","name your role",roles, r -> r.roleArn);

        //TODO change output stream to printstream with factory, test out println.
        assertEquals("role2",chosen.roleArn);
    }

    private ScanFactory mockScanData(String inputString) {
        ScanFactory scanFactory = mock(ScanFactory.class);
        when(scanFactory.getScanner()).thenReturn(new Scanner(inputString));
        return scanFactory;
    }




}