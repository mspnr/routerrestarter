package com.applikationsprogramvara.routerrestarter;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.CheckBox;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.preference.PreferenceManager;

import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class MainActivity extends AppCompatActivity {

    public static final int STEP1_INIT = 1;
    public static final int STEP2_LOGOUT_START = 2;
    public static final int STEP3_RELOAD_AFTER_LOGOUT = 3;
    public static final int STEP4_LOGIN_START = 4;
    public static final int STEP5_SWITCH_TO_RESTART = 5;
    public static final int STEP6_RESTART = 6;
    public static final int STEP7_TRIGGERED = 7;
    public static final int PROCESSING_ABORTED = 9;

    public static final String RESULT_OK = "OK";
    public static final String RESULT_TIMEOUT = "Timeout";

    AtomicInteger step = new AtomicInteger();;
    private SharedPreferences prefs;

    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.ROOT);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        prefs = PreferenceManager.getDefaultSharedPreferences(this);
    }

    public void clickRestart(View v) {
        if ("".equals(getRouterPassword())) {
            new AlertDialog.Builder(this)
                    .setTitle("Warning")
                    .setMessage("Router password is not set!")
                    .setPositiveButton("Set password", (dialog, which) -> actionSettings())
                    .setNegativeButton("Back", null)
                    .show();
        } else {

            new AlertDialog.Builder(this)
                    .setTitle("Warning")
                    .setMessage("Are you sure?\nRestart router " + getRouterAddress() + (isTestMode() ? "\n(test mode)" : ""))
                    .setPositiveButton("Restart", (dialog, which) -> routerRestart())
                    .setNegativeButton("Back", null)
                    .show();
        }
    }

    public void routerRestart() {
        step.set(STEP1_INIT);

        WebView webView = new WebView(getApplicationContext());

        webView.getSettings().setJavaScriptEnabled(true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view1, String url) {
                super.onPageFinished(view1, url);
                log("    page loaded [" + url + "]");

                switch (step.get()) {
                    case STEP1_INIT:
                        log("Step 2 - Page loaded [" + url + "] -> Logout");
                        step.set(STEP2_LOGOUT_START);
                        webView.evaluateJavascript(
                                "(function() { if (typeof logout === 'function') { logout(); } else { return false; }; } )();", //", // logout();
                                result -> {
                                    log("Step 3 - Logout result [" + result + "] -> Reloading page " + getRouterAddress());
                                    if ("false".equals(result))
                                    new AlertDialog.Builder(MainActivity.this)
                                            .setTitle("Error")
                                            .setMessage("It looks like have some another router.\n(there is no function to call)")
                                            .setPositiveButton("Go on anyway", (dialog, which) -> {
                                                step.set(STEP3_RELOAD_AFTER_LOGOUT);
                                                webView.loadUrl(getRouterAddress());
                                            })
                                            .setNeutralButton("Back", null)
                                            .show();

                                    else {
                                        step.set(STEP3_RELOAD_AFTER_LOGOUT);
                                        webView.loadUrl(getRouterAddress());
                                    }
                                }
                        );
                        break;
                    case STEP3_RELOAD_AFTER_LOGOUT:
                        log("Step 4 - Page loaded [" + url + "] -> Login");
                        step.set(STEP4_LOGIN_START);
                        webView.evaluateJavascript(
                                "login('admin', '" + getRouterPassword() + "');",
                                result -> {
                                    log("Step 5 - Login finished [" + result + "] -> Loading the restart page");
                                    if ("null".equals(result))
                                        new AlertDialog.Builder(MainActivity.this)
                                                .setTitle("Error")
                                                .setMessage("It looks like you have a problem with login. \nThe server did not confirm your credentials. \nIs your password wrong?")
                                                .setPositiveButton("Change password", (dialog, which) -> actionSettings())
                                                .setNegativeButton("Go on anyway", (dialog, which) -> {
                                                    step.set(STEP5_SWITCH_TO_RESTART);
                                                    webView.loadUrl(getRouterAddress() + "?status_restart&mid=StatusRestart");
                                                })
                                                .setNeutralButton("Back", null)
                                                .show();
                                    else {
                                        step.set(STEP5_SWITCH_TO_RESTART);
                                        webView.loadUrl(getRouterAddress() + "?status_restart&mid=StatusRestart");
                                    }
                                }
                        );
                        break;
                    case STEP5_SWITCH_TO_RESTART:
                        log("Step 6 - Switched to restart page [" + url + "] -> Initiating restart");
                        step.set(STEP6_RESTART);
                        String script;
                        if (isTestMode()) {
                            script = "isLoggedIn();";
                            log("test mode on");
                        } else
                            script = "communicateToServer('POST', 'php/ajaxSet_status_restart.php', {'RestartReset': 'Restart'}, null, 0, null, true);";
                        webView.evaluateJavascript(
//                                "",
//                                "storeData('Restart');",
                                script,
                                result -> {
                                    log("Step 7 - Restart initiated [" + result + "]");
                                    step.set(STEP7_TRIGGERED);

                                    runOnUiThread(() -> {
                                        new AlertDialog.Builder(MainActivity.this).setTitle("Info").setMessage("Restart triggered").setPositiveButton("Ok", null).show();
                                    });
                                }
                        );

                        break;
                }
            }

            @Nullable @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                if (!request.getMethod().equals("GET"))
                    log("    request " + request.getMethod() + " [" + request.getUrl() + "]");
                return super.shouldInterceptRequest(view, request);
            }
        });

        log("Init restart");
        executor.submit(() -> {
            if (ping(getRouterAddress())) {
                runOnUiThread(() -> {
                    log("Step 0 - Loading the router page");
                    webView.loadUrl(getRouterAddress());
                });
            } else {
                log("Init 4");
                runOnUiThread(() -> {
                    new AlertDialog.Builder(MainActivity.this)
                        .setTitle("Error")
                        .setMessage("Router is not reachable via URL\n" + getRouterAddress() + "")
                        .setPositiveButton("Abort", null)
                        .show();
                });
            }
        });

    }

    private boolean isTestMode() {
        return ((CheckBox) findViewById(R.id.checkboxTest)).isChecked();
    }

    private void log(String string) {
        Log.d("MyApp", string);

        runOnUiThread(() -> {
            SpannableString sp = new SpannableString(string);
            sp = colorize(sp, RESULT_OK, Color.GREEN);
            sp = colorize(sp, RESULT_TIMEOUT, Color.RED);

            TextView textview = findViewById(R.id.tvLog);
            textview.append("\n" + sdf.format(new Date()) + " ");
            textview.append(sp);

            ScrollView scrollView = findViewById(R.id.scrollView);
            scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));

        });
    }

    private static SpannableString colorize(SpannableString input, String subString, int color) {
        SpannableString output = new SpannableString(input);

        int last = 0;
        int current;
        do {
            current = input.toString().indexOf(subString, last);
            if (current >= 0) {
                output.setSpan(new ForegroundColorSpan(color), current, current + subString.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                last = current + subString.length();
            }
        } while (current >= 0);

        return output;
    }

    public void clickClear(View view) {
        step.set(STEP1_INIT);
        TextView textview = findViewById(R.id.tvLog);
        textview.setText("");
    }


    public void clickPing(View view) {

        executor.submit(() -> {
            try {
                while (((SwitchCompat) view).isChecked()) {

                    String routerPing = "router " +
                            (ping(getRouterAddress()) ? RESULT_OK : RESULT_TIMEOUT);

                    String externalPing = "external " +
                        (ping(getExternalWebsite()) ? RESULT_OK : RESULT_TIMEOUT);

                    log(routerPing + " " + externalPing);

                    Thread.sleep(1000);
                }
            } catch (Exception e) {
                log(e.toString());
            }
        });
    }

    private boolean ping(String website) {
        int code;
        HttpURLConnection connection = null;
        try {
            URL url = new URL(website);
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(1000);
            connection.setReadTimeout(1000);
            connection.setRequestMethod("HEAD");
            code = connection.getResponseCode();
            Log.d("MyApp3", "code " + code + ", website " + website);
        } catch (Exception e) {
            Log.d("MyApp3", "website " + website + ", error     " + e.toString());
            code = -1;
        } finally {
            if (connection != null)
                connection.disconnect();
        }

        return code == 200;
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.actionSettings: actionSettings(); break;
            default: return super.onOptionsItemSelected(item);
        }
        return true;
    }

    private void actionSettings() {
        startActivityForResult(new Intent(this, SettingsActivity.class), 0);
    }


    private String getRouterAddress() {
        return prefs.getString("RouterAddress", "http://192.168.0.1/");
    }

    private String getRouterPassword() {
        return prefs.getString("RouterPassword", "");
    }

    private String getExternalWebsite() {
        return prefs.getString("ExternalWebsite", "https://www.google.com/");
    }




}