package br.com.santabarbara.tv;

import android.app.AppOpsManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Process;
import android.provider.Settings;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * "Vigia": em aparelhos onde não dá para trocar a tela inicial (Fire TV),
 * percebe quando a tela inicial do fabricante abriu e traz a TV do hotel de volta.
 * Precisa das permissões "acesso ao uso" e "sobrepor apps", liberadas pelo script de configuração.
 */
public class Vigia extends Service {

    static final Set<String> TELAS_INICIAIS = new HashSet<>(Arrays.asList(
            "com.amazon.tv.launcher",
            "com.amazon.firebat",
            "com.amazon.tv.leanbacklauncher",
            "com.google.android.tvlauncher",
            "com.google.android.apps.tv.launcherx",
            "com.google.android.leanbacklauncher"
    ));

    private final Handler h = new Handler(Looper.getMainLooper());
    private String ultimoPlano = null;
    private long ultimaAbertura = 0;

    static void iniciar(Context c) {
        Intent i = new Intent(c, Vigia.class);
        try {
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i); else c.startService(i);
        } catch (Exception ignored) { }
    }

    static boolean temAcessoUso(Context c) {
        try {
            AppOpsManager ops = (AppOpsManager) c.getSystemService(APP_OPS_SERVICE);
            int m = ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.getPackageName());
            return m == AppOpsManager.MODE_ALLOWED;
        } catch (Exception e) { return false; }
    }

    static boolean podeSobrepor(Context c) {
        return Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(c);
    }

    static void pausar(Context c, long ms) {
        c.getSharedPreferences("tv", MODE_PRIVATE).edit().putLong("vigia_pausa", System.currentTimeMillis() + ms).apply();
    }

    static void retomar(Context c) {
        c.getSharedPreferences("tv", MODE_PRIVATE).edit().putLong("vigia_pausa", 0).apply();
    }

    private boolean pausado() {
        try {
            return getSharedPreferences("tv", MODE_PRIVATE).getLong("vigia_pausa", 0) > System.currentTimeMillis();
        } catch (Exception e) { return false; }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            nm.createNotificationChannel(new NotificationChannel("vigia", "TV do hotel", NotificationManager.IMPORTANCE_MIN));
            Notification n = new Notification.Builder(this, "vigia")
                    .setContentTitle("TV Santa Bárbara").setSmallIcon(R.mipmap.ic_launcher).build();
            startForeground(1, n);
        }
        h.removeCallbacksAndMessages(null);
        h.post(tick);
        return START_STICKY;
    }

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            try { verificar(); } catch (Exception ignored) { }
            h.postDelayed(this, 500);
        }
    };

    private void verificar() {
        if (!temAcessoUso(this)) return;
        UsageStatsManager usm = (UsageStatsManager) getSystemService(USAGE_STATS_SERVICE);
        long agora = System.currentTimeMillis();
        UsageEvents ev = usm.queryEvents(agora - 10000, agora);
        UsageEvents.Event e = new UsageEvents.Event();
        while (ev.hasNextEvent()) {
            ev.getNextEvent(e);
            if (e.getEventType() == UsageEvents.Event.MOVE_TO_FOREGROUND) ultimoPlano = e.getPackageName();
        }
        if (ultimoPlano == null || pausado()) return;
        if (TELAS_INICIAIS.contains(ultimoPlano) && agora - ultimaAbertura > 1500) {
            ultimaAbertura = agora;
            abrirApp(this);
        }
    }

    static void abrirApp(Context c) {
        Intent i = new Intent(c, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        try { c.startActivity(i); } catch (Exception ignored) { }
    }

    @Override
    public void onDestroy() { h.removeCallbacksAndMessages(null); super.onDestroy(); }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
