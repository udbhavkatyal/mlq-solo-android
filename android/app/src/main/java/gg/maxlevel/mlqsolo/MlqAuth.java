package gg.maxlevel.mlqsolo;

import android.content.Context;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import org.json.JSONObject;

// The app's sign-in, shared with the quick reply from a chat notification (which sends without opening the app).
// Capacitor.Plugins.MlqAuth.set({ url, key, access_token, refresh_token, expires_at, me, them, myName }) / get()
@CapacitorPlugin(name = "MlqAuth")
public class MlqAuth extends Plugin {
    static final String PREFS = "mlq_auth";

    static synchronized JSONObject read(Context c) {
        try {
            String s = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("auth", null);
            return s == null ? null : new JSONObject(s);
        } catch (Exception e) {
            return null;
        }
    }

    static synchronized void write(Context c, JSONObject o) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("auth", o.toString()).apply();
    }

    @PluginMethod
    public void set(PluginCall call) {
        try {
            JSONObject in = call.getData(), cur = read(getContext());
            // never go back to an older sign-in than the one a quick reply already renewed
            if (cur != null && cur.optLong("expires_at", 0) > in.optLong("expires_at", 0) && !cur.optString("refresh_token").equals(in.optString("refresh_token"))) {
                for (String k : new String[] { "url", "key", "me", "them", "myName" }) if (in.has(k)) cur.put(k, in.get(k));
                write(getContext(), cur);
            } else {
                write(getContext(), in);
            }
            call.resolve();
        } catch (Exception e) {
            call.reject(e.getMessage());
        }
    }

    @PluginMethod
    public void get(PluginCall call) {
        JSONObject o = read(getContext());
        JSObject r = new JSObject();
        r.put("auth", o == null ? "" : o.toString());
        call.resolve(r);
    }
}
