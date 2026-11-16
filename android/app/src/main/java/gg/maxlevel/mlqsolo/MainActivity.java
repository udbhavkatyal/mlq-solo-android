package gg.maxlevel.mlqsolo;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import android.content.ClipData;
import android.util.Base64;
import android.webkit.WebView;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.view.ContentInfoCompat;
import androidx.core.view.ViewCompat;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    // MLQ Solo on screen: its own pop-up shows new notices, so the phone's notification stays quiet
    public static volatile boolean inForeground = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(MlqFiles.class);          // app updates: download inside the app, then Android's installer
        registerPlugin(MlqAuth.class);           // the sign-in, shared with quick replies from chat notifications
        super.onCreate(savedInstanceState);
        SoloMessagingService.channels(this);     // the channels exist from the start, so Settings can show them
        clearFrom(getIntent());
        keyboardContent();
    }

    // GIFs and stickers from the keyboard (Gboard): the message box says it takes pictures, and what the keyboard sends
    // is handed to the chat screen, which sends it like a photo (animated GIFs stay animated)
    private void keyboardContent() {
        try {
            WebView wv = getBridge() == null ? null : getBridge().getWebView();
            if (wv == null) return;
            ViewCompat.setOnReceiveContentListener(wv, new String[] { "image/*" }, (view, payload) -> {
                android.util.Pair<ContentInfoCompat, ContentInfoCompat> split = payload.partition(item -> item.getUri() != null);
                ContentInfoCompat pics = split.first;
                if (pics != null) {
                    ClipData clip = pics.getClip();
                    for (int i = 0; i < clip.getItemCount(); i++) {
                        Uri u = clip.getItemAt(i).getUri();
                        String type = getContentResolver().getType(u);
                        if (type == null && clip.getDescription().getMimeTypeCount() > 0) type = clip.getDescription().getMimeType(0);
                        if (type == null || !type.startsWith("image/")) continue;
                        try (InputStream is = getContentResolver().openInputStream(u)) {
                            if (is == null) continue;
                            ByteArrayOutputStream bo = new ByteArrayOutputStream();
                            byte[] b = new byte[16384]; int n; long total = 0;
                            while ((n = is.read(b)) > 0) { bo.write(b, 0, n); total += n; if (total > 6 * 1024 * 1024) break; }
                            if (total > 6 * 1024 * 1024) continue;
                            String data = "data:" + type + ";base64," + Base64.encodeToString(bo.toByteArray(), Base64.NO_WRAP);
                            String js = "window.dispatchEvent(new CustomEvent('solo-kbd',{detail:{type:" + org.json.JSONObject.quote(type) + ",data:" + org.json.JSONObject.quote(data) + "}}))";
                            wv.post(() -> wv.evaluateJavascript(js, null));
                        } catch (Exception ignored) {
                        }
                    }
                }
                return split.second;
            });
        } catch (Throwable ignored) {
        }
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
        SoloMessagingService.clearChat(this);    // opened: the conversation is read
    }

    @Override
    public void onPause() {
        inForeground = false;
        super.onPause();
    }
}
