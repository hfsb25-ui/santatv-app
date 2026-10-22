package br.com.santabarbara.tv;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

/**
 * Quando o aparelho liga (ou o app é atualizado), abre a TV do hotel e liga o vigia.
 * Escuta também o aviso mais cedo da inicialização (LOCKED_BOOT_COMPLETED), porque no
 * Fire TV o BOOT_COMPLETED pode chegar bem depois da tela da Amazon aparecer.
 */
public class AoLigar extends BroadcastReceiver {
    private static long ultimo = 0;

    @Override
    public void onReceive(Context c, Intent intent) {
        long agora = System.currentTimeMillis();
        if (agora - ultimo < 4000) return;   // já tratado pelo outro aviso
        ultimo = agora;
        try { Vigia.retomar(c); } catch (Exception ignored) { }  // pode falhar antes do desbloqueio
        Vigia.iniciar(c);
        Vigia.abrirApp(c, false);
        // Insiste mais algumas vezes: a tela da Amazon pode abrir por cima logo depois.
        Context app = c.getApplicationContext();
        Handler h = new Handler(Looper.getMainLooper());
        for (long d : new long[]{3000, 8000}) h.postDelayed(() -> Vigia.abrirApp(app, false), d);
    }
}
