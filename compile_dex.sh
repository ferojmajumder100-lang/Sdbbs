#!/bin/bash
set -e

echo "=== Compiling DialogLoader Java class ==="
TEMP_DIR=$(mktemp -d)
mkdir -p "$TEMP_DIR/com/example/dialogsdk"

cat << 'EOF' > "$TEMP_DIR/com/example/dialogsdk/DialogLoader.java"
package com.example.dialogsdk;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import org.json.JSONObject;

public class DialogLoader {
    private static AlertDialog activeDialog = null;

    public static void init(final Context context, final String configUrl, final int checkIntervalSeconds) {
        if (context == null || configUrl == null || configUrl.isEmpty()) {
            return;
        }

        final Handler handler = new Handler(Looper.getMainLooper());
        Runnable checkRunnable = new Runnable() {
            @Override
            public void run() {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            URL url = new URL(configUrl);
                            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                            conn.setRequestMethod("GET");
                            conn.setConnectTimeout(5000);
                            conn.setReadTimeout(5000);

                            int responseCode = conn.getResponseCode();
                            if (responseCode == 200) {
                                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
                                String inputLine;
                                StringBuilder content = new StringBuilder();
                                while ((inputLine = in.readLine()) != null) {
                                    content.append(inputLine);
                                }
                                in.close();
                                conn.disconnect();

                                final JSONObject json = new JSONObject(content.toString());
                                final String status = json.optString("status", "on"); // "on" = normal/hide dialog, "off" = show dialog/toast
                                final String title = json.optString("title", "Update Available");
                                final String message = json.optString("message", "Please update to the latest version.");
                                final String type = json.optString("type", "dialog"); // "dialog" or "toast"
                                final boolean cancelable = json.optBoolean("is_cancelable", true);
                                final String buttonText = json.optString("button_text", "Update");
                                final String actionUrl = json.optString("action_url", "");

                                handler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        if ("off".equalsIgnoreCase(status)) {
                                            if ("dialog".equalsIgnoreCase(type)) {
                                                if (activeDialog != null && activeDialog.isShowing()) {
                                                    return;
                                                }
                                                AlertDialog.Builder builder = new AlertDialog.Builder(context);
                                                builder.setTitle(title);
                                                builder.setMessage(message);
                                                builder.setCancelable(cancelable);

                                                if (!actionUrl.isEmpty()) {
                                                    builder.setPositiveButton(buttonText, new DialogInterface.OnClickListener() {
                                                        @Override
                                                        public void onClick(DialogInterface dialog, int which) {
                                                            try {
                                                                Intent intent = new Intent(Intent.ACTION_VIEW);
                                                                intent.setData(Uri.parse(actionUrl));
                                                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                                                context.startActivity(intent);
                                                            } catch (Exception e) {
                                                                e.printStackTrace();
                                                            }
                                                        }
                                                    });
                                                } else {
                                                    builder.setPositiveButton("OK", null);
                                                }

                                                activeDialog = builder.create();
                                                activeDialog.show();
                                            } else if ("toast".equalsIgnoreCase(type)) {
                                                Toast.makeText(context, message, Toast.LENGTH_LONG).show();
                                            }
                                        } else {
                                            // status is "on", dismiss active dialog
                                            if (activeDialog != null && activeDialog.isShowing()) {
                                                activeDialog.dismiss();
                                                activeDialog = null;
                                            }
                                        }
                                    }
                                });
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }).start();

                if (checkIntervalSeconds > 0) {
                    handler.postDelayed(this, checkIntervalSeconds * 1000L);
                }
            }
        };

        handler.post(checkRunnable);
    }
}
EOF

echo "=== Running Javac ==="
javac -source 8 -target 8 -classpath "$ANDROID_SDK_ROOT/platforms/android-36/android.jar" "$TEMP_DIR/com/example/dialogsdk/DialogLoader.java"

echo "=== Running d8 ==="
mkdir -p app/src/main/assets
"$ANDROID_SDK_ROOT/build-tools/36.0.0/d8" --lib "$ANDROID_SDK_ROOT/platforms/android-36/android.jar" --output "$TEMP_DIR" "$TEMP_DIR/com/example/dialogsdk/DialogLoader.class"

cp "$TEMP_DIR/classes.dex" app/src/main/assets/classes.dex
echo "=== classes.dex generated and copied to app/src/main/assets/classes.dex ==="

rm -rf "$TEMP_DIR"
