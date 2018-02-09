package jp.gclue.deviceconnect.android.app.sample;

/**
 * 定数定義.
 *
 * 主に {@link SampleService} と {@link SampleActivity} の間で
 * やり取りするメッセージについて定義する.
 */
public interface Constants {

    String ACTION_REQUEST_EVENT = "jp.gclue.deviceconnect.android.app.sample.action.REQUEST_EVENT";

    String EXTRA_PATH = "path";

    String ACTION_NOTIFY_WAITING_MANAGER = "jp.gclue.deviceconnect.android.app.sample.action.WAITING_MANAGER";

    String ACTION_NOTIFY_MANAGER_AVAILABLE = "jp.gclue.deviceconnect.android.app.sample.action.MANAGER_AVAILABLE";

    String ACTION_NOTIFY_WAITING_SERVICE = "jp.gclue.deviceconnect.android.app.sample.action.WAITING_SERVICE";

    String ACTION_NOTIFY_SERVICE_AVAILABLE = "jp.gclue.deviceconnect.android.app.sample.action.SERVICE_AVAILABLE";

    String EXTRA_SERVICE_NAME = "serviceName";

    String ACTION_NOTIFY_EVENT = "jp.gclue.deviceconnect.android.app.sample.action.EVENT";

    String EXTRA_EVENT = "event";

}
