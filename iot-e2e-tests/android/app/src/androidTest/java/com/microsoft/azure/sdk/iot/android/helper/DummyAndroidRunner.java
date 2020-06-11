/*
 *  Copyright (c) Microsoft. All rights reserved.
 *  Licensed under the MIT license. See LICENSE file in the project root for full license information.
 */

package com.microsoft.azure.sdk.iot.android.helper;

import com.microsoft.azure.sdk.iot.common.helpers.BasicTierOnlyRule;
import com.microsoft.azure.sdk.iot.common.helpers.ConditionalIgnoreRule;

import org.junit.Test;

// This class is to add a dummy test for every TestGroup.  Currently, android testing errors out
// when a TestGroup did not execute any actual tests, so this forces each test group to have at least
// this test
public class DummyAndroidRunner
{
    @TestGroup1
    @TestGroup2
    @TestGroup3
    @TestGroup4
    @TestGroup5
    @TestGroup6
    @TestGroup7
    @TestGroup8
    @TestGroup9
    @TestGroup10
    @TestGroup11
    @TestGroup12
    @TestGroup13
    @TestGroup14
    @TestGroup15
    @TestGroup16
    @TestGroup17
    @TestGroup18
    @TestGroup19
    @TestGroup20
    @TestGroup21
    @TestGroup22
    @TestGroup23
    @TestGroup24
    @TestGroup25
    @Test
    @ConditionalIgnoreRule.ConditionalIgnore(condition = BasicTierOnlyRule.class)
    public void dummyTest()
    {
    }
}
