/*
 *  Copyright (c) Microsoft. All rights reserved.
 *  Licensed under the MIT license. See LICENSE file in the project root for full license information.
 */

package com.microsoft.azure.sdk.iot.common.tests.iothub.errorinjection;

import com.microsoft.azure.sdk.iot.common.helpers.*;
import com.microsoft.azure.sdk.iot.common.helpers.annotations.ContinuousIntegrationTest;
import com.microsoft.azure.sdk.iot.common.helpers.annotations.IotHubTest;
import com.microsoft.azure.sdk.iot.common.helpers.annotations.StandardTierHubOnlyTest;
import com.microsoft.azure.sdk.iot.common.setup.iothub.ReceiveMessagesCommon;
import com.microsoft.azure.sdk.iot.device.*;
import com.microsoft.azure.sdk.iot.device.DeviceTwin.Pair;
import com.microsoft.azure.sdk.iot.device.transport.IotHubConnectionStatus;
import com.microsoft.azure.sdk.iot.service.Module;
import com.microsoft.azure.sdk.iot.service.auth.AuthenticationType;
import com.microsoft.azure.sdk.iot.service.exceptions.IotHubException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static com.microsoft.azure.sdk.iot.common.helpers.CorrelationDetailsLoggingAssert.buildExceptionMessage;
import static com.microsoft.azure.sdk.iot.common.helpers.ErrorInjectionHelper.DefaultDelayInSec;
import static com.microsoft.azure.sdk.iot.common.helpers.ErrorInjectionHelper.DefaultDurationInSec;
import static com.microsoft.azure.sdk.iot.device.IotHubClientProtocol.*;
import static com.microsoft.azure.sdk.iot.service.auth.AuthenticationType.*;
import static org.junit.Assert.assertTrue;

/**
 * Test class containing all error injection tests to be run on JVM and android pertaining to receiving messages. Class needs to be extended
 * in order to run these tests as that extended class handles setting connection strings and certificate generation
 */
@IotHubTest
@RunWith(Parameterized.class)
public class ReceiveMessagesErrInjTests extends ReceiveMessagesCommon
{
    public ReceiveMessagesErrInjTests(IotHubClientProtocol protocol, AuthenticationType authenticationType, ClientType clientType, String publicKeyCert, String privateKey, String x509Thumbprint) throws Exception
    {
        super(protocol, authenticationType, clientType, publicKeyCert, privateKey, x509Thumbprint);
    }

    @Parameterized.Parameters(name = "{0} with {1} auth using {2}")
    public static Collection inputs() throws Exception
    {
        iotHubConnectionString = Tools.retrieveEnvironmentVariableValue(TestConstants.IOT_HUB_CONNECTION_STRING_ENV_VAR_NAME);
        isBasicTierHub = Boolean.parseBoolean(Tools.retrieveEnvironmentVariableValue(TestConstants.IS_BASIC_TIER_HUB_ENV_VAR_NAME));
        isPullRequest = Boolean.parseBoolean(Tools.retrieveEnvironmentVariableValue(TestConstants.IS_PULL_REQUEST, "false"));
        if (!isBasicTierHub)
        {
            //Device and Module
            return inputsCommon();
        }
        else
        {
            //only Device
            return inputsCommon(ClientType.DEVICE_CLIENT);
        }
    }

    @Test
    @StandardTierHubOnlyTest
    public void receiveMessagesWithTCPConnectionDrop() throws Exception
    {
        if (testInstance.protocol == HTTPS)
        {
            //test case not applicable
            return;
        }

        super.setupTest();
        this.errorInjectionTestFlow(ErrorInjectionHelper.tcpConnectionDropErrorInjectionMessage(DefaultDelayInSec, DefaultDurationInSec));
    }

    @Test
    @StandardTierHubOnlyTest
    @ContinuousIntegrationTest
    public void receiveMessagesWithAmqpsConnectionDrop() throws Exception
    {
        if (testInstance.protocol != AMQPS && testInstance.protocol != AMQPS_WS)
        {
            //test case not applicable
            return;
        }

        super.setupTest();
        this.errorInjectionTestFlow(ErrorInjectionHelper.amqpsConnectionDropErrorInjectionMessage(DefaultDelayInSec, DefaultDurationInSec));
    }

    @Test
    @StandardTierHubOnlyTest
    @ContinuousIntegrationTest
    public void receiveMessagesWithAmqpsSessionDrop() throws Exception
    {
        if (testInstance.protocol != AMQPS && testInstance.protocol != AMQPS_WS)
        {
            //test case not applicable
            return;
        }

        super.setupTest();
        this.errorInjectionTestFlow(ErrorInjectionHelper.amqpsSessionDropErrorInjectionMessage(DefaultDelayInSec, DefaultDurationInSec));
    }

    @Test
    @StandardTierHubOnlyTest
    @ContinuousIntegrationTest
    public void receiveMessagesWithAmqpsCBSReqLinkDrop() throws Exception
    {
        if (testInstance.protocol != AMQPS && testInstance.protocol != AMQPS_WS)
        {
            //test case not applicable
            return;
        }

        if (testInstance.authenticationType == SELF_SIGNED || testInstance.authenticationType == CERTIFICATE_AUTHORITY)
        {
            //cbs links aren't established in these scenarios, so it would be impossible/irrelevant if a cbs link dropped
            return;
        }

        super.setupTest();
        this.errorInjectionTestFlow(ErrorInjectionHelper.amqpsCBSReqLinkDropErrorInjectionMessage(DefaultDelayInSec, DefaultDurationInSec));
    }

