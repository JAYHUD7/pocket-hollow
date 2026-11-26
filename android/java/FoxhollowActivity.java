package dev.foxhollow.android;
import android.app.ActivityManager; import android.content.*; import android.graphics.*;
import android.graphics.drawable.GradientDrawable; import android.os.*; import android.system.Os;
import android.text.*; import android.text.style.ForegroundColorSpan; import android.util.TypedValue;
import android.view.*; import android.widget.*; import java.io.*; import dev.encounter.borealis.BorealisActivity;

public class FoxhollowActivity extends BorealisActivity { public static native long nativeGetFrameStats(); int lastNativeFrames = -1; long lastStatNs = 0;
  TextView hudView; Handler hudHandler; Choreographer.FrameCallback frameCb;
  long lastFrameNs = 0, secStartNs = 0; int frameCnt = 0, totalFrames = 0; boolean bootCleared = false; float curFps = 0f, curMs = 0f; volatile long cachedKhz = 0; volatile float cachedTemp = 0f; volatile String cachedBat = null; volatile long cachedRamKb = 0L;
  boolean sFps, sFt, sTemp, sBat, sRam, sCpu, isNarrow, touchEnabled, touchHaptics, touchHybrid; float touchScale = 1.0f; int hudPos = 0, hudSize = 1; GcTouchView touchView; LinearLayout touchBar; TextView touchPill, alphaPill, editPill, resetPill; public static native void nativeSetTouchPad(long p); public static native void nativeSetPhysPad(int m, int en); int physMask = 0; boolean physCustom = false; final int[] kcMap = new int[]{96,97,99,100,102,103,105,108,19,20,21,22}; final int[] gcBits = new int[]{0x0100,0x0200,0x0400,0x0800,0x0040,0x0020,0x0010,0x1000,0x0008,0x0004,0x0001,0x0002};

