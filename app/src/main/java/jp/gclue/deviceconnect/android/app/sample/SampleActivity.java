package jp.gclue.deviceconnect.android.app.sample;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.deviceconnect.message.DConnectEventMessage;
import org.deviceconnect.message.DConnectMessage;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.logging.Logger;


/**
 * {@link SampleService} の取得したデータを表示する画面.
 */
public class SampleActivity extends AppCompatActivity implements Constants {

    /**
     * リクエストするAPIのパス名.
     */
    private static final String EVENT_API_PATH = "/gotapi/deviceOrientation/onDeviceOrientation"; // 加速度センサー値

    /**
     * {@link SampleService} からのブロードキャストを受信するリスナー.
     *
     * ブロードキャストの中に、Device Web API Managerからのイベントが格納されている.
     */
    private final BroadcastReceiver mLocalBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, final Intent intent) {
            if (ACTION_NOTIFY_WAITING_MANAGER.equals(intent.getAction())) {
                log("Waiting until Device Web API Manager become available...");
            } else if (ACTION_NOTIFY_MANAGER_AVAILABLE.equals(intent.getAction())) {
                log("Device Web API Manager is available.");
            } else if (ACTION_NOTIFY_WAITING_SERVICE.equals(intent.getAction())) {
                log("Waiting until service which supports '" + EVENT_API_PATH + "'");
            } else if (ACTION_NOTIFY_SERVICE_AVAILABLE.equals(intent.getAction())) {
                String name = intent.getStringExtra(EXTRA_SERVICE_NAME);
                log("Service is available: name = " + name);
            } else if (ACTION_NOTIFY_EVENT.equals(intent.getAction())) {
                DConnectEventMessage event = (DConnectEventMessage) intent.getSerializableExtra(SampleService.EXTRA_EVENT);

                // PUT /gotapi/deviceOrientation/onDeviceOrientation で定義されているイベントの内容を解析.
                DConnectMessage orientation = event.getMessage("orientation");
                DConnectMessage acceleration = orientation.getMessage("accelerationIncludingGravity");
                float x = acceleration.getFloat("x");
                float y = acceleration.getFloat("y");
                float z = acceleration.getFloat("z");

                log("x = " + String.format(Locale.ENGLISH, "%.2f", x) + ", " +
                        "y = " + String.format(Locale.ENGLISH, "%.2f", y) + ", " +
                        "z = " + String.format(Locale.ENGLISH, "%.2f", z));
            }
        }
    };

    /**
     * {@link SampleService} からブロードキャストを受信するためのフィルター設定.
     */
    private final IntentFilter mIntentFilter;
    {
        mIntentFilter = new IntentFilter();
        mIntentFilter.addAction(ACTION_NOTIFY_EVENT);
        mIntentFilter.addAction(ACTION_NOTIFY_WAITING_MANAGER);
        mIntentFilter.addAction(ACTION_NOTIFY_MANAGER_AVAILABLE);
        mIntentFilter.addAction(ACTION_NOTIFY_WAITING_SERVICE);
        mIntentFilter.addAction(ACTION_NOTIFY_SERVICE_AVAILABLE);
    }

    /**
     * ロガー.
     */
    private final Logger mLogger = Logger.getLogger("Sample");

    /**
     * アプリ内部のブロードキャスト機能を提供するオブジェクト.
     */
    private LocalBroadcastManager mLocalBroadcast;

    /**
     * 通信ログを表示するビュー.
     */
    private LinearLayout mLogView;

    private SimpleDateFormat mDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SS", Locale.JAPAN);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mLocalBroadcast = LocalBroadcastManager.getInstance(getApplicationContext());
        mLogView = findViewById(R.id.log_view);
    }

    @Override
    protected void onResume() {
        super.onResume();

        mLocalBroadcast.registerReceiver(mLocalBroadcastReceiver, mIntentFilter);

        Intent intent = new Intent(getApplicationContext(), SampleService.class);
        intent.setAction(ACTION_REQUEST_EVENT);
        intent.putExtra(EXTRA_PATH, EVENT_API_PATH);
        startService(intent);
    }

    @Override
    protected void onPause() {
        mLocalBroadcast.unregisterReceiver(mLocalBroadcastReceiver);

        Intent intent = new Intent(getApplicationContext(), SampleService.class);
        stopService(intent);

        super.onPause();
    }

    private synchronized void log(final String message) {
        // 画面上にログを追加.
        int count = mLogView.getChildCount();
        if (count >= 100) {
            mLogView.removeViewAt(count - 1);
        }
        mLogView.addView(createView(currentTime() + ": " + message), 0);

        mLogger.info(message);
    }

    private View createView(final String message) {
        TextView view = new TextView(getApplicationContext());
        view.setText(message);
        view.setTextColor(getResources().getColor(R.color.black));
        view.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        return view;
    }

    private String currentTime() {
        return mDateFormat.format(new Date());
    }
}
