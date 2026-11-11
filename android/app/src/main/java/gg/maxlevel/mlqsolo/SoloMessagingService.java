package gg.maxlevel.mlqsolo;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import com.google.firebase.messaging.RemoteMessage;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

// MLQ Solo's phone notifications. Urgent reminders (level 3: not started, overdue commissions) ring like an alarm and keep
// ringing until he looks (or 10 minutes pass), and can show over the lock screen. Reminders buzz hard; updates are quiet.
// Each one has the cat icon for its kind, and one-tap buttons where they help (Start Timer, Mark Done, Reply).
public class SoloMessagingService extends com.capacitorjs.plugins.pushnotifications.MessagingService {

    static final String CH_URGENT = "solo_urgent_2", CH_REMIND = "solo_remind_2", CH_CHAT = "solo_chat_2", CH_UPDATES = "solo_updates_2", CH_MONEY = "solo_money_1", CH_QUIET = "solo_quiet_1";

    // MLQ's own sounds: Marimba Duo for messages, Crystal Ping for money in, Glass Drop for the rest
    private static void sound(android.content.Context ctx, NotificationChannel c, int raw) {
        Uri u = Uri.parse("android.resource://" + ctx.getPackageName() + "/" + raw);
        c.setSound(u, new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage message) {
        super.onMessageReceived(message);                 // the app itself (when open) hears about it too
        Map<String, String> d = message.getData();
        if (d == null || !"1".equals(d.get("solo"))) return;
        if (MainActivity.inForeground) return;            // on screen: the app shows its own pop-up and plays its sound
        try {
            show(d);
        } catch (Throwable ignored) {
        }
    }

    private static String val(Map<String, String> d, String k, String def) {
        String v = d.get(k);
        return v == null || v.isEmpty() ? def : v;
    }

    static void channels(android.content.Context ctx) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm = ctx.getSystemService(NotificationManager.class);
        if (nm == null) return;
        // (a channel's sound can't change once made: the first version used the phone's alarm, so it's replaced)
        // (solo_remind_1 stays: the timer check-in from the app uses it)
        for (String old : new String[] { "solo_urgent_1", "solo_chat_1", "solo_updates_1" }) {
            try { nm.deleteNotificationChannel(old); } catch (Throwable ignored) { }
        }
        if (nm.getNotificationChannel(CH_URGENT) == null) {
            NotificationChannel c = new NotificationChannel(CH_URGENT, "Urgent Reminders", NotificationManager.IMPORTANCE_HIGH);
            c.setDescription("Not started, overdue commissions: an old-phone \"tring tring\" that keeps ringing until you look");
            // MLQ Solo's own ring (res/raw/solo_urgent.ogg), played on the alarm volume so silent mode doesn't hide it
            Uri alarm = Uri.parse("android.resource://" + ctx.getPackageName() + "/" + R.raw.solo_urgent);
            c.setSound(alarm, new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
            c.enableVibration(true);
            c.setVibrationPattern(new long[] { 0, 900, 300, 900, 300, 900, 300, 900 });
            c.enableLights(true);
            c.setLightColor(0xFFEF4444);
            c.setLockscreenVisibility(NotificationCompat.VISIBILITY_PUBLIC);
            c.setBypassDnd(true);
            nm.createNotificationChannel(c);
        }
        if (nm.getNotificationChannel(CH_REMIND) == null) {
            NotificationChannel c = new NotificationChannel(CH_REMIND, "Reminders", NotificationManager.IMPORTANCE_HIGH);
            c.setDescription("Time to start, deadlines, habits, the daily target");
            sound(ctx, c, R.raw.solo_notify);
            c.enableVibration(true);
            c.setVibrationPattern(new long[] { 0, 500, 250, 500, 250, 500 });
            c.enableLights(true);
            c.setLightColor(0xFF8B5CF6);
            c.setLockscreenVisibility(NotificationCompat.VISIBILITY_PUBLIC);
            nm.createNotificationChannel(c);
        }
        if (nm.getNotificationChannel("solo_remind_1") == null) {
            NotificationChannel c = new NotificationChannel("solo_remind_1", "Timer Check-In", NotificationManager.IMPORTANCE_HIGH);
            c.setDescription("Still working? while the timer runs");
            sound(ctx, c, R.raw.solo_notify);
            c.enableVibration(true);
            nm.createNotificationChannel(c);
        }
        if (nm.getNotificationChannel(CH_CHAT) == null) {
            NotificationChannel c = new NotificationChannel(CH_CHAT, "Messages", NotificationManager.IMPORTANCE_HIGH);
            c.setDescription("Chat between Hemank and Udbhav");
            sound(ctx, c, R.raw.solo_chat);
            c.enableVibration(true);
            c.setVibrationPattern(new long[] { 0, 250, 150, 250 });
            nm.createNotificationChannel(c);
        }
        if (nm.getNotificationChannel(CH_UPDATES) == null) {
            NotificationChannel c = new NotificationChannel(CH_UPDATES, "Updates", NotificationManager.IMPORTANCE_DEFAULT);
            c.setDescription("Workshop comments, partner updates");
            sound(ctx, c, R.raw.solo_notify);
            nm.createNotificationChannel(c);
        }
        if (nm.getNotificationChannel(CH_MONEY) == null) {
            NotificationChannel c = new NotificationChannel(CH_MONEY, "Money In", NotificationManager.IMPORTANCE_HIGH);
            c.setDescription("Payments and GameRush sales");
            sound(ctx, c, R.raw.solo_money);
            c.enableVibration(true);
            c.setVibrationPattern(new long[] { 0, 120, 80, 120 });
            nm.createNotificationChannel(c);
        }
        if (nm.getNotificationChannel(CH_QUIET) == null) {
            NotificationChannel c = new NotificationChannel(CH_QUIET, "Night (Silent)", NotificationManager.IMPORTANCE_LOW);
            c.setDescription("Anything that arrives during quiet hours: shown, no sound");
            nm.createNotificationChannel(c);
        }
    }

    // the cat picture for each kind of notice (the same pictures as the Windows notifications)
    private Bitmap kindIcon(String kind) {
        String k = kind == null ? "" : kind;
        String name = "alert";
        if (k.startsWith("start") || k.equals("fresh") || k.equals("stall") || k.equals("carry")) name = "start";
        else if (k.startsWith("due") || k.equals("overdue")) name = "due";
        else if (k.startsWith("h") && (k.equals("habits") || k.equals("hwin") || k.equals("hlast") || k.equals("habit"))) name = "habit";
        else if (k.equals("money") || k.equals("unpaid")) name = "money";
        else if (k.startsWith("goal") || k.startsWith("pace")) name = "goal";
        else if (k.startsWith("target") || k.equals("win") || k.equals("bday")) name = "win";
        else if (k.equals("chat") || k.equals("comment")) name = "comment";
        else if (k.equals("guard")) name = "guard";
        else if (k.startsWith("ws") || k.equals("steam") || k.equals("star")) name = "steam";
        else if (k.equals("report") || k.startsWith("week")) name = "report";
        else if (k.equals("pause")) name = "pause";
        int id = getResources().getIdentifier("ic_k_" + name, "drawable", getPackageName());
        if (id == 0) id = getResources().getIdentifier("ic_k_alert", "drawable", getPackageName());
        return id == 0 ? null : BitmapFactory.decodeResource(getResources(), id);
    }

    private PendingIntent link(String tag, String act, String go, int req) {
        Uri.Builder b = Uri.parse("mlqsolo://open").buildUpon();
        if (act != null && !act.isEmpty()) b.appendQueryParameter("act", act);
        if (go != null && !go.isEmpty() && !"{}".equals(go) && !"null".equals(go)) b.appendQueryParameter("go", go);
        b.appendQueryParameter("ntag", tag);
        Intent open = new Intent(Intent.ACTION_VIEW, b.build(), this, MainActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(this, req, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private void show(Map<String, String> d) {
        if (Build.VERSION.SDK_INT >= 33
            && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return;
        channels(this);
        String title = val(d, "title", "MLQ Solo");
        String body = val(d, "body", "");
        String kind = val(d, "kind", "alert");
        String go = val(d, "go", "");
        int level = 1;
        try { level = Integer.parseInt(val(d, "level", "1")); } catch (Exception ignored) { }
        String ch = val(d, "channel", "updates");
        String channel = "urgent".equals(ch) ? CH_URGENT : "remind".equals(ch) ? CH_REMIND : "chat".equals(ch) ? CH_CHAT : "quiet".equals(ch) ? CH_QUIET : CH_UPDATES;
        if ("money".equals(kind) && level < 3 && !CH_QUIET.equals(channel)) channel = CH_MONEY;
        String tag = val(d, "tag", "solo-" + System.currentTimeMillis());
        int req = tag.hashCode();

        NotificationCompat.Builder b = new NotificationCompat.Builder(this, channel)
            .setSmallIcon(R.drawable.ic_stat_solo)
            .setColor(level >= 3 ? 0xFFEF4444 : 0xFF8B5CF6)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(link(tag, "", go, req))
            .setWhen(System.currentTimeMillis())
            .setShowWhen(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(level >= 2 || "chat".equals(kind) ? NotificationCompat.PRIORITY_MAX : NotificationCompat.PRIORITY_DEFAULT)
            .setCategory("chat".equals(kind) ? NotificationCompat.CATEGORY_MESSAGE : level >= 3 ? NotificationCompat.CATEGORY_ALARM : NotificationCompat.CATEGORY_REMINDER);
        Bitmap big = kindIcon(kind);
        if (big != null) b.setLargeIcon(big);

        // one-tap buttons
        try {
            JSONArray acts = new JSONArray(val(d, "actions", "[]"));
            for (int i = 0; i < acts.length() && i < 2; i++) {
                JSONObject a = acts.getJSONObject(i);
                String id = a.optString("id", ""), label = a.optString("label", "");
                if (id.isEmpty() || label.isEmpty()) continue;
                b.addAction(0, label, link(tag, id, go, req + 1 + i));
            }
        } catch (Exception ignored) { }

        if (level >= 3 && !CH_QUIET.equals(channel)) {
            // urgent: over the lock screen when Android allows it, and the alarm keeps ringing until it's looked at
            b.setFullScreenIntent(link(tag, "", go, req + 7), true);
            b.setTimeoutAfter(10 * 60 * 1000L);
            android.app.Notification n = b.build();
            n.flags |= android.app.Notification.FLAG_INSISTENT;
            NotificationManagerCompat.from(this).notify(tag, 1, n);
            return;
        }
        NotificationManagerCompat.from(this).notify(tag, 1, b.build());
    }
}
