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
        getWindow().setStatusBarColor(Color.rgb(16, 39, 61));
        getWindow().setNavigationBarColor(Color.rgb(16, 39, 61));

        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        serverUrl = prefs.getString(KEY_SERVER, "");

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(242, 243, 247));

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(10), dp(6), dp(4), dp(6));
        bar.setBackgroundColor(Color.rgb(16, 39, 61));

        LinearLayout titleWrap = new LinearLayout(this);
        titleWrap.setOrientation(LinearLayout.VERTICAL);
        titleWrap.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView title = new TextView(this);
        title.setText("CANTINA · Fiesta Patronal");
        title.setTextColor(Color.WHITE);
        title.setTextSize(16);
        title.setMaxLines(1);
        title.setTypeface(null, android.graphics.Typeface.BOLD);

        serverLabel = new TextView(this);
        serverLabel.setTextColor(Color.rgb(207, 220, 232));
        serverLabel.setTextSize(10);
        serverLabel.setMaxLines(1);

        statusLabel = new TextView(this);
        statusLabel.setTextColor(Color.rgb(241, 207, 112));
        statusLabel.setTextSize(10);
        statusLabel.setMaxLines(1);

        titleWrap.addView(title);
        titleWrap.addView(serverLabel);
        titleWrap.addView(statusLabel);
        bar.addView(titleWrap);

        Button reload = topButton("↻");
        reload.setContentDescription("Recargar");
        reload.setOnClickListener(v -> webView.reload());
        bar.addView(reload);

        Button settings = topButton("⚙");
        settings.setContentDescription("Configurar servidor");
        settings.setOnClickListener(v -> showServerDialog(false));
        bar.addView(settings);

        webView = new WebView(this);
        webView.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        webView.setBackgroundColor(Color.rgb(242, 243, 247));
        webView.setHorizontalScrollBarEnabled(false);
        webView.setVerticalScrollBarEnabled(true);
        webView.setOverScrollMode(WebView.OVER_SCROLL_NEVER);

        WebSettings ws = webView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setDatabaseEnabled(true);
        ws.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        ws.setUseWideViewPort(true);
        ws.setLoadWithOverviewMode(false);
        ws.setSupportZoom(false);
        ws.setBuiltInZoomControls(false);
        ws.setDisplayZoomControls(false);
        ws.setTextZoom(100);

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);
        webView.addJavascriptInterface(new AndroidBridge(), "AndroidBridge");
        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return serverUrl == null || !request.getUrl().toString().startsWith(serverUrl);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                statusLabel.setText("Conectado · modo vertical");
                injectCantinaMode();
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) statusLabel.setText("Sin conexión al servidor");
            }
        });

        root.addView(bar);
        root.addView(webView);
        setContentView(root);
        updateServerLabel();

        if (serverUrl == null || serverUrl.trim().isEmpty()) {
            showServerDialog(true);
        } else {
            loadServer();
        }
    }

    private Button topButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(19);
        b.setTextColor(Color.WHITE);
        b.setBackgroundColor(Color.TRANSPARENT);
        b.setMinWidth(dp(46));
        b.setMinHeight(dp(46));
        b.setPadding(dp(5), 0, dp(5), 0);
        return b;
    }

    private void showServerDialog(boolean required) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setTextSize(16);
        input.setHint("Ej.: 192.168.1.50:8080");
        input.setText(serverUrl == null ? "" : serverUrl.replace("http://", "").replace("https://", ""));

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
            if (normalized.isEmpty()) {
                input.setError("Ingrese una dirección válida");
                return;
            }
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
        } catch (Exception e) {
            return "";
        }
    }

    private void updateServerLabel() {
        serverLabel.setText(serverUrl == null || serverUrl.isEmpty() ? "Servidor no configurado" : serverUrl);
    }

    private void loadServer() {
        statusLabel.setText("Conectando…");
        webView.loadUrl(serverUrl + "/");
    }

    private void injectCantinaMode() {
        String css =
                "html,body{width:100%!important;max-width:100%!important;overflow-x:hidden!important;-webkit-text-size-adjust:100%!important;}" +
                "body{margin:0!important;}" +
                "*,*:before,*:after{box-sizing:border-box!important;}" +
                ".sidebar{display:none!important;}" +
                ".app{grid-template-columns:minmax(0,1fr)!important;width:100%!important;max-width:100%!important;}" +
                ".main,.content,.page{width:100%!important;max-width:100%!important;min-width:0!important;}" +
                ".page{padding:clamp(8px,2.4vw,16px)!important;}" +
                ".page-header,.section-header{gap:8px!important;flex-wrap:wrap!important;}" +
                ".pos-layout{grid-template-columns:minmax(0,1fr)!important;gap:10px!important;width:100%!important;}" +
                ".product-grid-pos{display:grid!important;grid-template-columns:repeat(2,minmax(0,1fr))!important;gap:8px!important;width:100%!important;}" +
                ".product-tile{min-width:0!important;min-height:0!important;width:100%!important;padding:8px!important;border-radius:12px!important;overflow:hidden!important;touch-action:manipulation!important;}" +
                ".product-tile img{display:block!important;width:100%!important;height:auto!important;aspect-ratio:1/1!important;max-height:none!important;object-fit:contain!important;margin:0 auto 5px!important;}" +
                ".product-tile .name,.product-tile .title,.product-name{font-size:clamp(12px,3.2vw,15px)!important;line-height:1.2!important;overflow-wrap:anywhere!important;}" +
                ".product-tile .price,.product-price{font-size:clamp(13px,3.5vw,16px)!important;font-weight:700!important;}" +
                "button,.btn,input,select,textarea{font-size:16px!important;max-width:100%!important;}" +
                "button,.btn{min-height:46px!important;touch-action:manipulation!important;}" +
                "input,select,textarea{min-height:46px!important;}" +
                ".grid-2,.grid-3,.form-grid,.form-row{min-width:0!important;}" +
                "table{max-width:100%!important;}" +
                ".table-wrap,.table-responsive{width:100%!important;max-width:100%!important;overflow-x:auto!important;-webkit-overflow-scrolling:touch!important;}" +
                ".modal,.modal-card,.dialog,.card{max-width:100%!important;}" +
                ".modal-card,.dialog{max-height:92vh!important;overflow:auto!important;}" +
                ".pos-summary,.sale-summary,.checkout-card,.payment-panel{position:relative!important;width:100%!important;max-width:100%!important;}" +
                "@media(max-width:380px){.page{padding:7px!important}.product-grid-pos{grid-template-columns:repeat(2,minmax(0,1fr))!important;gap:6px!important}.product-tile{padding:6px!important}.grid-2,.grid-3,.form-grid{grid-template-columns:1fr!important}}" +
                "@media(min-width:381px) and (max-width:539px){.product-grid-pos{grid-template-columns:repeat(2,minmax(0,1fr))!important}.grid-2,.grid-3,.form-grid{grid-template-columns:1fr!important}}" +
                "@media(min-width:540px) and (max-width:719px){.product-grid-pos{grid-template-columns:repeat(3,minmax(0,1fr))!important}}" +
                "@media(min-width:720px) and (max-width:959px){.product-grid-pos{grid-template-columns:repeat(4,minmax(0,1fr))!important}}" +
                "@media(min-width:960px){.product-grid-pos{grid-template-columns:repeat(5,minmax(0,1fr))!important}.page{padding:18px!important}}";

        String escapedCss = css.replace("\\", "\\\\").replace("'", "\\'");

        String js = "(function(){try{" +
                "var vp=document.querySelector('meta[name=viewport]');" +
                "if(!vp){vp=document.createElement('meta');vp.name='viewport';document.head.appendChild(vp);}" +
                "vp.setAttribute('content','width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no,viewport-fit=cover');" +
                "document.documentElement.classList.add('android-portrait');" +
                "var s=document.getElementById('androidCantinaResponsiveStyle');" +
                "if(!s){s=document.createElement('style');s.id='androidCantinaResponsiveStyle';document.head.appendChild(s);}" +
                "s.textContent='" + escapedCss + "';" +
                "window.print=function(){if(window.AndroidBridge)AndroidBridge.printCurrent();};" +
                "var f=function(){var app=document.getElementById('app');if(app&&!app.classList.contains('hidden')&&typeof openSection==='function'){openSection('canteenSale');var t=document.getElementById('pageTitle');if(t)t.textContent='Cantina · Venta de producto';}};" +
                "f();setTimeout(f,450);setTimeout(f,1200);" +
                "}catch(e){}})();";

        webView.evaluateJavascript(js, null);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    public class AndroidBridge {
        @JavascriptInterface
        public void printCurrent() {
            runOnUiThread(() -> {
                try {
                    PrintManager pm = (PrintManager) getSystemService(Context.PRINT_SERVICE);
                    PrintDocumentAdapter adapter = webView.createPrintDocumentAdapter("Ticket Cantina");
                    PrintAttributes attrs = new PrintAttributes.Builder()
                            .setMediaSize(PrintAttributes.MediaSize.UNKNOWN_PORTRAIT)
                            .setColorMode(PrintAttributes.COLOR_MODE_MONOCHROME)
                            .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                            .build();
                    pm.print("Ticket Cantina", adapter, attrs);
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "No se pudo abrir la impresión", Toast.LENGTH_LONG).show();
                }
            });
        }

        @JavascriptInterface
        public void configureServer() {
            runOnUiThread(() -> showServerDialog(false));
        }
    }
}
