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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;


/**
 * Device Web API Managerからデバイスのデータを取得するクラス.
 */
public class SampleService extends Service {

    static final String ACTION_REQUEST_EVENT = "jp.gclue.deviceconnect.android.app.sample.action.REQUEST_EVENT";

    static final String EXTRA_PATH = "path";

    static final String ACTION_NOTIFY_EVENT = "jp.gclue.deviceconnect.android.app.sample.action.EVENT";

    static final String EXTRA_EVENT = "event";

    /**
     * Device Connect SDK for Androidのインスタンス.
     */
    private DConnectSDK mSDK;

    /**
     * Device Web API Managerと通信するスレッド.
     */
    private ExecutorService mExecutors = Executors.newFixedThreadPool(4);

    /**
     * Device Web API Managerからのイベントを受信するリスナー.
     * イベントを受信した祭、{@link SampleActivity} へそのまま転送する.
     */
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

        // SDKの初期化.
        mSDK = DConnectSDKFactory.create(getApplicationContext(), DConnectSDKFactory.Type.HTTP);
        mSDK.setOrigin(getPackageName());
    }

    @Override
    public void onDestroy() {
        mExecutors.shutdown();
        mExecutors = null;

        super.onDestroy();
    }

    @Override
    public int onStartCommand(final Intent intent, final int flags, final int startId) {
        if (intent != null) {
            if (ACTION_REQUEST_EVENT.equals(intent.getAction())) {
                final String path = intent.getStringExtra(EXTRA_PATH);
                if (path != null) {
                    mExecutors.execute(new Runnable() {
                        @Override
                        public void run() {
                            // Device Web API Managerの起動を待機.
                            waitManagerStart();
                            log("Manager is available.");

                            // 指定されたAPIをサポートするサービスが見つかるまで待機.
                            Device targetDevice = acquireTargetService(DConnectPath.parse(path));
                            if (targetDevice != null) {
                                log("Target device is found: serviceId = " + targetDevice.getName());

                                // WebSocket接続.
                                connectWebSocket();

                                // イベント開始要求.
                                requestEvent(targetDevice);
                            }
                        }
                    });
                }
            }
        }
        return START_STICKY;
    }

    /**
     * ServiceDiscoveryを実行し、使用したいデバイスの情報をサービス一覧より取得する.
     *
     * サービス一覧に使用したいデバイスが含まれていない場合、サービス一覧に追加されるまでスレッドをブロックする.
     * （ただし、ブロック中に割り込みが入った場合は、null を返す.）
     *
     * @param path リクエストパス
     * @return デバイス情報
     */
    private Device acquireTargetService(final DConnectPath path) {
        try {
            while (!Thread.interrupted()) {
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
                                if (isSupported(serviceId, path)) {
                                    return new Device(serviceId, name);
                                }
                            }
                        }
                    }
                }

                Thread.sleep(500);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // 割り込み状態の復元
        }
        return null;
    }

    /**
     * Device Web API Managerが起動するまでスレッドをブロックする.
     */
    private void waitManagerStart() {
        while (true) {
            DConnectResponseMessage response = mSDK.availability();
            if (response.getResult() == DConnectMessage.RESULT_OK) {
                break;
            }
        }
    }

    /**
     * Device Web API ManagerのWebSocketサーバに接続する.
     */
    private void connectWebSocket() {
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

    private boolean isSupported(final String serviceId, final DConnectPath path) {
        DConnectResponseMessage response = mSDK.getServiceInformation(serviceId);
        if (response.getResult() == DConnectMessage.RESULT_OK) {
            DConnectMessage supportApis = response.getMessage(ServiceInformationProfileConstants.PARAM_SUPPORT_APIS);
            if (supportApis != null) {
                DConnectMessage profileDefinition = supportApis.getMessage(path.getProfileName());
                if (profileDefinition != null) {
                    DConnectMessage paths = profileDefinition.getMessage("paths");
                    DConnectMessage apiDefinition = paths.getMessage(path.getSubPath());
                    return apiDefinition != null;
                }
            }
        }
        return false;
    }

    /**
     * Device Web API Managerに対してイベント登録要求を送信する.
     *
     * @param device デバイス情報
     */
    private void requestEvent(final Device device) {
        DConnectSDK.URIBuilder uriBuilder = mSDK.createURIBuilder();
        uriBuilder.setServiceId(device.getId());
        uriBuilder.setProfile("deviceOrientation");
        uriBuilder.setAttribute("onDeviceOrientation");
        uriBuilder.addParameter("interval", "500");
        mSDK.addEventListener(uriBuilder.build(), mEventListener);
    }

    /**
     * Device Web API Managerから通知されたイベントをサンプルアプリ内でブロードキャストする.
     *
     * 実際には、{@link SampleActivity} に対して転送される.
     *
     * @param event イベント
     */
    private void notifyEvent(final DConnectEventMessage event) {
        Intent intent = new Intent(ACTION_NOTIFY_EVENT);
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

    private static class DConnectPath {
        private final String mApiName;
        private final String mProfileName;
        private final String mInterfaceName;
        private final String mAttributeName;

        DConnectPath(final String apiName, final String profileName,
                     final String interfaceName, final String attributeName) {
            mApiName = apiName;
            mProfileName = profileName;
            mInterfaceName = interfaceName;
            mAttributeName = attributeName;
        }

        public String getApiName() {
            return mApiName;
        }

        public String getProfileName() {
            return mProfileName;
        }

        public String getInterfaceName() {
            return mInterfaceName;
        }

        public String getAttributeName() {
            return mAttributeName;
        }

        public String getSubPath() {
            if (mInterfaceName != null) {
                return "/" + mInterfaceName + "/" + mAttributeName;
            } else {
                return "/" + mAttributeName;
            }
        }

        static DConnectPath parse(final String path) {
            if (!path.startsWith("/")) {
                return null;
            }
            final String[] array = path.split("/");
            if (array.length < 3 || array.length > 5) {
                return null;
            }
            final String apiName = array[1];
            final String profileName = array[2];
            final String interfaceName;
            final String attributeName;
            switch (array.length) {
                case 3:
                    interfaceName = null;
                    attributeName = null;
                    break;
                case 4:
                    interfaceName = null;
                    attributeName = array[3];
                    break;
                case 5:
                    interfaceName = array[3];
                    attributeName = array[4];
                    break;
                default:
                    return null;
            }
            return new DConnectPath(apiName, profileName, interfaceName, attributeName);
        }
    }
}
