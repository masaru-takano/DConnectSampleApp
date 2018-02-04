package jp.gclue.deviceconnect.android.app.sample;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v4.content.LocalBroadcastManager;

import org.deviceconnect.message.DConnectEventMessage;
import org.deviceconnect.message.DConnectMessage;
import org.deviceconnect.message.DConnectResponseMessage;
import org.deviceconnect.message.DConnectSDK;
import org.deviceconnect.message.DConnectSDKFactory;
import org.deviceconnect.profile.ServiceDiscoveryProfileConstants;
import org.deviceconnect.profile.ServiceInformationProfileConstants;

import java.util.List;
import java.util.logging.Logger;


public class SampleService extends Service {

    static final String ACTION_EVENT = "jp.gclue.deviceconnect.android.app.sample.action.EVENT";
    static final String EXTRA_EVENT = "event";

    private DConnectSDK mSDK;

    private Thread mThread;

    private final DConnectSDK.OnEventListener mEventListener = new DConnectSDK.OnEventListener() {
        @Override
        public void onMessage(final DConnectEventMessage event) {
            log("OnEventListener.onMessage");
            notifyEvent(event);
        }

        @Override
        public void onResponse(final DConnectResponseMessage response) {
            log("DConnectResponseMessage.onResponse");
        }
    };

    private final Logger mLogger = Logger.getLogger("Sample");

    @Override
    public void onCreate() {
        super.onCreate();

        mSDK = DConnectSDKFactory.create(getApplicationContext(), DConnectSDKFactory.Type.HTTP);
        mSDK.setOrigin(getPackageName());

        mThread = new Thread(new Runnable() {
            @Override
            public void run() {
                Device targetDevice = acquireTargetService("deviceOrientation");
                log("Target device is found: serviceId = " + targetDevice.getName());

                openWebSocket();
                requestEvent(targetDevice);
            }
        });
        mThread.start();
    }

    private Device acquireTargetService(final String profileName) {
        waitManagerStart();
        log("Manager is available.");

        while (true) {
            DConnectResponseMessage response = mSDK.serviceDiscovery();
            if (response.getResult() != DConnectMessage.RESULT_OK) {
                continue;
            }
            List<Object> services = response.getList(ServiceDiscoveryProfileConstants.PARAM_SERVICES);
            if (services != null) {
                for (Object obj : services) {
                    if (obj instanceof DConnectMessage) {
                        DConnectMessage service = (DConnectMessage) obj;
                        String serviceId = service.getString(ServiceDiscoveryProfileConstants.PARAM_ID);
                        String name = service.getString(ServiceDiscoveryProfileConstants.PARAM_NAME);
                        if (serviceId != null && name != null) {
                            if (isSupported(serviceId, profileName)) {
                                return new Device(serviceId, name);
                            }
                        }
                    }
                }
            }
        }
    }

    private void waitManagerStart() {
        while (true) {
            DConnectResponseMessage response = mSDK.availability();
            if (response.getResult() == DConnectMessage.RESULT_OK) {
                break;
            }
        }
    }

    private void openWebSocket() {
        mSDK.connectWebSocket(new DConnectSDK.OnWebSocketListener() {
            @Override
            public void onOpen() {
                log("OnWebSocketListener.onOpen");
            }

            @Override
            public void onClose() {
                log("OnWebSocketListener.onClose");
            }

            @Override
            public void onError(final Exception e) {
                log("OnWebSocketListener.onError: " + e.getMessage());
            }
        });
    }

    private boolean isSupported(final String serviceId, final String profileName) {
        DConnectResponseMessage response = mSDK.getServiceInformation(serviceId);
        if (response.getResult() == DConnectMessage.RESULT_OK) {
            List<Object> supports = response.getList(ServiceInformationProfileConstants.PARAM_SUPPORTS);
            if (supports != null) {
                for (Object obj : supports) {
                    if (obj instanceof String) {
                        String scope = (String) obj;
                        if (profileName.equals(scope)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private void requestEvent(final Device device) {
        DConnectSDK.URIBuilder uriBuilder = mSDK.createURIBuilder();
        uriBuilder.setServiceId(device.getId());
        uriBuilder.setProfile("deviceOrientation");
        uriBuilder.setAttribute("onDeviceOrientation");
        mSDK.addEventListener(uriBuilder.build(), mEventListener);
    }

    private void notifyEvent(final DConnectEventMessage event) {
        Intent intent = new Intent(ACTION_EVENT);
        intent.putExtra(EXTRA_EVENT, event);
        sendLocalBroadcast(intent);
    }

    private void sendLocalBroadcast(final Intent intent) {
        LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);
    }

    private void log(final String message) {
        mLogger.info(message);
    }

    @Nullable
    @Override
    public IBinder onBind(final Intent intent) {
        return null;
    }

    private static class Device {
        private final String mServiceId;
        private final String mName;

        Device(final String serviceId, final String name) {
            mServiceId = serviceId;
            mName = name;
        }

        public String getId() {
            return mServiceId;
        }

        public String getName() {
            return mName;
        }
    }
}