  @Override protected void onCreate(Bundle savedInstanceState) {
    Intent i = getIntent();
    if (i != null) {
      String[] keys = {"FOXHOLLOW_USER_DIR","FOXHOLLOW_CACHE_DIR","FOXHOLLOW_DISC","FOXHOLLOW_MEMORY_CARD","FOXHOLLOW_AUTOSAVE","FOXHOLLOW_REFLECTIONS","FOXHOLLOW_HD_TEXTURES","FOXHOLLOW_HOLD_A","FOXHOLLOW_BTN_REMAP","FOXHOLLOW_RENDER_SCALE","FOXHOLLOW_SCREEN_STYLE","FOXHOLLOW_FRAME_LIMIT","FOXHOLLOW_VSYNC","FOXHOLLOW_ANISO","FOXHOLLOW_RENDERER","FOXHOLLOW_LANGUAGE","FOXHOLLOW_DEBUG"};
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
    isNarrow = i != null && "narrow".equals(i.getStringExtra("FOXHOLLOW_SCREEN_STYLE")); touchEnabled = i != null && "1".equals(i.getStringExtra("FOXHOLLOW_TOUCH_CONTROLS")); touchHybrid = i != null && "1".equals(i.getStringExtra("FOXHOLLOW_TOUCH_HYBRID")); SharedPreferences cp = getSharedPreferences("foxhollow_pocket_prefs_v11", MODE_PRIVATE); physCustom = cp.getBoolean("kc_custom", false); for(int k=0;k<12;k++) java.lang.reflect.Array.setInt(kcMap,k,cp.getInt("kc_"+k,java.lang.reflect.Array.getInt(kcMap,k))); touchHaptics = i == null || !"0".equals(i.getStringExtra("FOXHOLLOW_TOUCH_HAPTICS")); try { String sc = i != null ? i.getStringExtra("FOXHOLLOW_TOUCH_SCALE") : null; if (sc != null) touchScale = Float.parseFloat(sc); } catch (Throwable ignored) {}
    super.onCreate(savedInstanceState); try { getWindow().getDecorView().requestUnbufferedDispatch(InputDevice.SOURCE_ANY); } catch (Throwable ignored) {} if (Build.VERSION.SDK_INT >= 24) { try { getWindow().setSustainedPerformanceMode(true); } catch (Throwable ignored) {} }
    if (isNarrow) {
      getWindow().getDecorView().setBackgroundColor(0xFF000000);
      View dv = getWindow().getDecorView(); dv.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or2, ob) -> applyNarrowSurface(dv)); dv.post(() -> applyNarrowSurface(dv));
    }
    if (sFps || sFt || sTemp || sBat || sRam || sCpu) getWindow().getDecorView().post(() -> { try { setupHudOverlay(); } catch (Throwable ignored) {} }); getWindow().getDecorView().post(() -> { try { setupTouchOverlay(); } catch (Throwable ignored) {} });
  }
  void setupHudOverlay() {
    float d = getResources().getDisplayMetrics().density;
    hudView = new TextView(this); hudView.setTextColor(0xFFF8FAFC); hudView.setTypeface(Typeface.SANS_SERIF, Typeface.BOLD);
    hudView.setIncludeFontPadding(false); hudView.setTextSize(TypedValue.COMPLEX_UNIT_SP, hudSize == 0 ? 9.5f : (hudSize == 2 ? 13f : 11f));
    GradientDrawable bg = new GradientDrawable(); bg.setColor(0x78060A12); bg.setCornerRadius(9f * d);
    hudView.setBackground(bg); hudView.setPadding(Math.round(8f * d), Math.round(3f * d), Math.round(8f * d), Math.round(3f * d));
    int grav = hudPos == 1 ? (Gravity.TOP | Gravity.END) : hudPos == 2 ? (Gravity.BOTTOM | Gravity.START) : hudPos == 3 ? (Gravity.BOTTOM | Gravity.END) : (Gravity.TOP | Gravity.START);
    FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2, grav); lp.setMargins(Math.round(4f * d), Math.round(3f * d), Math.round(4f * d), Math.round(3f * d)); addContentView(hudView, lp);
    Thread statBg = new Thread(() -> { while (!isFinishing()) { try { if (sCpu) cachedKhz = readMaxCpuKhz(); if (sTemp) cachedTemp = readTempC(); if (sBat) cachedBat = readBatteryStr(); if (sRam) cachedRamKb = readGameRamKb(); Thread.sleep(1000); } catch (Throwable ignored) {} } }); statBg.setDaemon(true); statBg.start(); hudHandler = new Handler(Looper.getMainLooper());
    hudHandler.post(new Runnable() { @Override public void run() {
      try { long st = nativeGetFrameStats(); int f = (int)(st >>> 32); int us = (int)(st & 0xFFFFFFFFL); long now = System.nanoTime(); if (lastNativeFrames >= 0 && lastStatNs > 0) { float dt = (now - lastStatNs) / 1000000000f; if (dt > 0.05f) curFps = Math.max(0f, (f - lastNativeFrames) / dt); } lastNativeFrames = f; lastStatNs = now; if (us > 0) curMs = us / 1000f; else if (curFps > 0.5f) curMs = 1000f / curFps; if (!bootCleared && f > 30) { bootCleared = true; String cd = getIntent().getStringExtra("FOXHOLLOW_CACHE_DIR"); if (cd != null) new File(cd, "boot_guard.flag").delete(); } } catch (Throwable ignored) {}
      if (hudView != null) { hudView.setText(buildTelemetryPill()); hudHandler.postDelayed(this, 500); }
    } });
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
    if (sCpu) { long khz = cachedKhz; if (khz > 0) addSeg(sb, "CPU", String.format("%.2f GHz", khz / 1000000f), 0xFF93C5FD); }
    if (sTemp) { float tc = cachedTemp; if (tc > 0f) addSeg(sb, "", String.format("%.1f\u00B0C", tc), tc >= 75f ? 0xFFF87171 : 0xFFFDE68A); }
    if (sRam) { long kb = cachedRamKb; if (kb > 0) addSeg(sb, "RAM", kb >= 1048576L ? String.format("%.2fG", kb / 1048576f) : String.format("%dM", kb / 1024L), 0xFFCBD5E1); }
    if (sBat && cachedBat != null) { addSeg(sb, "BAT", cachedBat, 0xFF67E8F9); }
    return sb;
  }
  @Override public void onWindowFocusChanged(boolean hasFocus) { super.onWindowFocusChanged(hasFocus); if (hasFocus && isNarrow) applyNarrowSurface(getWindow().getDecorView()); }
  @Override protected void onDestroy() { super.onDestroy(); System.exit(0); }
  long cachedGfxKb = 0L; int gfxTick = 0;
  long readGameRamKb() {
    long rssKb = 0L;
    try (BufferedReader br = new BufferedReader(new FileReader("/proc/self/status"))) {
      String line;
      while ((line = br.readLine()) != null) {
        if (line.startsWith("VmRSS:")) {
          String digits = line.replaceAll("[^0-9]", "");
          if (!digits.isEmpty()) { rssKb = Long.parseLong(digits); break; }
        }
      }
    } catch (Throwable ignored) {}
    if ((gfxTick++ % 4) == 0) {
      try {
        android.os.Debug.MemoryInfo dmi = new android.os.Debug.MemoryInfo();
        android.os.Debug.getMemoryInfo(dmi);
        String g = dmi.getMemoryStat("summary.graphics");
        if (g != null && !g.isEmpty()) cachedGfxKb = Long.parseLong(g);
      } catch (Throwable ignored) {}
    }
    return rssKb + cachedGfxKb;
  }

  String readBatteryStr() { try { BatteryManager bm = (BatteryManager) getSystemService(BATTERY_SERVICE); int pct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY); int ua = Math.abs(bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)); return ua > 1000 ? String.format("%d%% (%.1fW)", pct, (ua / 1000000f) * 3.85f) : (pct + "%"); } catch (Throwable ignored) { return null; } }
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

  
  void setupTouchOverlay() {
    float d = getResources().getDisplayMetrics().density; FrameLayout root = findViewById(android.R.id.content); if (root == null) return;
    touchView = new GcTouchView(this, getSharedPreferences("fh_touch_cfg", MODE_PRIVATE), touchHaptics, touchScale); touchView.setVisibility(touchEnabled ? View.VISIBLE : View.GONE); root.addView(touchView, new FrameLayout.LayoutParams(-1, -1));
    touchBar = new LinearLayout(this); touchBar.setOrientation(LinearLayout.HORIZONTAL); touchPill = mkP(d); alphaPill = mkP(d); editPill = mkP(d); resetPill = mkP(d);
    LinearLayout.LayoutParams g = new LinearLayout.LayoutParams(-2, -2); g.leftMargin = Math.round(6f*d); touchBar.addView(touchPill); touchBar.addView(alphaPill, g); touchBar.addView(editPill, g); touchBar.addView(resetPill, g);
    FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL); lp.topMargin = Math.round(8f*d); // top bar removed
    touchPill.setOnClickListener(v -> setTouchVis(touchView.getVisibility() != View.VISIBLE));
    alphaPill.setOnClickListener(v -> { touchView.cycleAlpha(); refreshTopBar(); });
    editPill.setOnClickListener(v -> { touchView.editMode = !touchView.editMode; if (!touchView.editMode) touchView.saveLayout(); touchView.invalidate(); refreshTopBar(); }); resetPill.setOnClickListener(v -> { touchView.resetLayoutToDefault(); refreshTopBar(); });
    refreshTopBar();
  }
  TextView mkP(float d) { TextView t = new TextView(this); t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.5f); t.setTypeface(Typeface.DEFAULT_BOLD); t.setPadding(Math.round(9f*d), Math.round(3f*d), Math.round(9f*d), Math.round(3f*d)); return t; }
  void stP(TextView t, String x, int fg, int st, float d) { if (t==null) return; t.setText(x); t.setTextColor(fg); GradientDrawable b = new GradientDrawable(); b.setColor(0x66080C14); b.setCornerRadius(99f*d); b.setStroke(Math.max(1,Math.round(d)), st); t.setBackground(b); }
  void refreshTopBar() { if (touchView==null) return; float d = getResources().getDisplayMetrics().density; boolean v = touchView.getVisibility()==View.VISIBLE; stP(touchPill, v?"PAD ON":"PAD", v?0xFF34D399:0xCC94A3B8, v?0x9934D399:0x4494A3B8, d); alphaPill.setVisibility(v?View.VISIBLE:View.GONE); editPill.setVisibility(v?View.VISIBLE:View.GONE); stP(alphaPill, Math.round(touchView.uiAlpha*100f)+"%", 0xFFE2E8F0, 0x5594A3B8, d); stP(editPill, touchView.editMode?"DONE":"EDIT", touchView.editMode?0xFFFACC15:0xCC94A3B8, touchView.editMode?0xAAFACC15:0x4494A3B8, d); resetPill.setVisibility((v && touchView.editMode)?View.VISIBLE:View.GONE); stP(resetPill, "RESET", 0xFFF87171, 0x99F87171, d); }
  void setTouchVis(boolean v) { if (touchView==null) return; touchView.editMode=false; touchView.setVisibility(v?View.VISIBLE:View.GONE); if (!v) { touchView.resetAll(); try { nativeSetTouchPad(0L); } catch (Throwable ignored) {} } refreshTopBar(); }
    @Override public boolean dispatchKeyEvent(KeyEvent e) {
    int kc = e.getKeyCode();
    if (kc == KeyEvent.KEYCODE_BACK || (e.getSource() & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD || (e.getSource() & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK || (e.getSource() & InputDevice.SOURCE_DPAD) == InputDevice.SOURCE_DPAD) {
      if (!touchHybrid && touchView != null && touchView.getVisibility() == View.VISIBLE) setTouchVis(false);
      if (physCustom) {
        int bit = 0; for (int k = 0; k < 12; k++) { if (java.lang.reflect.Array.getInt(kcMap, k) == kc) bit |= java.lang.reflect.Array.getInt(gcBits, k); }
        if (bit != 0) { if (e.getAction() == KeyEvent.ACTION_DOWN) physMask |= bit; else if (e.getAction() == KeyEvent.ACTION_UP) physMask &= ~bit; try { nativeSetPhysPad(physMask, 1); } catch (Throwable ignored) {} return true; }
      }
      if (kc == KeyEvent.KEYCODE_BACK) return true;
    }
    return super.dispatchKeyEvent(e);
  }
  @Override public boolean dispatchGenericMotionEvent(MotionEvent e) { if ((e.getSource()&InputDevice.SOURCE_JOYSTICK)==InputDevice.SOURCE_JOYSTICK&&touchView!=null&&touchView.getVisibility()==View.VISIBLE&&(Math.abs(e.getAxisValue(MotionEvent.AXIS_X))>0.25f||Math.abs(e.getAxisValue(MotionEvent.AXIS_Y))>0.25f)) setTouchVis(false); return super.dispatchGenericMotionEvent(e); }
  static final class GcTouchView extends View {
    final float d; final android.content.SharedPreferences prefs;
    final Paint fp = new Paint(Paint.ANTI_ALIAS_FLAG), rp = new Paint(Paint.ANTI_ALIAS_FLAG), tp = new Paint(Paint.ANTI_ALIAS_FLAG), gp = new Paint(Paint.ANTI_ALIAS_FLAG);
    int lPtr = -1, cPtr = -1, dPtr = -1, lastBtns = 0, dragIdx = -1, selIdx = 3; Runnable onSel; final float[] itemSc = new float[]{1f,1f,1f,1f,1f,1f,1f,1f,1f,1f,1f}; boolean editMode = false, hapticsOn = true; float uiAlpha = 0.78f, scaleMul = 1f, dragOffX, dragOffY;
    float lBx, lBy, lCx, lCy, defLx, defLy, lR, cBx, cBy, cCx, cCy, defCx, defCy, cR, dpadX, dpadY, dpadR;
    final java.util.HashMap<Integer, Integer> pBtn = new java.util.HashMap<>();
    static final class Btn { final int m, col, kind; final String lbl; float cx, cy, r, w, h, ang; Btn(int m, String l, int c, int k, float a){this.m=m;lbl=l;col=c;kind=k;ang=a;} }
    final Btn[] bs = { new Btn(0x0100,"A",0xFF10B981,0,0f), new Btn(0x0200,"B",0xFFEF4444,0,0f), new Btn(0x0400,"X",0xFFCBD5E1,1,66f), new Btn(0x0800,"Y",0xFFCBD5E1,1,-24f), new Btn(0x0040,"L",0xFF94A3B8,2,0f), new Btn(0x0020,"R",0xFF94A3B8,2,0f), new Btn(0x0010,"Z",0xFF818CF8,2,0f), new Btn(0x1000,"START",0xFFCBD5E1,2,0f) };
    GcTouchView(Context c, android.content.SharedPreferences p, boolean hap, float sc) { super(c); prefs = p; hapticsOn = hap; scaleMul = sc; d = c.getResources().getDisplayMetrics().density; uiAlpha = p.getFloat("alpha", 0.78f); for(int k=0;k<11;k++) java.lang.reflect.Array.setFloat(itemSc,k,p.getFloat("sc_"+k,1f)); tp.setTextAlign(Paint.Align.CENTER); tp.setTypeface(Typeface.DEFAULT_BOLD); gp.setStyle(Paint.Style.STROKE); }
    void cycleAlpha() { uiAlpha = uiAlpha > 0.90f ? 0.35f : (uiAlpha < 0.45f ? 0.58f : (uiAlpha < 0.68f ? 0.78f : 1.0f)); prefs.edit().putFloat("alpha", uiAlpha).apply(); invalidate(); }
    void resetAll() { lPtr = cPtr = dPtr = -1; pBtn.clear(); lastBtns = 0; lCx = lBx = defLx; lCy = lBy = defLy; cCx = cBx = defCx; cCy = cBy = defCy; invalidate(); }
    Btn gb(int i) { return (Btn) java.lang.reflect.Array.get(bs, i); }
    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
      float s = scaleMul; lR = 54f*d*s*java.lang.reflect.Array.getFloat(itemSc,0); defLx = prefs.getFloat("lx", Math.max(lR+24f*d, w*0.125f)); defLy = prefs.getFloat("ly", h - Math.max(lR+28f*d, h*0.26f)); lBx = lCx = defLx; lBy = lCy = defLy;
      cR = 38f*d*s*java.lang.reflect.Array.getFloat(itemSc,1); defCx = prefs.getFloat("cx", w - Math.max(198f*d, w*0.245f)); defCy = prefs.getFloat("cy", h - Math.max(cR+22f*d, h*0.16f)); cBx = cCx = defCx; cBy = cCy = defCy;
      dpadR = 42f*d*s*java.lang.reflect.Array.getFloat(itemSc,2); dpadX = prefs.getFloat("dx", Math.max(176f*d, w*0.245f)); dpadY = prefs.getFloat("dy", h - Math.max(dpadR+18f*d, h*0.16f));
      float ax = prefs.getFloat("ax", w - Math.max(88f*d, w*0.115f)), ay = prefs.getFloat("ay", h - Math.max(98f*d, h*0.26f));
      Btn bA = gb(0); bA.cx = ax; bA.cy = ay; bA.r = 32f*d*s*java.lang.reflect.Array.getFloat(itemSc,3); Btn bB = gb(1); bB.cx = prefs.getFloat("bx", ax - 54f*d*s); bB.cy = prefs.getFloat("by", ay + 22f*d*s); bB.r = 22f*d*s*java.lang.reflect.Array.getFloat(itemSc,4);
      Btn bX = gb(2); bX.cx = prefs.getFloat("xx", ax + 48f*d*s); bX.cy = prefs.getFloat("xy", ay - 10f*d*s); float sx=java.lang.reflect.Array.getFloat(itemSc,5); bX.w = 48f*d*s*sx; bX.h = 26f*d*s*sx; Btn bY = gb(3); bY.cx = prefs.getFloat("yx", ax - 10f*d*s); bY.cy = prefs.getFloat("yy", ay - 48f*d*s); float sy=java.lang.reflect.Array.getFloat(itemSc,6); bY.w = 48f*d*s*sy; bY.h = 26f*d*s*sy;
      Btn bL = gb(4); bL.cx = prefs.getFloat("lx_b", 58f*d); bL.cy = prefs.getFloat("ly_b", 36f*d); float sl=java.lang.reflect.Array.getFloat(itemSc,7); bL.w = 82f*d*s*sl; bL.h = 34f*d*s*sl;
      Btn bR = gb(5); bR.cx = prefs.getFloat("rx_b", w - 58f*d); bR.cy = prefs.getFloat("ry_b", 56f*d); float sr=java.lang.reflect.Array.getFloat(itemSc,8); bR.w = 82f*d*s*sr; bR.h = 34f*d*s*sr;
      Btn bZ = gb(6); bZ.cx = prefs.getFloat("zx_b", w - 138f*d); bZ.cy = prefs.getFloat("zy_b", 56f*d); float sz=java.lang.reflect.Array.getFloat(itemSc,9); bZ.w = 58f*d*s*sz; bZ.h = 30f*d*s*sz;
      Btn bS = gb(7); bS.cx = prefs.getFloat("sx_b", w * 0.5f); bS.cy = prefs.getFloat("sy_b", h - 26f*d); float ss=java.lang.reflect.Array.getFloat(itemSc,10); bS.w = 74f*d*s*ss; bS.h = 26f*d*s*ss;
    }
    void setSelScale(float v){v=Math.max(0.40f,Math.min(2.50f,v));java.lang.reflect.Array.setFloat(itemSc,selIdx,v);onSizeChanged(getWidth(),getHeight(),getWidth(),getHeight());invalidate();} void adjScale(float dl){float v=Math.max(0.65f,Math.min(1.65f,java.lang.reflect.Array.getFloat(itemSc,selIdx)+dl));java.lang.reflect.Array.setFloat(itemSc,selIdx,v);onSizeChanged(getWidth(),getHeight(),getWidth(),getHeight());invalidate();} void resetLayoutToDefault() { for(int k=0;k<11;k++){java.lang.reflect.Array.setFloat(itemSc,k,1f);prefs.edit().remove("sc_"+k).apply();} prefs.edit().remove("lx").remove("ly").remove("cx").remove("cy").remove("dx").remove("dy").remove("ax").remove("ay").remove("bx").remove("by").remove("xx").remove("xy").remove("yx").remove("yy").remove("lx_b").remove("ly_b").remove("rx_b").remove("ry_b").remove("zx_b").remove("zy_b").remove("sx_b").remove("sy_b").apply(); onSizeChanged(getWidth(), getHeight(), getWidth(), getHeight()); invalidate(); } void saveLayout() { for(int k=0;k<11;k++) prefs.edit().putFloat("sc_"+k,java.lang.reflect.Array.getFloat(itemSc,k)).apply(); Btn bB=gb(1),bX=gb(2),bY=gb(3); prefs.edit().putFloat("bx",bB.cx).putFloat("by",bB.cy).putFloat("xx",bX.cx).putFloat("xy",bX.cy).putFloat("yx",bY.cx).putFloat("yy",bY.cy).apply();
      Btn bA = gb(0), bL = gb(4), bR = gb(5), bZ = gb(6), bS = gb(7);
      prefs.edit().putFloat("lx",defLx).putFloat("ly",defLy).putFloat("cx",defCx).putFloat("cy",defCy).putFloat("dx",dpadX).putFloat("dy",dpadY).putFloat("ax",bA.cx).putFloat("ay",bA.cy).putFloat("lx_b",bL.cx).putFloat("ly_b",bL.cy).putFloat("rx_b",bR.cx).putFloat("ry_b",bR.cy).putFloat("zx_b",bZ.cx).putFloat("zy_b",bZ.cy).putFloat("sx_b",bS.cx).putFloat("sy_b",bS.cy).apply();
    }
    int hitBtn(float x, float y) { int best = 0; float bd = 1e9f; for (int i = 0; i < 8; i++) { Btn b = gb(i); float rad = b.kind == 0 ? b.r * 1.22f : Math.max(b.w, b.h) * 0.60f; float dist = (float)Math.hypot(x-b.cx, y-b.cy); if (dist <= rad && dist < bd) { bd = dist; best = b.m; } } return best; }
    int dpadMask(float x, float y) { float dx = x - dpadX, dy = y - dpadY; if (Math.hypot(dx, dy) < dpadR * 0.22f) return 0; int m = 0; if (dx < -dpadR*0.28f) m |= 1; if (dx > dpadR*0.28f) m |= 2; if (dy > dpadR*0.28f) m |= 4; if (dy < -dpadR*0.28f) m |= 8; return m; }
    @Override public boolean onTouchEvent(MotionEvent e) {
      int a = e.getActionMasked(), i = e.getActionIndex(), id = e.getPointerId(i); float x = e.getX(i), y = e.getY(i); int w = getWidth(), h = getHeight();
      if (editMode) { if (a == MotionEvent.ACTION_DOWN) { Btn bA=gb(0),bB=gb(1),bX=gb(2),bY=gb(3),bL=gb(4),bR=gb(5),bZ=gb(6),bS=gb(7); float[] px={defLx,defCx,dpadX,bA.cx,bB.cx,bX.cx,bY.cx,bL.cx,bR.cx,bZ.cx,bS.cx}, py={defLy,defCy,dpadY,bA.cy,bB.cy,bX.cy,bY.cy,bL.cy,bR.cy,bZ.cy,bS.cy}; dragIdx=-1; float bd=64f*d; for(int k=0;k<11;k++){float dist=(float)Math.hypot(x-px[k],y-py[k]);if(dist<bd){bd=dist;dragIdx=k;selIdx=k;if(onSel!=null)onSel.run();dragOffX=px[k]-x;dragOffY=py[k]-y;}} } else if (a == MotionEvent.ACTION_MOVE && dragIdx != -1) { float nx=Math.max(28f*d,Math.min(w-28f*d,x+dragOffX)), ny=Math.max(20f*d,Math.min(h-20f*d,y+dragOffY)); if(dragIdx==0){defLx=lBx=lCx=nx;defLy=lBy=lCy=ny;} else if(dragIdx==1){defCx=cBx=cCx=nx;defCy=cBy=cCy=ny;} else if(dragIdx==2){dpadX=nx;dpadY=ny;} else {Btn b=gb(dragIdx-3);b.cx=nx;b.cy=ny;} invalidate(); } else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) dragIdx=-1; return true; } if (false) {
        if (a == MotionEvent.ACTION_DOWN) {
          Btn bA=gb(0),bL=gb(4),bR=gb(5),bZ=gb(6),bS=gb(7); float[] px={defLx,defCx,dpadX,bA.cx,bL.cx,bR.cx,bZ.cx,bS.cx}, py={defLy,defCy,dpadY,bA.cy,bL.cy,bR.cy,bZ.cy,bS.cy}; dragIdx=-1; float bd=72f*d;
          for (int k=0;k<8;k++){float dist=(float)Math.hypot(x-px[k],y-py[k]);if(dist<bd){bd=dist;dragIdx=k;selIdx=k;if(onSel!=null)onSel.run();dragOffX=px[k]-x;dragOffY=py[k]-y;}}
        } else if (a == MotionEvent.ACTION_MOVE && dragIdx != -1) {
          float nx=Math.max(36f*d,Math.min(w-36f*d,x+dragOffX)), ny=Math.max(24f*d,Math.min(h-24f*d,y+dragOffY));
          if(dragIdx==0){defLx=lBx=lCx=nx;defLy=lBy=lCy=ny;} else if(dragIdx==1){defCx=cBx=cCx=nx;defCy=cBy=cCy=ny;} else if(dragIdx==2){dpadX=nx;dpadY=ny;}
          else if(dragIdx==3){Btn bA=gb(0),bB=gb(1),bX=gb(2),bY=gb(3);bA.cx=nx;bA.cy=ny;bB.cx=nx-54f*d;bB.cy=ny+22f*d;bX.cx=nx+48f*d;bX.cy=ny-10f*d;bY.cx=nx-10f*d;bY.cy=ny-48f*d;}
          else{Btn b=gb(dragIdx);b.cx=nx;b.cy=ny;} invalidate();
        } else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) dragIdx = -1;
        return true;
      }
      if (a == MotionEvent.ACTION_DOWN || a == MotionEvent.ACTION_POINTER_DOWN) {
        int hm = hitBtn(x, y);
        if (hm != 0) pBtn.put(id, hm);
        else if (dPtr == -1 && Math.hypot(x-dpadX, y-dpadY) <= dpadR*1.25f) { dPtr = id; pBtn.put(id, dpadMask(x, y)); }
        else if (cPtr == -1 && Math.hypot(x-defCx, y-defCy) <= cR*1.45f) { cPtr = id; cBx = defCx; cBy = defCy; cCx = x; cCy = y; }
        else if (lPtr == -1 && x < w*0.46f && y > h*0.22f) { lPtr = id; lBx = Math.max(lR+16f*d, Math.min(w*0.36f, x)); lBy = Math.max(h*0.32f, Math.min(h-lR-16f*d, y)); lCx = x; lCy = y; }
      } else if (a == MotionEvent.ACTION_MOVE) {
        for (int p = 0; p < e.getPointerCount(); p++) {
          int pid = e.getPointerId(p); float px = e.getX(p), py = e.getY(p);
          if (pid == lPtr) { lCx = px; lCy = py; } else if (pid == cPtr) { cCx = px; cCy = py; } else if (pid == dPtr) pBtn.put(pid, dpadMask(px, py));
          else if (pBtn.containsKey(pid)) { int sh = hitBtn(px, py); if ((sh & 0x0F00) != 0) pBtn.put(pid, sh); }
        }
      } else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_POINTER_UP || a == MotionEvent.ACTION_CANCEL) {
        if (a == MotionEvent.ACTION_CANCEL) { resetAll(); push(); return true; }
        pBtn.remove(id); if (id == lPtr) { lPtr = -1; lBx = lCx = defLx; lBy = lCy = defLy; } if (id == cPtr) { cPtr = -1; cCx = defCx; cCy = defCy; } if (id == dPtr) dPtr = -1;
      }
      push(); invalidate(); return true;
    }
    void push() {
      int b = 0; for (int m : pBtn.values()) b |= m;
      if (hapticsOn && (b & ~lastBtns) != 0) { try { performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING); } catch (Throwable ignored) {} }
      lastBtns = b; int sx = 0, sy = 0, cx = 0, cy = 0;
      if (lPtr != -1) { float dx = lCx-lBx, dy = lCy-lBy, dist = (float)Math.hypot(dx,dy), mr = lR*0.86f; if (dist > mr) { dx=(dx/dist)*mr; dy=(dy/dist)*mr; } if (Math.hypot(dx/mr,-dy/mr) > 0.10f) { sx = Math.round((dx/mr)*100f); sy = Math.round((-dy/mr)*100f); } }
      if (cPtr != -1) { float dx = cCx-cBx, dy = cCy-cBy, dist = (float)Math.hypot(dx,dy), mr = cR*0.86f; if (dist > mr) { dx=(dx/dist)*mr; dy=(dy/dist)*mr; } if (Math.hypot(dx/mr,-dy/mr) > 0.12f) { cx = Math.round((dx/mr)*100f); cy = Math.round((-dy/mr)*100f); } }
      long pk = ((long)(b&0xFFFF))|(((long)(sx&0xFF))<<16)|(((long)(sy&0xFF))<<24)|(((long)(cx&0xFF))<<32)|(((long)(cy&0xFF))<<40)|(((long)((b&0x0040)!=0?255:0))<<48)|(((long)((b&0x0020)!=0?255:0))<<56);
      try { nativeSetTouchPad(pk); } catch (Throwable ignored) {}
    }
    int aCol(int rgb, float mul) { return (Math.max(0, Math.min(255, Math.round(255f * uiAlpha * mul))) << 24) | (rgb & 0x00FFFFFF); }
    void drawOct(Canvas c, float cx, float cy, float r, Paint p) { Path ph = new Path(); for (int k=0;k<8;k++){double a=Math.toRadians(22.5+k*45.0);float px=cx+(float)(Math.cos(a)*r),py=cy+(float)(Math.sin(a)*r);if(k==0)ph.moveTo(px,py);else ph.lineTo(px,py);} ph.close(); c.drawPath(ph,p); }
    void drawStick(Canvas c, float bx, float by, float kx, float ky, float r, boolean act, int ac, boolean isC) {
      float dx=kx-bx,dy=ky-by,dist=(float)Math.hypot(dx,dy),mr=r*0.82f; if(dist>mr){kx=bx+(dx/dist)*mr;ky=by+(dy/dist)*mr;}
      fp.setStyle(Paint.Style.FILL); fp.setColor(aCol(0x070B14, act?0.56f:0.40f)); drawOct(c,bx,by,r,fp);
      gp.setStrokeWidth((act?2.2f:1.5f)*d); gp.setColor(aCol(act?ac:0x94A3B8, act?0.92f:0.52f)); drawOct(c,bx,by,r,gp);
      gp.setStrokeWidth(1f*d); gp.setColor(aCol(0xCBD5E1, 0.22f)); c.drawCircle(bx,by,r*0.64f,gp);
      float kr=r*(isC?0.46f:0.44f); fp.setColor(aCol(0x050810,0.72f)); c.drawCircle(kx,ky+2f*d,kr,fp);
      fp.setColor(aCol(isC?0xFACC15:(act?0x38BDF8:0x64748B), act?0.78f:0.48f)); c.drawCircle(kx,ky,kr,fp);
      gp.setStrokeWidth(1.6f*d); gp.setColor(aCol(isC?0xFEF08A:0xF8FAFC, act?0.96f:0.65f)); c.drawCircle(kx,ky,kr,gp);
      if(isC){tp.setColor(aCol(0x0F172A,0.92f));tp.setTextSize(13f*d);c.drawText("C",kx,ky+4.5f*d,tp);} else {gp.setStrokeWidth(1f*d);gp.setColor(aCol(0xFFFFFF,0.35f));c.drawCircle(kx,ky,kr*0.62f,gp);}
    }
    @Override protected void onDraw(Canvas c) {
      drawStick(c,lBx,lBy,lCx,lCy,lR,lPtr!=-1,0x38BDF8,false); drawStick(c,defCx,defCy,cCx,cCy,cR,cPtr!=-1,0xFACC15,true);
      float arm=dpadR*0.34f,len=dpadR*0.96f; Path cr=new Path(); cr.moveTo(dpadX-arm,dpadY-len);cr.lineTo(dpadX+arm,dpadY-len);cr.lineTo(dpadX+arm,dpadY-arm);cr.lineTo(dpadX+len,dpadY-arm);cr.lineTo(dpadX+len,dpadY+arm);cr.lineTo(dpadX+arm,dpadY+arm);cr.lineTo(dpadX+arm,dpadY+len);cr.lineTo(dpadX-arm,dpadY+len);cr.lineTo(dpadX-arm,dpadY+arm);cr.lineTo(dpadX-len,dpadY+arm);cr.lineTo(dpadX-len,dpadY-arm);cr.lineTo(dpadX-arm,dpadY-arm);cr.close();
      fp.setStyle(Paint.Style.FILL);fp.setColor(aCol(0x0A101D,0.62f));c.drawPath(cr,fp);rp.setStyle(Paint.Style.STROKE);rp.setStrokeWidth(1.6f*d);rp.setColor(aCol(0x94A3B8,0.65f));c.drawPath(cr,rp);
      int[] ms={8,4,1,2};String[] gl={"\u25B2","\u25BC","\u25C4","\u25BA"};float[] gx={dpadX,dpadX,dpadX-dpadR*0.62f,dpadX+dpadR*0.62f},gy={dpadY-dpadR*0.62f,dpadY+dpadR*0.62f,dpadY,dpadY};tp.setTextSize(11f*d);
      for(int k=0;k<4;k++){boolean dn=(lastBtns&ms[k])!=0;tp.setColor(aCol(dn?0x38BDF8:0xCBD5E1,dn?1f:0.65f));c.drawText(gl[k],gx[k],gy[k]+4f*d,tp);}
      for(int i=0;i<8;i++){Btn b=gb(i);boolean dn=(lastBtns&b.m)!=0;fp.setStyle(Paint.Style.FILL);fp.setColor(aCol(dn?b.col:0x0B111E,dn?0.72f:0.56f));rp.setStyle(Paint.Style.STROKE);rp.setStrokeWidth((dn?2.4f:1.6f)*d);rp.setColor(aCol(b.col,dn?1f:0.72f));tp.setColor(aCol(dn?0xFFFFFF:b.col,dn?1f:0.92f));
        if(b.kind==0){c.drawCircle(b.cx,b.cy,b.r,fp);c.drawCircle(b.cx,b.cy,b.r,rp);gp.setStrokeWidth(1f*d);gp.setColor(aCol(0xFFFFFF,0.18f));c.drawCircle(b.cx,b.cy,b.r-3f*d,gp);tp.setTextSize((i==0?20f:15f)*d);c.drawText(b.lbl,b.cx,b.cy+(i==0?7f:5.5f)*d,tp);}
        else if(b.kind==1){c.save();c.rotate(b.ang,b.cx,b.cy);RectF rf=new RectF(b.cx-b.w*0.5f,b.cy-b.h*0.5f,b.cx+b.w*0.5f,b.cy+b.h*0.5f);c.drawRoundRect(rf,b.h*0.5f,b.h*0.5f,fp);c.drawRoundRect(rf,b.h*0.5f,b.h*0.5f,rp);c.rotate(-b.ang,b.cx,b.cy);tp.setTextSize(14f*d);c.drawText(b.lbl,b.cx,b.cy+5f*d,tp);c.restore();}
        else{RectF rf=new RectF(b.cx-b.w*0.5f,b.cy-b.h*0.5f,b.cx+b.w*0.5f,b.cy+b.h*0.5f);c.drawRoundRect(rf,b.h*0.5f,b.h*0.5f,fp);c.drawRoundRect(rf,b.h*0.5f,b.h*0.5f,rp);tp.setTextSize(11.5f*d);c.drawText(b.lbl,b.cx,b.cy+4f*d,tp);}
      }
      if(editMode){Btn bA=gb(0),bB=gb(1),bX=gb(2),bY=gb(3),bL=gb(4),bR=gb(5),bZ=gb(6),bS=gb(7);float[] px={defLx,defCx,dpadX,bA.cx,bB.cx,bX.cx,bY.cx,bL.cx,bR.cx,bZ.cx,bS.cx},py={defLy,defCy,dpadY,bA.cy,bB.cy,bX.cy,bY.cy,bL.cy,bR.cy,bZ.cy,bS.cy};gp.setStrokeWidth(2.2f*d);gp.setColor(0xFFFACC15);c.drawCircle(java.lang.reflect.Array.getFloat(px,selIdx),java.lang.reflect.Array.getFloat(py,selIdx),38f*d,gp);tp.setColor(0xFFFACC15);tp.setTextSize(13f*d);c.drawText("DRAG ANY CONTROL \u2022 TAP DONE WHEN FINISHED",getWidth()*0.5f,getHeight()*0.48f,tp);}
    }
  }
}