package br.com.santabarbara.tv;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Quando o aparelho liga (ou o app é atualizado), abre a TV do hotel e liga o vigia. */
public class AoLigar extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent intent) {
        Vigia.retomar(c);
        Vigia.iniciar(c);
        Vigia.abrirApp(c);
    }
}
