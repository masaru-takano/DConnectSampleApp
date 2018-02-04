package jp.gclue.deviceconnect.android.app.sample;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import org.deviceconnect.message.DConnectEventMessage;
import org.deviceconnect.message.DConnectMessage;

import java.util.logging.Logger;

public class SampleActivity extends AppCompatActivity {

    private LocalBroadcastManager mLocalBroadcast;

    private final Logger mLogger = Logger.getLogger("Sample");

    /**
     * SampleServiceの転送したイベントのレシーバー.
     */
    private BroadcastReceiver mLocalBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, final Intent intent) {
            if (SampleService.ACTION_EVENT.equals(intent.getAction())) {
                DConnectEventMessage event = (DConnectEventMessage) intent.getSerializableExtra(SampleService.EXTRA_EVENT);
                DConnectMessage orientation = event.getMessage("orientation");
                DConnectMessage acceleration = orientation.getMessage("accelerationIncludingGravity");
                float x = acceleration.getFloat("x");
                float y = acceleration.getFloat("y");
                float z = acceleration.getFloat("z");
                log("x = " + x + ", " +
                    "y = " + y + ", " +
                    "z = " + z + ", ");
            }
        }
    };

    private final IntentFilter mIntentFilter;
    {
        mIntentFilter = new IntentFilter();
        mIntentFilter.addAction(SampleService.ACTION_EVENT);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mLocalBroadcast = LocalBroadcastManager.getInstance(getApplicationContext());
    }

    @Override
    protected void onResume() {
        super.onResume();

        mLocalBroadcast.registerReceiver(mLocalBroadcastReceiver, mIntentFilter);

        Intent intent = new Intent(getApplicationContext(), SampleService.class);
        startService(intent);
    }

    @Override
    protected void onPause() {
        mLocalBroadcast.unregisterReceiver(mLocalBroadcastReceiver);

        Intent intent = new Intent(getApplicationContext(), SampleService.class);
        stopService(intent);

        super.onPause();
    }

    private void log(final String message) {
        mLogger.info(message);
    }
}
