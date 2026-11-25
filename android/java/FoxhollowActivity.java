package dev.foxhollow.android;
import android.app.ActivityManager; import android.content.*; import android.graphics.*;
import android.graphics.drawable.GradientDrawable; import android.os.*; import android.system.Os;
import android.text.*; import android.text.style.ForegroundColorSpan; import android.util.TypedValue;
import android.view.*; import android.widget.*; import java.io.*; import dev.encounter.borealis.BorealisActivity;

public class FoxhollowActivity extends BorealisActivity {
  TextView hudView; Handler hudHandler; Choreographer.FrameCallback frameCb;
  long lastFrameNs = 0, secStartNs = 0; int frameCnt = 0; float curFps = 0f, curMs = 0f;
  boolean sFps, sFt, sTemp, sBat, sRam, sCpu, isNarrow; int hudPos = 0, hudSize = 1;

  @Override protected void onCreate(Bundle savedInstanceState) {
    Intent i = getIntent();
    if (i != null) {
      String[] keys = {"FOXHOLLOW_USER_DIR","FOXHOLLOW_CACHE_DIR","FOXHOLLOW_DISC","FOXHOLLOW_MEMORY_CARD","FOXHOLLOW_AUTOSAVE","FOXHOLLOW_REFLECTIONS","FOXHOLLOW_HD_TEXTURES","FOXHOLLOW_HOLD_A","FOXHOLLOW_RENDER_SCALE","FOXHOLLOW_SCREEN_STYLE","FOXHOLLOW_FRAME_LIMIT","FOXHOLLOW_VSYNC","FOXHOLLOW_ANISO","FOXHOLLOW_RENDERER","FOXHOLLOW_LANGUAGE","FOXHOLLOW_DEBUG"};
      for (String k : keys) { String v = i.getStringExtra(k); if (v != null && !v.isEmpty()) try { Os.setenv(k, v, true); } catch (Throwable ignored) {} }
      try { Os.setenv("FOXHOLLOW_SHOW_FPS", "0", true); Os.setenv("FOXHOLLOW_SHOW_TEMP", "0", true); } catch (Throwable ignored) {}
      String r = i.getStringExtra("FOXHOLLOW_RENDERER"); if (r != null && !r.isEmpty()) try { Os.setenv("FOXHOLLOW_BACKEND", r, true); } catch (Throwable ignored) {}
      String icd = i.getStringExtra("CUSTOM_VK_ICD"); if (icd != null && !icd.isEmpty()) try { Os.setenv("VK_ICD_FILENAMES", icd, true); Os.setenv("VK_DRIVER_FILES", icd, true); } catch (Throwable ignored) {}
      sFps = "1".equals(i.getStringExtra("FOXHOLLOW_SHOW_FPS")); sFt = "1".equals(i.getStringExtra("FOXHOLLOW_SHOW_FRAMETIME"));
      sTemp = "1".equals(i.getStringExtra("FOXHOLLOW_SHOW_TEMP")); sBat = "1".equals(i.getStringExtra("FOXHOLLOW_SHOW_BATTERY"));
      sRam = "1".equals(i.getStringExtra("FOXHOLLOW_SHOW_RAM")); sCpu = "1".equals(i.getStringExtra("FOXHOLLOW_SHOW_CPU_FREQ"));
      try { hudPos = Integer.parseInt(i.getStringExtra("FOXHOLLOW_STAT_POS")); } catch (Throwable ignored) {}
      try { hudSize = Integer.parseInt(i.getStringExtra("FOXHOLLOW_STAT_SIZE")); } catch (Throwable ignored) {}
    }
    isNarrow = i != null && "narrow".equals(i.getStringExtra("FOXHOLLOW_SCREEN_STYLE"));
    super.onCreate(savedInstanceState);
    if (isNarrow) {
      getWindow().getDecorView().setBackgroundColor(0xFF000000);
      View dv = getWindow().getDecorView(); dv.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or2, ob) -> applyNarrowSurface(dv)); dv.post(() -> applyNarrowSurface(dv));
    }
    if (sFps || sFt || sTemp || sBat || sRam || sCpu) getWindow().getDecorView().post(() -> { try { setupHudOverlay(); } catch (Throwable ignored) {} });
  }
  void setupHudOverlay() {
    float d = getResources().getDisplayMetrics().density;
    hudView = new TextView(this); hudView.setTextColor(0xFFF8FAFC); hudView.setTypeface(Typeface.SANS_SERIF, Typeface.BOLD);
    hudView.setTextSize(TypedValue.COMPLEX_UNIT_SP, hudSize == 0 ? 10.5f : (hudSize == 2 ? 14.5f : 12f));
    GradientDrawable bg = new GradientDrawable(); bg.setColor(0x78060A12); bg.setCornerRadius(14f * d);
    hudView.setBackground(bg); hudView.setPadding(Math.round(12f * d), Math.round(5f * d), Math.round(12f * d), Math.round(5f * d));
    int grav = hudPos == 1 ? (Gravity.TOP | Gravity.END) : hudPos == 2 ? (Gravity.BOTTOM | Gravity.START) : hudPos == 3 ? (Gravity.BOTTOM | Gravity.END) : (Gravity.TOP | Gravity.START);
    FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2, grav); int m = Math.round(14f * d); lp.setMargins(m, m, m, m); addContentView(hudView, lp);
    frameCb = now -> {
      if (lastFrameNs != 0) curMs = (now - lastFrameNs) / 1000000f; lastFrameNs = now; frameCnt++;
      if (secStartNs == 0) secStartNs = now; else if (now - secStartNs >= 500000000L) { curFps = (frameCnt * 1000000000f) / (now - secStartNs); frameCnt = 0; secStartNs = now; }
      if (hudView != null && hudView.getVisibility() == View.VISIBLE) Choreographer.getInstance().postFrameCallback(frameCb);
    };
    Choreographer.getInstance().postFrameCallback(frameCb);
    hudHandler = new Handler(Looper.getMainLooper());
    hudHandler.post(new Runnable() { @Override public void run() { if (hudView != null) { hudView.setText(buildTelemetryPill()); hudHandler.postDelayed(this, 500); } } });
  }
  void addSeg(SpannableStringBuilder sb, String lbl, String val, int valCol) {
    if (sb.length() > 0) { int s0 = sb.length(); sb.append("  \u2022  "); sb.setSpan(new ForegroundColorSpan(0xFF475569), s0, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE); }
    if (!lbl.isEmpty()) { int s1 = sb.length(); sb.append(lbl).append(" "); sb.setSpan(new ForegroundColorSpan(0xFF94A3B8), s1, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE); }
    int s2 = sb.length(); sb.append(val); sb.setSpan(new ForegroundColorSpan(valCol), s2, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
  }
  CharSequence buildTelemetryPill() {
    SpannableStringBuilder sb = new SpannableStringBuilder();
    if (sFps) addSeg(sb, "FPS", String.format("%.0f", curFps), curFps >= 55f ? 0xFF34D399 : (curFps >= 40f ? 0xFFFBBF24 : 0xFFF87171));
    if (sFt) addSeg(sb, "", String.format("%.1f ms", curMs), 0xFFE2E8F0);
    if (sCpu) { long khz = readMaxCpuKhz(); if (khz > 0) addSeg(sb, "CPU", String.format("%.2f GHz", khz / 1000000f), 0xFF93C5FD); }
    if (sTemp) { float tc = readTempC(); if (tc > 0f) addSeg(sb, "", String.format("%.1f\u00B0C", tc), tc >= 75f ? 0xFFF87171 : 0xFFFDE68A); }
    if (sRam) { long kb = readGameRamKb(); if (kb > 0) addSeg(sb, "RAM", kb >= 1048576L ? String.format("%.2fG", kb / 1048576f) : String.format("%dM", kb / 1024L), 0xFFCBD5E1); }
    if (sBat) { BatteryManager bm = (BatteryManager) getSystemService(BATTERY_SERVICE); int pct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY); int ua = Math.abs(bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)); addSeg(sb, "BAT", ua > 1000 ? String.format("%d%% (%.1fW)", pct, (ua / 1000000f) * 3.85f) : (pct + "%"), 0xFF67E8F9); }
    return sb;
  }
  @Override public void onWindowFocusChanged(boolean hasFocus) { super.onWindowFocusChanged(hasFocus); if (hasFocus && isNarrow) applyNarrowSurface(getWindow().getDecorView()); }
  @Override protected void onDestroy() { super.onDestroy(); System.exit(0); }
  long readGameRamKb() {
    try (BufferedReader br = new BufferedReader(new FileReader("/proc/self/status"))) {
      String line;
      while ((line = br.readLine()) != null) {
        if (line.startsWith("VmRSS:")) {
          String digits = line.replaceAll("[^0-9]", "");
          if (!digits.isEmpty()) return Long.parseLong(digits);
        }
      }
    } catch (Throwable ignored) {}
    return 0L;
  }

  long readMaxCpuKhz() { long best = 0; for (int c = 0; c < 8; c++) { try (BufferedReader br = new BufferedReader(new FileReader("/sys/devices/system/cpu/cpu" + c + "/cpufreq/scaling_cur_freq"))) { long v = Long.parseLong(br.readLine().trim()); if (v > best) best = v; } catch (Throwable ignored) {} } return best; }
  float readTempC() { for (int z = 0; z < 16; z++) { try (BufferedReader br = new BufferedReader(new FileReader("/sys/class/thermal/thermal_zone" + z + "/temp"))) { float v = Float.parseFloat(br.readLine().trim()); if (v > 1000f) v /= 1000f; if (v >= 20f && v <= 115f) return v; } catch (Throwable ignored) {} } Intent bi = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED)); return bi != null ? bi.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f : 0f; }

  void applyNarrowSurface(View v) {
    if (v instanceof SurfaceView) {
      android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
      int sw = dm.widthPixels, sh = dm.heightPixels;
      if (Build.VERSION.SDK_INT >= 30) { android.graphics.Rect rb = getWindowManager().getMaximumWindowMetrics().getBounds(); if (rb.width() > 0 && rb.height() > 0) { sw = rb.width(); sh = rb.height(); } }
      int fullW = Math.max(sw, sh), fullH = Math.min(sw, sh);
      int w43 = Math.min(fullW, Math.round(fullH * 4f / 3f));
      ViewGroup.LayoutParams lp = v.getLayoutParams();
      if (lp != null && (lp.width != w43 || lp.height != ViewGroup.LayoutParams.MATCH_PARENT)) {
        lp.width = w43; lp.height = ViewGroup.LayoutParams.MATCH_PARENT;
        if (lp instanceof FrameLayout.LayoutParams) ((FrameLayout.LayoutParams) lp).gravity = Gravity.CENTER_HORIZONTAL;
        else if (lp instanceof RelativeLayout.LayoutParams) ((RelativeLayout.LayoutParams) lp).addRule(RelativeLayout.CENTER_IN_PARENT, RelativeLayout.TRUE);
        v.setLayoutParams(lp);
      }
      return;
    }
    if (v instanceof ViewGroup) { ViewGroup vg = (ViewGroup) v; for (int idx = 0; idx < vg.getChildCount(); idx++) applyNarrowSurface(vg.getChildAt(idx)); }
  }
}
