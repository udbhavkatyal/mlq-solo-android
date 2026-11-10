package gg.maxlevel.mlqsolo;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.core.app.NotificationManagerCompat;

import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    // MLQ Solo on screen: its own pop-up shows new notices, so the phone's notification stays quiet
    public static volatile boolean inForeground = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(MlqFiles.class);          // app updates: download inside the app, then Android's installer
        super.onCreate(savedInstanceState);
        SoloMessagingService.channels(this);     // the channels exist from the start, so Settings can show them
        clearFrom(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        clearFrom(intent);
    }

    // a notification's button was tapped: take that notification away (an urgent one would otherwise keep ringing)
    private void clearFrom(Intent intent) {
        try {
            Uri u = intent == null ? null : intent.getData();
            if (u == null || !"mlqsolo".equals(u.getScheme())) return;
            String tag = u.getQueryParameter("ntag");
            if (tag != null && !tag.isEmpty()) NotificationManagerCompat.from(this).cancel(tag, 1);
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        inForeground = true;
    }

    @Override
    public void onPause() {
        inForeground = false;
        super.onPause();
    }
}
