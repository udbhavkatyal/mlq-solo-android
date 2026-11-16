package gg.maxlevel.mlqsolo;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import androidx.core.app.RemoteInput;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import org.json.JSONArray;
import org.json.JSONObject;

// Quick reply from a chat notification: sends the message straight to the cloud (no app opening), buzzes the other
// person's phone, and updates the notification to show what was sent.
public class ReplyReceiver extends BroadcastReceiver {
    static final String KEY_TEXT = "solo_reply";

    @Override
    public void onReceive(Context ctx, Intent intent) {
        Bundle in = RemoteInput.getResultsFromIntent(intent);
        CharSequence cs = in == null ? null : in.getCharSequence(KEY_TEXT);
        final String text = cs == null ? "" : cs.toString().trim();
        if (text.isEmpty()) return;
        final Context app = ctx.getApplicationContext();
        final PendingResult pr = goAsync();
        new Thread(() -> {
            boolean ok = false;
            try {
                ok = send(app, text);
            } catch (Throwable ignored) {
            }
            try {
                SoloMessagingService.afterReply(app, text, ok);
            } catch (Throwable ignored) {
            }
            pr.finish();
        }).start();
    }

    private static String http(String method, String url, String key, String bearer, String body, String prefer) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestMethod(method);
        c.setConnectTimeout(12000);
        c.setReadTimeout(15000);
        c.setRequestProperty("apikey", key);
        c.setRequestProperty("Content-Type", "application/json");
        if (bearer != null) c.setRequestProperty("Authorization", "Bearer " + bearer);
        if (prefer != null) c.setRequestProperty("Prefer", prefer);
        if (body != null) {
            c.setDoOutput(true);
            try (OutputStream o = c.getOutputStream()) { o.write(body.getBytes(StandardCharsets.UTF_8)); }
        }
        int code = c.getResponseCode();
        InputStream is = code < 400 ? c.getInputStream() : c.getErrorStream();
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        if (is != null) { byte[] b = new byte[4096]; int n; while ((n = is.read(b)) > 0) bo.write(b, 0, n); is.close(); }
        c.disconnect();
        if (code >= 400) throw new Exception("HTTP " + code + " " + bo.toString("UTF-8"));
        return bo.toString("UTF-8");
    }

    private static boolean send(Context ctx, String text) throws Exception {
        JSONObject a = MlqAuth.read(ctx);
        if (a == null) return false;
        String url = a.optString("url"), key = a.optString("key");
        if (url.isEmpty() || key.isEmpty()) return false;
        long now = System.currentTimeMillis();
        String token = a.optString("access_token");
        if (a.optLong("expires_at", 0) < now + 30000) {
            JSONObject b = new JSONObject(http("POST", url + "/auth/v1/token?grant_type=refresh_token", key, null,
                new JSONObject().put("refresh_token", a.optString("refresh_token")).toString(), null));
            token = b.getString("access_token");
            a.put("access_token", token);
            a.put("refresh_token", b.getString("refresh_token"));
            a.put("expires_at", now + (b.optLong("expires_in", 3600) - 60) * 1000);
            MlqAuth.write(ctx, a);
        }
        String me = a.optString("me", "hemank"), them = a.optString("them", "udbhav"), myName = a.optString("myName", me);
        String id = "msg:" + Long.toString(now, 36) + Long.toString((long) (Math.random() * 1679616), 36);
        JSONObject msg = new JSONObject().put("id", id).put("kind", "msg").put("from", me).put("to", them).put("text", text)
            .put("refs", new JSONArray()).put("files", new JSONArray()).put("at", now).put("updated_at", now).put("created_at", now).put("via", "notification");
        SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
        iso.setTimeZone(TimeZone.getTimeZone("UTC"));
        JSONObject row = new JSONObject().put("id", id).put("kind", "msg").put("data", msg).put("updated_at", iso.format(new Date(now))).put("deleted", false);
        http("POST", url + "/rest/v1/solo_items?on_conflict=user_id,id", key, token, new JSONArray().put(row).toString(), "resolution=merge-duplicates,return=minimal");
        // buzz their phone (their app decides whether to show it)
        try {
            JSONObject p = new JSONObject().put("to", them).put("title", myName).put("body", text).put("level", 2).put("kind", "chat").put("tag", "chat")
                .put("go", new JSONObject().put("view", "chat")).put("sender", myName)
                .put("actions", new JSONArray().put(new JSONObject().put("id", "reply").put("label", "Reply")));
            http("POST", url + "/functions/v1/solo-push", key, token, p.toString(), null);
        } catch (Throwable ignored) {
        }
        return true;
    }
}
