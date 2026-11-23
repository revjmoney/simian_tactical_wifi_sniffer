package com.reconmonkey.server;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStreamReader;

public class MainActivity extends Activity {

    static final String DIR = "/sdcard/recon_captures";
    static final String SH = DIR + "/server.sh";

    // serves /sdcard over http :8888 via python (in the chroot), bind-mounting the SD card in.
    static final String[] E = {
        "#!/system/bin/sh",
        "M=/data/local/mnt",
        "LDBIN=/data/data/ru.meefik.linuxdeploy/files/bin",
        "case \"$1\" in",
        "start)",
        "  $LDBIN/linuxdeploy mount >/dev/null 2>&1",
        "  mkdir -p $M/mnt/sdcard",
        "  mount | grep -q \"$M/mnt/sdcard \" || $LDBIN/busybox mount --bind /storage/sdcard0 $M/mnt/sdcard",
        "  $LDBIN/pkill -f 'http.server 8888' 2>/dev/null; sleep 1",
        "  setsid chroot $M /usr/bin/python3 -m http.server 8888 --directory /mnt/sdcard --bind 0.0.0.0 </dev/null >/sdcard/httpd.log 2>&1 &",
        "  sleep 3",
        "  IPS=$(ip addr 2>/dev/null | grep -oE 'inet [0-9.]+' | cut -d' ' -f2 | grep -v '^127\\.')",
        "  if netstat -tln 2>/dev/null | grep -q :8888; then echo 'SERVER RUNNING (port 8888, /sdcard)'; echo ''; echo 'Connect another device to ONE of:'; for ip in $IPS; do echo \"   http://$ip:8888\"; done; echo ''; echo 'In HOTSPOT mode, use:'; echo '   http://192.168.43.1:8888'; else echo 'FAILED to start (is the chroot set up + mounted?)'; fi ;;",
        "stop)",
        "  $LDBIN/pkill -f 'http.server 8888' 2>/dev/null; echo 'Server STOPPED.' ;;",
        "status)",
        "  if netstat -tln 2>/dev/null | grep -q :8888; then IPS=$(ip addr 2>/dev/null | grep -oE 'inet [0-9.]+' | cut -d' ' -f2 | grep -v '^127\\.'); echo 'RUNNING on port 8888:'; for ip in $IPS; do echo \"   http://$ip:8888\"; done; echo '   (hotspot: http://192.168.43.1:8888)'; else echo 'stopped'; fi ;;",
        "esac"
    };

    TextView status;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        writeEngine();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);
        int p = dp(14);
        root.setPadding(p, p, p, p);

        TextView title = new TextView(this);
        title.setText("SIMIAN WiFi SERVER");
        title.setTextColor(0xFFFFC107);
        title.setTextSize(23);
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText("file server for /sdcard");
        sub.setTextColor(0xFFBBBBBB);
        sub.setTextSize(12);
        sub.setPadding(0, 0, 0, dp(10));
        root.addView(sub);

        root.addView(btn("▶  START SERVER", 0xFF2E7D32, "start"));
        root.addView(btn("■  STOP SERVER", 0xFFB71C1C, "stop"));
        root.addView(btn("ⓘ  STATUS", 0xFF37474F, "status"));

        TextView slabel = new TextView(this);
        slabel.setText("\nstatus:");
        slabel.setTextColor(0xFFFFC107);
        root.addView(slabel);

        status = new TextView(this);
        status.setTextColor(0xFF00E676);
        status.setTextSize(14);
        status.setText("Tap START SERVER, then open the URL\nfrom any device on the same WiFi.");
        ScrollView sv = new ScrollView(this);
        sv.addView(status);
        sv.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        root.addView(sv);

        setContentView(root);
    }

    Button btn(String text, int color, final String action) {
        Button x = new Button(this);
        x.setText(text);
        x.setTextColor(0xFFFFFFFF);
        x.setBackgroundColor(color);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(4), 0, dp(4));
        x.setLayoutParams(lp);
        x.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { go(action); }
        });
        return x;
    }

    void go(final String action) {
        status.setText("working...");
        new Thread(new Runnable() {
            public void run() {
                final String out = sh("sh " + SH + " " + action);
                runOnUiThread(new Runnable() { public void run() { status.setText(out); } });
            }
        }).start();
    }

    void writeEngine() {
        try {
            new File(DIR).mkdirs();
            FileWriter w = new FileWriter(SH);
            for (String line : E) { w.write(line); w.write("\n"); }
            w.close();
        } catch (Exception e) { }
    }

    String sh(String cmd) {
        try {
            Process pr = Runtime.getRuntime().exec(new String[]{"su", "-c", cmd});
            BufferedReader r = new BufferedReader(new InputStreamReader(pr.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line).append("\n");
            pr.waitFor();
            String s = sb.toString().trim();
            return s.length() == 0 ? "(done)" : s;
        } catch (Exception ex) {
            return "ERROR: " + ex.getMessage() + "\n(grant root when SuperSU prompts)";
        }
    }

    int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }
}
