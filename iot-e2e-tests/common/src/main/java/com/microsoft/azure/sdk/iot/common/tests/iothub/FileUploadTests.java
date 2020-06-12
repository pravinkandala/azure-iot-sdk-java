/*
 *  Copyright (c) Microsoft. All rights reserved.
 *  Licensed under the MIT license. See LICENSE file in the project root for full license information.
 */

package com.microsoft.azure.sdk.iot.common.tests.iothub;

import com.microsoft.azure.sdk.iot.common.helpers.Tools;
import com.microsoft.azure.sdk.iot.common.helpers.*;
import com.microsoft.azure.sdk.iot.device.*;
import com.microsoft.azure.sdk.iot.service.*;
import com.microsoft.azure.sdk.iot.service.auth.AuthenticationType;
import com.microsoft.azure.sdk.iot.service.exceptions.IotHubException;
import org.junit.*;
import org.littleshoot.proxy.HttpProxyServer;
import org.littleshoot.proxy.impl.DefaultHttpProxyServer;

import javax.net.ssl.SSLContext;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.microsoft.azure.sdk.iot.common.helpers.CorrelationDetailsLoggingAssert.buildExceptionMessage;
import static com.microsoft.azure.sdk.iot.common.tests.iothub.FileUploadTests.STATUS.FAILURE;
import static com.microsoft.azure.sdk.iot.common.tests.iothub.FileUploadTests.STATUS.SUCCESS;
import static com.microsoft.azure.sdk.iot.device.IotHubStatusCode.OK;
import static com.microsoft.azure.sdk.iot.device.IotHubStatusCode.OK_EMPTY;
import static junit.framework.TestCase.fail;
import static org.junit.Assert.*;

/**
 * Test class containing all tests to be run on JVM and android pertaining to FileUpload. Class needs to be extended
 * in order to run these tests as that extended class handles setting connection strings and certificate generation
 */
public class FileUploadTests extends IotHubIntegrationTest
{
    // Max time to wait to see it on Hub
    private static final long MAXIMUM_TIME_TO_WAIT_FOR_IOTHUB = 180000; // 3 minutes
    private static final long FILE_UPLOAD_QUEUE_POLLING_INTERVAL = 4000; // 4 sec
    private static final long MAXIMUM_TIME_TO_WAIT_FOR_CALLBACK = 5000; // 5 sec

    //Max time to wait before timing out test
    private static final long MAX_MILLISECS_TIMEOUT_KILL_TEST = MAXIMUM_TIME_TO_WAIT_FOR_IOTHUB + 50000; // 50 secs

    //Max devices to test
    private static final Integer MAX_FILES_TO_UPLOAD = 5;

    // remote name of the file
    private static final String REMOTE_FILE_NAME = "File";
    private static final String REMOTE_FILE_NAME_EXT = ".txt";

    protected static String iotHubConnectionString = "";

    // States of SDK
    private static RegistryManager registryManager;
    private static ServiceClient serviceClient;
    private static FileUploadNotificationReceiver fileUploadNotificationReceiver;

    private static String publicKeyCertificate;
    private static String privateKeyCertificate;
    private static String x509Thumbprint;

    protected static HttpProxyServer proxyServer;
    protected static String testProxyHostname = "127.0.0.1";
    protected static int testProxyPort = 8897;
    protected static final String testProxyUser = "proxyUsername";
    protected static final char[] testProxyPass = "1234".toCharArray();

    static Set<FileUploadNotification> activeFileUploadNotifications = new ConcurrentSkipListSet<>(new Comparator<FileUploadNotification>()
    {
        @Override
        public int compare(FileUploadNotification o1, FileUploadNotification o2) {
            if (!o1.getDeviceId().equals(o2.getDeviceId()))
            {
                return -1;
            }

            if (!o1.getBlobName().equals(o2.getBlobName()))
            {
                return -1;
            }

            if (!o1.getBlobSizeInBytes().equals(o2.getBlobSizeInBytes()))
            {
                return -1;
            }

            if (!o1.getBlobUri().equals(o2.getBlobUri()))
            {
                return -1;
            }

            if (!o1.getEnqueuedTimeUtcDate().equals(o2.getEnqueuedTimeUtcDate()))
            {
                return -1;
            }

            if (!o1.getLastUpdatedTimeDate().equals(o2.getLastUpdatedTimeDate()))
            {
                return -1;
            }

            return 0;
        }
    });
    static Thread fileUploadNotificationListenerThread;
    static AtomicBoolean hasFileUploadNotificationReceiverThreadFailed = new AtomicBoolean(false);
    static Exception fileUploadNotificationReceiverThreadException = null;

    public static Collection inputs() throws Exception
    {
        X509CertificateGenerator certificateGenerator = new X509CertificateGenerator();
        return inputs(certificateGenerator.getPublicCertificate(), certificateGenerator.getPrivateKey(), certificateGenerator.getX509Thumbprint());
    }

    public static Collection inputs(String publicK, String privateK, String thumbprint) throws Exception
    {
        registryManager = RegistryManager.createFromConnectionString(iotHubConnectionString);

        serviceClient = ServiceClient.createFromConnectionString(iotHubConnectionString, IotHubServiceClientProtocol.AMQPS);
        serviceClient.open();

        publicKeyCertificate = publicK;
        privateKeyCertificate = privateK;
        x509Thumbprint = thumbprint;

        return Arrays.asList(
                new Object[][]
                        {
                                //without proxy
                                {IotHubClientProtocol.HTTPS, AuthenticationType.SAS, false},
                                {IotHubClientProtocol.HTTPS, AuthenticationType.SELF_SIGNED, false},
                        });
    }

    public FileUploadTests(IotHubClientProtocol protocol, AuthenticationType authenticationType, boolean withProxy) throws InterruptedException, IOException, IotHubException, URISyntaxException
    {
        this.testInstance = new FileUploadTestInstance(protocol, authenticationType, withProxy);
    }

    public FileUploadTestInstance testInstance;

    public class FileUploadTestInstance
    {
        public IotHubClientProtocol protocol;
        public AuthenticationType authenticationType;
        private FileUploadState[] fileUploadState;
        private MessageState[] messageStates;
        private boolean withProxy;

        public FileUploadTestInstance(IotHubClientProtocol protocol, AuthenticationType authenticationType, boolean withProxy) throws InterruptedException, IOException, IotHubException, URISyntaxException
        {
            this.protocol = protocol;
            this.authenticationType = authenticationType;
            this.withProxy = withProxy;
        }
    }

    enum STATUS
    {
        SUCCESS, FAILURE
    }

    private static class FileUploadState
    {
        String blobName;
        InputStream fileInputStream;
        long fileLength;
        boolean isCallBackTriggered;
        STATUS fileUploadStatus;
        STATUS fileUploadNotificationReceived;
    }

    private static class MessageState
    {
        String messageBody;
        STATUS messageStatus;
    }

    @Test (timeout = MAX_MILLISECS_TIMEOUT_KILL_TEST)
    public void uploadToBlobAsyncSingleFile() throws URISyntaxException, IOException, InterruptedException, IotHubException, GeneralSecurityException
    {
        serviceClient.open();
        FileUploadNotificationReceiver receiver = serviceClient.getFileUploadNotificationReceiver();
        receiver.open();
        receiver.receive(10000);
    }
}
