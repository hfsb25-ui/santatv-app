package br.com.santabarbara.tv;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/** Apps de streaming que podem aparecer no menu da TV do hotel. */
class Apps {

    // nome, pacotes possíveis (Android TV, Fire TV…)
    static final String[][] CONHECIDOS = {
            {"Netflix", "com.netflix.ninja", "com.netflix.mediaclient"},
            {"YouTube", "com.google.android.youtube.tv", "com.amazon.firetv.youtube"},
            {"YouTube Kids", "com.google.android.youtube.tvkids", "com.amazon.firetv.youtube.kids"},
            {"Prime Video", "com.amazon.amazonvideo.livingroom", "com.amazon.avod"},
            {"Globoplay", "com.globo.globotv"},
            {"Disney+", "com.disney.disneyplus"},
            {"Max", "com.wbd.stream", "com.hbo.hbonow"},
            {"Pluto TV", "tv.pluto.android"},
            {"Spotify", "com.spotify.tv.android"},
    };

    static Intent abrirIntent(Context c, String pkg) {
        PackageManager pm = c.getPackageManager();
        Intent i = null;
        if (Build.VERSION.SDK_INT >= 21) i = pm.getLeanbackLaunchIntentForPackage(pkg);
        if (i == null) i = pm.getLaunchIntentForPackage(pkg);
        return i;
    }

    static String listar(Context c) {
        JSONArray out = new JSONArray();
        PackageManager pm = c.getPackageManager();
        for (String[] app : CONHECIDOS) {
            for (int k = 1; k < app.length; k++) {
                String pkg = app[k];
                if (abrirIntent(c, pkg) == null) continue;
                try {
                    ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
                    Drawable d = null;
                    try { d = pm.getApplicationBanner(ai); } catch (Exception ignored) { }
                    boolean banner = d != null;
                    if (d == null) d = pm.getApplicationIcon(ai);
                    JSONObject o = new JSONObject();
                    o.put("nome", app[0]);
                    o.put("pkg", pkg);
                    o.put("banner", banner);
                    o.put("icone", "data:image/png;base64," + png(d, banner ? 320 : 160, banner ? 180 : 160));
                    out.put(o);
                } catch (Exception ignored) { }
                break; // um pacote por app basta
            }
        }
        return out.toString();
    }

    private static String png(Drawable d, int w, int h) {
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b);
        d.setBounds(0, 0, w, h);
        d.draw(cv);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        b.compress(Bitmap.CompressFormat.PNG, 100, bos);
        return Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP);
    }

    static boolean abrir(Context c, String pkg) {
        Intent i = abrirIntent(c, pkg);
        if (i == null) return false;
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try { c.startActivity(i); return true; } catch (Exception e) { return false; }
    }

    static ComponentName admin(Context c) { return new ComponentName(c, Admin.class); }

    static boolean ehAdministrador(Context c) {
        DevicePolicyManager dpm = (DevicePolicyManager) c.getSystemService(Context.DEVICE_POLICY_SERVICE);
        return dpm != null && dpm.isDeviceOwnerApp(c.getPackageName());
    }

    /** Apaga os dados (e o login) dos apps de streaming instalados. Só funciona como administrador do aparelho. */
    static String limpar(Context c, Runnable fim) {
        JSONObject r = new JSONObject();
        try {
            boolean adm = ehAdministrador(c) && Build.VERSION.SDK_INT >= 28;
            r.put("administrador", adm);
            int n = 0;
            if (adm) {
                DevicePolicyManager dpm = (DevicePolicyManager) c.getSystemService(Context.DEVICE_POLICY_SERVICE);
                AtomicInteger pendentes = new AtomicInteger();
                for (String[] app : CONHECIDOS)
                    for (int k = 1; k < app.length; k++) {
                        String pkg = app[k];
                        try { c.getPackageManager().getApplicationInfo(pkg, 0); } catch (Exception e) { continue; }
                        pendentes.incrementAndGet(); n++;
                        dpm.clearApplicationUserData(admin(c), pkg, Executors.newSingleThreadExecutor(),
                                (p, ok) -> { if (pendentes.decrementAndGet() == 0 && fim != null) fim.run(); });
                    }
            }
            r.put("apps", n);
        } catch (Exception e) {
            try { r.put("erro", String.valueOf(e.getMessage())); } catch (Exception ignored) { }
        }
        return r.toString();
    }

    static void deixarDeSerAdministrador(Context c) {
        try {
            DevicePolicyManager dpm = (DevicePolicyManager) c.getSystemService(Context.DEVICE_POLICY_SERVICE);
            if (dpm != null && dpm.isDeviceOwnerApp(c.getPackageName())) dpm.clearDeviceOwnerApp(c.getPackageName());
        } catch (Exception ignored) { }
    }
}
