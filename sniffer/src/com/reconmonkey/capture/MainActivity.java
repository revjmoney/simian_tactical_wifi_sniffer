package com.reconmonkey.capture;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStreamReader;

public class MainActivity extends Activity {

    static final String DIR = "/sdcard/recon_captures";
    static final String ENGINE = DIR + "/engine.sh";

    // capture engine, written to /sdcard on launch. uses chroot `setchan` + native tcpdump.
    // setchan must be compiled in the chroot at /root/setchan (see setchan/setchan.c).
    static final String[] E = {
        "#!/system/bin/sh",
        "M=/data/local/mnt",
        "LDBIN=/data/data/ru.meefik.linuxdeploy/files/bin",
        "DIR=/sdcard/recon_captures",
        "PX=\"export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin;\"",
        "mkdir -p $DIR",
        "mon(){ $LDBIN/linuxdeploy mount >/dev/null 2>&1; chroot $M /usr/bin/bash -c \"$PX iw dev wlan0 set type monitor; ifconfig wlan0 up\" 2>/dev/null; ifconfig wlan0 up 2>/dev/null; }",
        "setch(){ chroot $M /usr/bin/bash -c \"$PX /root/setchan $1\" >/dev/null 2>&1; }",
        "case \"$1\" in",
        "chan)",
        "  CH=$2; rm -f $DIR/.stop; mon; setch $CH; TS=$(date +%Y%m%d_%H%M%S)",
        "  /system/xbin/tcpdump -i wlan0 -n -s 0 -w $DIR/ch${CH}_$TS.pcap >/dev/null 2>&1 &",
        "  echo $! > $DIR/.cap.pid ;;",
        "sweep)",
        "  rm -f $DIR/.stop; mon; TS=$(date +%Y%m%d_%H%M%S); SW=$DIR/sweep_$TS; mkdir -p $SW",
        "  for ch in 1 2 3 4 5 6 7 8 9 10 11; do",
        "    [ -f $DIR/.stop ] && break; setch $ch",
        "    /system/xbin/tcpdump -i wlan0 -n -s 0 -w $SW/ch$ch.pcap >/dev/null 2>&1 &",
        "    TP=$!; sleep 6; kill $TP 2>/dev/null",
        "  done; rm -f $DIR/.sweeping ;;",
        "stop)",
        "  touch $DIR/.stop; $LDBIN/pkill -f 'tcpdump -i wlan0' 2>/dev/null",
        "  kill $(cat $DIR/.cap.pid 2>/dev/null) 2>/dev/null",
        "  chroot $M /usr/bin/bash -c \"$PX iw dev wlan0 set type managed\" 2>/dev/null",
        "  svc wifi disable; sleep 2; svc wifi enable; sleep 1; rm -f $DIR/.cap.pid",
        "  echo \"Stopped. WiFi restored to managed.\" ;;",
        "list)",
        "  n=0; for f in $DIR/*.pcap $DIR/sweep_*/*.pcap; do [ -f \"$f\" ] || continue; n=$((n+1)); SZ=$($LDBIN/du -h $f | cut -f1); echo \"$SZ  ${f#$DIR/}\"; done",
        "  [ $n -eq 0 ] && echo \"(no captures yet)\" ;;",
        "esac"
    };

    TextView status;
    EditText chan;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        writeEngine();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);
        int p = dp(12);
        root.setPadding(p, p, p, p);

        TextView title = new TextView(this);
        title.setText("SIMIAN TACTICAL");
        title.setTextColor(0xFFFFC107);
        title.setTextSize(24);
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText("WiFi Sniffer — monitor mode");
        sub.setTextColor(0xFFBBBBBB);
        sub.setTextSize(12);
        sub.setPadding(0, 0, 0, dp(8));
        root.addView(sub);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        TextView clab = new TextView(this);
        clab.setText("Channel (1-14): ");
        clab.setTextColor(0xFFFFFFFF);
        clab.setTextSize(16);
        chan = new EditText(this);
        chan.setInputType(InputType.TYPE_CLASS_NUMBER);
        chan.setText("6");
        chan.setTextColor(0xFFFFFFFF);
        chan.setWidth(dp(70));
        row.addView(clab);
        row.addView(chan);
        root.addView(row);

        root.addView(btn("▶  CAPTURE THIS CHANNEL", 0xFF2E7D32, "chan", true));
        root.addView(btn("⟳  SWEEP ALL (1-11)", 0xFF1565C0, "sweep", true));
        root.addView(btn("■  STOP & Restore WiFi", 0xFFB71C1C, "stop", false));
        root.addView(btn("≡  List Captures", 0xFF37474F, "list", false));

        TextView slabel = new TextView(this);
        slabel.setText("\nstatus:");
        slabel.setTextColor(0xFFFFC107);
        root.addView(slabel);

        status = new TextView(this);
        status.setTextColor(0xFF00E676);
        status.setTextSize(12);
        status.setText("Ready. Pick a channel & CAPTURE, or SWEEP all.");
        ScrollView sv = new ScrollView(this);
        sv.addView(status);
        sv.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        root.addView(sv);

        setContentView(root);
    }

    Button btn(String text, int color, final String action, final boolean detached) {
        Button x = new Button(this);
        x.setText(text);
        x.setTextColor(0xFFFFFFFF);
        x.setBackgroundColor(color);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(3), 0, dp(3));
        x.setLayoutParams(lp);
        x.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { go(action, detached); }
        });
        return x;
    }

    void go(final String action, final boolean detached) {
        String arg = "";
        String note;
        if (action.equals("chan")) {
            String c = chan.getText().toString().trim();
            int n;
            try { n = Integer.parseInt(c); } catch (Exception e) { status.setText("Enter a channel number 1-14."); return; }
            if (n < 1 || n > 14) { status.setText("Channel must be 1-14."); return; }
            arg = " " + n;
            note = "Capturing on channel " + n + " (monitor mode).\nTap STOP & Restore when done.";
        } else if (action.equals("sweep")) {
            note = "Sweeping channels 1-11, ~6s each (~70s total).\nPer-channel pcaps -> sweep_<time>/.\nTap List Captures to see them.";
        } else {
            note = "working...";
        }
        final String cmd;
        if (detached) {
            cmd = "setsid sh " + ENGINE + " " + action + arg + " </dev/null >/dev/null 2>&1 &";
        } else {
            cmd = "sh " + ENGINE + " " + action;
        }
        status.setText(note);
        new Thread(new Runnable() {
            public void run() {
                final String out = sh(cmd);
                if (!detached) {
                    runOnUiThread(new Runnable() { public void run() { status.setText(out); } });
                }
            }
        }).start();
    }

    void writeEngine() {
        try {
            new File(DIR).mkdirs();
            FileWriter w = new FileWriter(ENGINE);
            for (String line : E) { w.write(line); w.write("\n"); }
            w.close();
        } catch (Exception e) { }
    }

    String sh(String cmd) {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", cmd});
            BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line).append("\n");
            p.waitFor();
            String s = sb.toString().trim();
            return s.length() == 0 ? "(done)" : s;
        } catch (Exception ex) {
            return "ERROR: " + ex.getMessage() + "\n(grant root when SuperSU prompts)";
        }
    }

    int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }
}