    @Test
    @StandardTierHubOnlyTest
    @ContinuousIntegrationTest
    public void receiveMessagesWithAmqpsCBSRespLinkDrop() throws Exception
    {
        if (testInstance.protocol != AMQPS && testInstance.protocol != AMQPS_WS)
        {
            //test case not applicable
            return;
        }

        if (testInstance.authenticationType == SELF_SIGNED || testInstance.authenticationType == CERTIFICATE_AUTHORITY)
        {
            //cbs links aren't established in these scenarios, so it would be impossible/irrelevant if a cbs link dropped
            return;
        }

        super.setupTest();
        this.errorInjectionTestFlow(ErrorInjectionHelper.amqpsCBSRespLinkDropErrorInjectionMessage(DefaultDelayInSec, DefaultDurationInSec));
    }

    @Test
    @StandardTierHubOnlyTest
    @ContinuousIntegrationTest
    public void receiveMessagesWithAmqpsD2CLinkDrop() throws Exception
    {
        if (testInstance.protocol != AMQPS && testInstance.protocol != AMQPS_WS)
        {
            //test case not applicable
            return;
        }

        super.setupTest();
        this.errorInjectionTestFlow(ErrorInjectionHelper.amqpsD2CTelemetryLinkDropErrorInjectionMessage(DefaultDelayInSec, DefaultDurationInSec));
    }

    @Test
    @StandardTierHubOnlyTest
    @ContinuousIntegrationTest
    public void receiveMessagesWithAmqpsC2DLinkDrop() throws Exception
    {
        if (testInstance.protocol != AMQPS && testInstance.protocol != AMQPS_WS)
        {
            //test case not applicable
            return;
        }

        if (testInstance.authenticationType != SAS)
        {
            //TODO X509 case never seems to get callback about the connection dying. Needs investigation because this should pass, but doesn't
            return;
        }

        super.setupTest();
        this.errorInjectionTestFlow(ErrorInjectionHelper.amqpsC2DLinkDropErrorInjectionMessage(DefaultDelayInSec, DefaultDurationInSec));
    }

    @Test
    @StandardTierHubOnlyTest
    public void receiveMessagesWithGracefulShutdownAmqp() throws Exception
    {
        if (testInstance.protocol != AMQPS && testInstance.protocol != AMQPS_WS)
        {
            //test case not applicable
            return;
        }

        super.setupTest();
        this.errorInjectionTestFlow(ErrorInjectionHelper.amqpsGracefulShutdownErrorInjectionMessage(DefaultDelayInSec, DefaultDurationInSec));
    }

    @Test
    @StandardTierHubOnlyTest
    public void receiveMessagesWithGracefulShutdownMqtt() throws Exception
    {
        if (testInstance.protocol != MQTT && testInstance.protocol != MQTT_WS)
        {
            //test case not applicable
            return;
        }

        super.setupTest();
        this.errorInjectionTestFlow(ErrorInjectionHelper.mqttGracefulShutdownErrorInjectionMessage(DefaultDelayInSec, DefaultDurationInSec));
    }

    public void errorInjectionTestFlow(com.microsoft.azure.sdk.iot.device.Message errorInjectionMessage) throws IOException, IotHubException, InterruptedException
    {
        List<Pair<IotHubConnectionStatus, Throwable>> connectionStatusUpdates = new ArrayList<>();
        testInstance.client.registerConnectionStatusChangeCallback(new IotHubConnectionStatusChangeCallback()
        {
            @Override
            public void execute(IotHubConnectionStatus status, IotHubConnectionStatusChangeReason statusChangeReason, Throwable throwable, Object callbackContext)
            {
                connectionStatusUpdates.add(new Pair<>(status, throwable));
            }
        }, null);

        com.microsoft.azure.sdk.iot.device.MessageCallback callback = new MessageCallback();

        if (testInstance.protocol == MQTT || testInstance.protocol == MQTT_WS)
        {
            callback = new MessageCallbackMqtt();
        }

        Success messageReceived = new Success();
        if (testInstance.client instanceof DeviceClient)
        {
            ((DeviceClient) testInstance.client).setMessageCallback(callback, messageReceived);
        }
        else if (testInstance.client instanceof ModuleClient)
        {
            ((ModuleClient) testInstance.client).setMessageCallback(callback, messageReceived);
        }

        try
        {
            testInstance.client.open();

            //error injection message is not guaranteed to be ack'd by service so it may be re-sent. By setting expiry time,
            // we ensure that error injection message isn't resent to service too many times. The message will still likely
            // be sent 3 or 4 times causing 3 or 4 disconnections, but the test should recover anyways.
            errorInjectionMessage.setExpiryTime(200);
            testInstance.client.sendEventAsync(errorInjectionMessage, new EventCallback(null), null);

            //wait to send the message because we want to ensure that the tcp connection drop happens beforehand and we
            // want the connection to be re-established before sending anything from service client
            IotHubServicesCommon.waitForStabilizedConnection(connectionStatusUpdates, ERROR_INJECTION_RECOVERY_TIMEOUT, testInstance.client);

            if (testInstance.client instanceof DeviceClient)
            {
                sendMessageToDevice(testInstance.identity.getDeviceId(), testInstance.protocol.toString());
            }
            else if (testInstance.client instanceof ModuleClient)
            {
                sendMessageToModule(testInstance.identity.getDeviceId(), ((Module) testInstance.identity).getId(), testInstance.protocol.toString());
            }

            waitForMessageToBeReceived(messageReceived, testInstance.protocol.toString());

            Thread.sleep(200);
        }
        finally
        {
            testInstance.client.closeNow();
        }

        assertTrue(buildExceptionMessage(testInstance.protocol + ", " + testInstance.authenticationType + ": Error Injection message did not cause service to drop TCP connection", testInstance.client), IotHubServicesCommon.actualStatusUpdatesContainsStatus(connectionStatusUpdates, IotHubConnectionStatus.DISCONNECTED_RETRYING));
    }
}
