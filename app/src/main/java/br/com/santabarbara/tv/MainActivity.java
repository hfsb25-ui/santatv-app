package br.com.santabarbara.tv;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;

import org.json.JSONObject;

/**
 * TV Santa Bárbara.
 * A tela (menus, canais, avisos) é a página web do hotel. O vídeo toca no player
 * nativo do Android (ExoPlayer), posicionado por cima da área de vídeo da página.
 */
public class MainActivity extends Activity {

    private static final String UA = "SantaBarbaraTV/1.0 (Android)";

    private FrameLayout root;
    private WebView web;
    private PlayerView playerView;
    private ExoPlayer player;
    private LinearLayout offline;
    private TextView offlineText;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private String homeUrl;
    private boolean pageFailed = false;
    private boolean syncLive = false;
    private boolean synced = false;
    private String currentUrl = null;

    // ------------------------------------------------------------------ ciclo de vida

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        SharedPreferences prefs = getSharedPreferences("tv", MODE_PRIVATE);
        homeUrl = prefs.getString("url", BuildConfig.HOME_URL);

        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        setContentView(root);

        // --- página da TV
        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        s.setUserAgentString(s.getUserAgentString() + " " + UA);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        web.setBackgroundColor(Color.BLACK);
        web.addJavascriptInterface(new Ponte(), "TVApp");
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView v, String url, android.graphics.Bitmap f) { pageFailed = false; }

            @Override
            public void onPageFinished(WebView v, String url) {
                if (!pageFailed) { offline.setVisibility(View.GONE); web.requestFocus(); }
            }

            @Override
            public void onReceivedError(WebView v, WebResourceRequest req, WebResourceError err) {
                if (req.isForMainFrame()) mostrarSemConexao();
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest req) {
                // A TV só navega dentro do próprio site.
                String host = req.getUrl().getHost();
                return host == null || !host.equals(Uri.parse(homeUrl).getHost());
            }
        });
        root.addView(web, new FrameLayout.LayoutParams(-1, -1));

        // --- player de vídeo
        playerView = new PlayerView(this);
        playerView.setUseController(false);
        playerView.setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING);
        playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
        playerView.setShutterBackgroundColor(Color.BLACK);
        playerView.setBackgroundColor(Color.BLACK);
        playerView.setFocusable(false);
        playerView.setFocusableInTouchMode(false);
        playerView.setVisibility(View.GONE);
        root.addView(playerView, new FrameLayout.LayoutParams(1, 1));

        // --- tela "sem conexão"
        offline = new LinearLayout(this);
        offline.setOrientation(LinearLayout.VERTICAL);
        offline.setGravity(Gravity.CENTER);
        offline.setBackgroundColor(Color.rgb(18, 32, 26));
        TextView titulo = new TextView(this);
        titulo.setText("Hotel Fazenda Santa Bárbara");
        titulo.setTextColor(Color.rgb(227, 176, 75));
        titulo.setTextSize(30);
        titulo.setGravity(Gravity.CENTER);
        offlineText = new TextView(this);
        offlineText.setTextColor(Color.rgb(238, 241, 230));
        offlineText.setTextSize(18);
        offlineText.setGravity(Gravity.CENTER);
        offlineText.setPadding(0, 24, 0, 0);
        offline.addView(titulo);
        offline.addView(offlineText);
        offline.setVisibility(View.GONE);
        root.addView(offline, new FrameLayout.LayoutParams(-1, -1));

        criarPlayer();
        Vigia.iniciar(this);
        web.loadUrl(homeUrl);
        esconderBarras();
    }

    private void criarPlayer() {
        DefaultHttpDataSource.Factory http = new DefaultHttpDataSource.Factory()
                .setUserAgent(UA)
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(12000)
                .setReadTimeoutMs(12000);
        player = new ExoPlayer.Builder(this)
                .setMediaSourceFactory(new DefaultMediaSourceFactory(this).setDataSourceFactory(http))
                .build();
        player.setPlayWhenReady(true);
        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_READY) {
                    if (syncLive && !synced) {
                        synced = true;
                        long dur = player.getDuration();
                        if (dur != C.TIME_UNSET && dur > 0) player.seekTo(System.currentTimeMillis() % dur);
                    }
                    avisarPagina("playing", "");
                }
            }

            @Override
            public void onPlayerError(PlaybackException e) {
                if (e.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                    player.seekToDefaultPosition();
                    player.prepare();
                    return;
                }
                playerView.setVisibility(View.GONE);
                avisarPagina("error", e.getErrorCodeName());
            }
        });
        playerView.setPlayer(player);
    }

    private void mostrarSemConexao() {
        pageFailed = true;
        pararVideo();
        offlineText.setText("Sem conexão com a internet.\nTentando de novo…");
        offline.setVisibility(View.VISIBLE);
        ui.removeCallbacksAndMessages(null);
        ui.postDelayed(() -> web.loadUrl(homeUrl), 10000);
    }

    private void avisarPagina(String tipo, String detalhe) {
        String js = "window.tvAppEvent&&window.tvAppEvent(" + JSONObject.quote(tipo) + "," + JSONObject.quote(detalhe) + ")";
        web.post(() -> web.evaluateJavascript(js, null));
    }

    private void posicionar(float x, float y, float w, float h) {
        int W = web.getWidth(), H = web.getHeight();
        if (W == 0 || H == 0) return;
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(Math.max(1, Math.round(w * W)), Math.max(1, Math.round(h * H)));
        lp.leftMargin = Math.round(x * W);
        lp.topMargin = Math.round(y * H);
        playerView.setLayoutParams(lp);
    }

    private void pararVideo() {
        currentUrl = null;
        if (player != null) { player.stop(); player.clearMediaItems(); }
        playerView.setVisibility(View.GONE);
    }

    private void esconderBarras() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
    }

    @Override
    public void onWindowFocusChanged(boolean f) { super.onWindowFocusChanged(f); if (f) esconderBarras(); }

    @Override
    protected void onResume() {
        super.onResume();
        web.onResume();
        Vigia.retomar(this); // voltou para a TV: o vigia volta a funcionar
        if (player != null && currentUrl != null) player.play();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (player != null) player.pause();
        web.onPause();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        // Apertou Home: volta para o início da TV.
        super.onNewIntent(intent);
        tecla("Home");
    }

    @Override
    protected void onDestroy() {
        if (player != null) player.release();
        web.destroy();
        super.onDestroy();
    }

    // ------------------------------------------------------------------ controle remoto

    private void tecla(String key) {
        String js = "document.dispatchEvent(new KeyboardEvent('keydown',{key:" + JSONObject.quote(key) + ",bubbles:true}))";
        web.evaluateJavascript(js, null);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        int k = e.getKeyCode();
        if (k == KeyEvent.KEYCODE_BACK || k == KeyEvent.KEYCODE_CHANNEL_UP || k == KeyEvent.KEYCODE_CHANNEL_DOWN
                || k == KeyEvent.KEYCODE_MENU) {
            if (e.getAction() == KeyEvent.ACTION_UP) {
                if (offline.getVisibility() == View.VISIBLE) { web.loadUrl(homeUrl); return true; }
                if (k == KeyEvent.KEYCODE_BACK) tecla("Escape");
                else if (k == KeyEvent.KEYCODE_CHANNEL_UP) tecla("ChannelUp");
                else if (k == KeyEvent.KEYCODE_CHANNEL_DOWN) tecla("ChannelDown");
                else tecla("Home");
            }
            return true; // o hóspede nunca sai do app pelo botão Voltar
        }
        if (!web.hasFocus()) web.requestFocus();
        return super.dispatchKeyEvent(e);
    }

    // ------------------------------------------------------------------ ponte com a página

    private class Ponte {
        @JavascriptInterface
        public String version() { return BuildConfig.VERSION_NAME; }

        @JavascriptInterface
        public void play(String url, float x, float y, float w, float h, boolean loop, boolean sync) {
            ui.post(() -> {
                posicionar(x, y, w, h);
                playerView.setVisibility(View.VISIBLE);
                if (url.equals(currentUrl)) { player.play(); return; }
                currentUrl = url;
                syncLive = sync;
                synced = false;
                MediaItem.Builder b = new MediaItem.Builder().setUri(url);
                String path = Uri.parse(url).getPath();
                path = path == null ? "" : path.toLowerCase();
                if (path.endsWith(".m3u8") || path.endsWith(".m3u") || path.contains(".m3u8")) b.setMimeType(MimeTypes.APPLICATION_M3U8);
                player.setRepeatMode(loop ? Player.REPEAT_MODE_ONE : Player.REPEAT_MODE_OFF);
                player.setMediaItem(b.build());
                player.prepare();
                player.play();
            });
        }

        @JavascriptInterface
        public String vigia() {
            return "{\"uso\":" + Vigia.temAcessoUso(MainActivity.this) + ",\"sobrepor\":" + Vigia.podeSobrepor(MainActivity.this) + "}";
        }

        @JavascriptInterface
        public void retomarVigia() { Vigia.retomar(MainActivity.this); }

        @JavascriptInterface
        public void setRect(float x, float y, float w, float h) {
            ui.post(() -> posicionar(x, y, w, h));
        }

        @JavascriptInterface
        public void stop() { ui.post(MainActivity.this::pararVideo); }

        @JavascriptInterface
        public void openSettings() {
            ui.post(() -> {
                pararVideo();
                Vigia.pausar(MainActivity.this, 15 * 60 * 1000L); // 15 min para a equipe mexer no aparelho
                Intent i = new Intent(Settings.ACTION_SETTINGS);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try { startActivity(i); } catch (Exception ignored) { }
            });
        }

        @JavascriptInterface
        public void setHomeUrl(String url) {
            if (url == null || !(url.startsWith("http://") || url.startsWith("https://"))) return;
            getSharedPreferences("tv", MODE_PRIVATE).edit().putString("url", url).apply();
            homeUrl = url;
            ui.post(() -> web.loadUrl(homeUrl));
        }
    }
}
