package com.sanfrancisco.cantina;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintManager;
import android.view.Gravity;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.net.URI;

public class MainActivity extends Activity {
    private static final String PREFS = "cantina_prefs";
    private static final String KEY_SERVER = "server_url";
    private SharedPreferences prefs;
    private WebView webView;
    private TextView serverLabel;
    private TextView statusLabel;
    private String serverUrl = "";

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        serverUrl = prefs.getString(KEY_SERVER, "");

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(242,243,247));

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(12),dp(8),dp(8),dp(8));
        bar.setBackgroundColor(Color.rgb(16,39,61));

        LinearLayout titleWrap = new LinearLayout(this);
        titleWrap.setOrientation(LinearLayout.VERTICAL);
        titleWrap.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT,1f));
        TextView title = new TextView(this);
        title.setText("CANTINA · Fiesta Patronal");
        title.setTextColor(Color.WHITE);
        title.setTextSize(17);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        serverLabel = new TextView(this);
        serverLabel.setTextColor(Color.rgb(207,220,232));
        serverLabel.setTextSize(11);
        statusLabel = new TextView(this);
        statusLabel.setTextColor(Color.rgb(241,207,112));
        statusLabel.setTextSize(10);
        titleWrap.addView(title);
        titleWrap.addView(serverLabel);
        titleWrap.addView(statusLabel);
        bar.addView(titleWrap);

        Button reload = topButton("↻");
        reload.setOnClickListener(v -> webView.reload());
        bar.addView(reload);
        Button settings = topButton("⚙");
        settings.setOnClickListener(v -> showServerDialog(false));
        bar.addView(settings);

        webView = new WebView(this);
        webView.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1f));
        WebSettings ws = webView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setDatabaseEnabled(true);
        ws.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView,true);
        webView.addJavascriptInterface(new AndroidBridge(), "AndroidBridge");
        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return serverUrl == null || !request.getUrl().toString().startsWith(serverUrl);
            }
            @Override public void onPageFinished(WebView view, String url) {
                statusLabel.setText("Conectado");
                injectCantinaMode();
            }
            @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) statusLabel.setText("Sin conexión al servidor");
            }
        });

        root.addView(bar);
        root.addView(webView);
        setContentView(root);
        updateServerLabel();
        if (serverUrl == null || serverUrl.trim().isEmpty()) showServerDialog(true); else loadServer();
    }

    private Button topButton(String text) {
        Button b = new Button(this);
        b.setText(text); b.setTextSize(20); b.setTextColor(Color.WHITE); b.setBackgroundColor(Color.TRANSPARENT);
        b.setMinWidth(dp(48)); b.setMinHeight(dp(44));
        return b;
    }

    private void showServerDialog(boolean required) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("Ej.: 192.168.1.50:8080");
        input.setText(serverUrl == null ? "" : serverUrl.replace("http://","").replace("https://",""));
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle("Servidor de la Cantina")
                .setMessage("Ingrese la IP de la notebook que ejecuta el Sistema Fiesta Patronal. Ambos equipos deben estar en la misma red Wi‑Fi.")
                .setView(input)
                .setPositiveButton("Conectar", null);
        if (!required) builder.setNegativeButton("Cancelar", null);
        AlertDialog dialog = builder.create();
        dialog.setCancelable(!required);
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String normalized = normalizeServer(input.getText().toString());
            if (normalized.isEmpty()) { input.setError("Ingrese una dirección válida"); return; }
            serverUrl = normalized;
            prefs.edit().putString(KEY_SERVER, serverUrl).apply();
            updateServerLabel();
            dialog.dismiss();
            loadServer();
        }));
        dialog.show();
    }

    private String normalizeServer(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) return "";
        if (!value.startsWith("http://") && !value.startsWith("https://")) value = "http://" + value;
        try {
            URI uri = URI.create(value);
            if (uri.getHost() == null) return "";
            int port = uri.getPort();
            String scheme = uri.getScheme() == null ? "http" : uri.getScheme();
            if (port < 0 && "http".equalsIgnoreCase(scheme)) port = 8080;
            return scheme + "://" + uri.getHost() + (port > 0 ? ":" + port : "");
        } catch (Exception e) { return ""; }
    }

    private void updateServerLabel() { serverLabel.setText(serverUrl == null || serverUrl.isEmpty() ? "Servidor no configurado" : serverUrl); }
    private void loadServer() { statusLabel.setText("Conectando…"); webView.loadUrl(serverUrl + "/"); }

    private void injectCantinaMode() {
        String js = "(function(){try{" +
                "if(!document.getElementById('androidCantinaStyle')){var s=document.createElement('style');s.id='androidCantinaStyle';" +
                "s.textContent='.sidebar{display:none!important}.app{grid-template-columns:1fr!important}.page{padding:8px!important}.product-grid-pos{grid-template-columns:repeat(auto-fill,minmax(120px,1fr))!important;gap:8px!important}.product-tile{min-height:150px!important}.product-tile img{height:85px!important;object-fit:contain!important}@media(max-width:820px){.pos-layout{grid-template-columns:1fr!important}.product-grid-pos{grid-template-columns:repeat(3,minmax(90px,1fr))!important}}';document.head.appendChild(s);} " +
                "window.print=function(){if(window.AndroidBridge)AndroidBridge.printCurrent();};" +
                "var f=function(){var app=document.getElementById('app');if(app&&!app.classList.contains('hidden')&&typeof openSection==='function'){openSection('canteenSale');var t=document.getElementById('pageTitle');if(t)t.textContent='Cantina · Venta de producto';}};f();setTimeout(f,500);setTimeout(f,1400);" +
                "}catch(e){}})();";
        webView.evaluateJavascript(js, null);
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    @Override public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack(); else super.onBackPressed();
    }

    public class AndroidBridge {
        @JavascriptInterface public void printCurrent() {
            runOnUiThread(() -> {
                try {
                    PrintManager pm = (PrintManager)getSystemService(Context.PRINT_SERVICE);
                    PrintDocumentAdapter adapter = webView.createPrintDocumentAdapter("Ticket Cantina");
                    PrintAttributes attrs = new PrintAttributes.Builder()
                            .setMediaSize(PrintAttributes.MediaSize.UNKNOWN_PORTRAIT)
                            .setColorMode(PrintAttributes.COLOR_MODE_MONOCHROME)
                            .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                            .build();
                    pm.print("Ticket Cantina", adapter, attrs);
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this,"No se pudo abrir la impresión",Toast.LENGTH_LONG).show();
                }
            });
        }
        @JavascriptInterface public void configureServer() { runOnUiThread(() -> showServerDialog(false)); }
    }
}
