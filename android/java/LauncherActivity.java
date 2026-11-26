package dev.foxhollow.android;
import android.app.*; import android.content.*; import android.content.res.Configuration;
import android.graphics.*; import android.graphics.drawable.*; import android.net.*;
import android.opengl.GLES20; import javax.microedition.khronos.egl.*; import android.os.*; import android.util.*; import android.view.*; import android.widget.*;
import java.io.*; import java.lang.reflect.Array; import java.util.zip.*;
public class LauncherActivity extends Activity {
  static final int REQ_ISO = 101, REQ_DRV = 102; static boolean didWarmReopen = false; static final String PREFS = "foxhollow_pocket_prefs_v11";
  File baseDir; TextView isoBadge, isoTxt, drvBadge, drvTxt, progTxt, topIsoPill, topDrvPill;
  FrameLayout progBar; View progFill; Button playBtn; int curTab = 0; float scaleH = 1f; String detectedGpu = ""; volatile boolean hdDownloading = false;
  final TextView[] tabBtns = (TextView[]) Array.newInstance(TextView.class, 4);
  final View[] tabPages = (View[]) Array.newInstance(View.class, 5);
  final String[] optLabels = {"Resolution Scale", "Aspect Ratio", "V-Sync", "Anisotropic", "Graphics API", "Language", "Overlay Position", "Overlay Size", "Driver Mode", "Touch Control Scale"};
  final String[][] optItems = {{"4.0x (2160p)", "3.0x (1440p)", "2.5x", "2.0x (1080p)", "1.5x", "1.0x (Native)"}, {"Widescreen (16:9)", "Original (4:3)"}, {"Enabled", "Disabled"}, {"16x", "8x", "4x", "2x", "1x"}, {"Vulkan", "OpenGL ES"}, {"English", "Francais", "Deutsch", "Italiano", "Espanol"}, {"Top-Left", "Top-Right", "Bottom-Left", "Bottom-Right"}, {"Compact", "Medium", "Large"}, {"Auto (Recommended)", "Bundled Turnip (A6xx/A7xx)", "Bundled Turnip (A8xx)", "Bundled Qualcomm (v840)", "System Default", "Custom (.zip)"}, {"85% (Compact)", "100% (Standard)", "115% (Large)", "130% (XL)"}};
  final int[] optIdx = {4, 1, 0, 2, 0, 0, 0, 1, 0, 1}; final TextView[] optVal = (TextView[]) Array.newInstance(TextView.class, 10);
  final boolean[] togState = {false, false, false, false, false, false, false, false, false, false, false, true, false};
  final FrameLayout[] togTrack = (FrameLayout[]) Array.newInstance(FrameLayout.class, 13);
  final View[] togThumb = (View[]) Array.newInstance(View.class, 13);

  int spx(float baseDp) { return Math.max(1, Math.round(baseDp * getResources().getDisplayMetrics().density * scaleH)); }
  GradientDrawable boxBg(int top, int bot, float radDp, float sw, int sc) {
    GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{top, bot});
    d.setCornerRadius(spx(radDp)); if (sc != 0) d.setStroke(Math.max(1, Math.round(sw * getResources().getDisplayMetrics().density)), sc); return d;
  }
  StateListDrawable focusBg(int top, int bot, float radDp, int idleStroke, int focStroke) {
    StateListDrawable s = new StateListDrawable();
    int[] fs = (int[]) Array.newInstance(int.class, 1); Array.setInt(fs, 0, android.R.attr.state_focused);
    int[] ps = (int[]) Array.newInstance(int.class, 1); Array.setInt(ps, 0, android.R.attr.state_pressed);
    int[] es = (int[]) Array.newInstance(int.class, 0);
    s.addState(fs, boxBg(top + 0x10141C, bot + 0x10141C, radDp, 2.2f, focStroke));
    s.addState(ps, boxBg(top + 0x0A0E14, bot + 0x0A0E14, radDp, 2.0f, focStroke));
    s.addState(es, boxBg(top, bot, radDp, 1.0f, idleStroke));
    return s;
  }
  void prep(View v) { v.setFocusable(true); if (Build.VERSION.SDK_INT >= 26) v.setDefaultFocusHighlightEnabled(false); }
  String aspectStr(int w, int h) { float r = (float) Math.max(w, h) / Math.max(1f, (float) Math.min(w, h)); return r >= 2.25f ? "21:9" : r >= 2.08f ? "20:9" : r >= 1.92f ? "19.5:9" : r >= 1.70f ? "16:9" : r >= 1.55f ? "16:10" : r >= 1.42f ? "3:2" : "4:3"; }
  File resolveStorageDir() { File pub = new File(Environment.getExternalStorageDirectory(), "pocket-hollow"); if (Build.VERSION.SDK_INT < 30 || Environment.isExternalStorageManager()) { pub.mkdirs(); if (pub.canWrite()) return pub; } File a = getExternalFilesDir(null); return a != null ? a : getFilesDir(); }
  void ensureDirs() { baseDir.mkdirs(); new File(baseDir, "mods").mkdirs(); new File(baseDir, "cache").mkdirs(); }
  void setProgPermille(int pm) { if (progBar == null || progFill == null) return; final int cl = Math.max(0, Math.min(1000, pm)); progBar.post(() -> { int tw = progBar.getWidth(); if (tw <= 0) tw = spx(220); int fw = cl <= 0 ? spx(8) : Math.max(spx(8), (int)((tw * (long)cl)/1000L)); ViewGroup.LayoutParams p = progFill.getLayoutParams(); p.width = Math.min(tw, fw); progFill.setLayoutParams(p); }); }
  void selectTab(int t) {
    curTab = (t + 5) % 5;
    for (int p = 0; p < 5; p++) { View pg = (View) Array.get(tabPages, p); if (pg != null) pg.setVisibility(p == curTab ? View.VISIBLE : View.GONE); }
    for (int i = 0; i < 4; i++) {
      TextView tb = (TextView) Array.get(tabBtns, i); boolean sel = ((i + 1) == curTab);
      if (tb != null) { tb.setTextColor(sel ? 0xFFFACC15 : 0xFF94A3B8); tb.setBackground(sel ? boxBg(0xFF1E293B, 0xFF151D2E, 7, 1.2f, 0xFFF59E0B) : new ColorDrawable(0)); }
    }
    TextView tb0 = (TextView) Array.get(tabBtns, 0); if (tb0 != null && tb0.getParent() instanceof View) ((View) tb0.getParent()).setNextFocusDownId(curTab == 0 ? 1001 : View.NO_ID); if (curTab == 0 && playBtn != null) playBtn.clearFocus(); else if (curTab > 0) { TextView atb = (TextView) Array.get(tabBtns, curTab - 1); if (atb != null && atb.getParent() instanceof View) ((View) atb.getParent()).requestFocus(); }
  }
  @Override public boolean dispatchKeyEvent(KeyEvent ev) {
    if (ev.getAction() == KeyEvent.ACTION_DOWN && ev.getRepeatCount() == 0) {
      int kc = ev.getKeyCode();
      if (kc == KeyEvent.KEYCODE_BUTTON_L1) { selectTab(curTab - 1); return true; }
      if (kc == KeyEvent.KEYCODE_BUTTON_R1) { selectTab(curTab + 1); return true; }
    }
    return super.dispatchKeyEvent(ev);
  }
  @Override public boolean onKeyDown(int kc, KeyEvent ev) { if ((kc == KeyEvent.KEYCODE_BUTTON_B || kc == KeyEvent.KEYCODE_BACK) && curTab != 0) { selectTab(0); return true; } return super.onKeyDown(kc, ev); }
  void hideSystemBars() {
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
    if (Build.VERSION.SDK_INT >= 30) {
      WindowInsetsController ic = getWindow().getInsetsController();
      if (ic != null) { ic.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars()); ic.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE); }
    } else {
      getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }
  }
  @Override public void onWindowFocusChanged(boolean hasFocus) { super.onWindowFocusChanged(hasFocus); if (hasFocus) { hideSystemBars(); View dv = getWindow().getDecorView(); if (dv != null) { dv.requestLayout(); dv.postDelayed(() -> { hideSystemBars(); dv.requestLayout(); }, 180); } } }
  @Override protected void onCreate(Bundle b) {
    super.onCreate(b); getWindow().setStatusBarColor(0x00000000); getWindow().setNavigationBarColor(0x00000000);
    if (Build.VERSION.SDK_INT >= 30 && !Environment.isExternalStorageManager()) {
      new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert).setTitle("Storage Access Required")
        .setMessage("Allow All files access for /storage/emulated/0/pocket-hollow.")
        .setPositiveButton("Grant Access", (d, w) -> { try { startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + getPackageName()))); } catch (Throwable ignored) {} })
        .setNegativeButton("Later", null).show();
    }
    baseDir = resolveStorageDir(); ensureDirs(); buildUi();
  }
  @Override public void onConfigurationChanged(Configuration c) { super.onConfigurationChanged(c); savePrefs(); buildUi(); }
  @Override protected void onResume() { super.onResume(); File n = resolveStorageDir(); if (baseDir == null || !n.getAbsolutePath().equals(baseDir.getAbsolutePath())) { baseDir = n; ensureDirs(); } refresh(); }
  Bitmap loadHeroArt() {
    try { File f = new File(baseDir, "hero_banner.png"); if (f.exists()) return BitmapFactory.decodeFile(f.getAbsolutePath()); } catch (Throwable ignored) {}
    try { int id = getResources().getIdentifier("hero_banner", "drawable", getPackageName()); if (id != 0) return BitmapFactory.decodeResource(getResources(), id); } catch (Throwable ignored) {}
    try (InputStream is = getAssets().open("hero_banner.png")) { return BitmapFactory.decodeStream(is); } catch (Throwable ignored) {}
    return null;
  }
  void buildUi() {
    DisplayMetrics dm = getResources().getDisplayMetrics();
    int scrW = dm.widthPixels, scrH = dm.heightPixels;
    if (Build.VERSION.SDK_INT >= 30) { android.graphics.Rect rb = getWindowManager().getCurrentWindowMetrics().getBounds(); if (rb.width() > 0 && rb.height() > 0) { scrW = rb.width(); scrH = rb.height(); } }
    int landW = Math.max(scrW, scrH), landH = Math.min(scrW, scrH);
    int aspChoice = getSharedPreferences(PREFS, MODE_PRIVATE).getInt("o1", 1);
    Array.setInt(optIdx, 1, aspChoice);
    float targetAsp = (aspChoice == 0) ? (16f / 9f) : (4f / 3f);
    int stageH = landH;
    int stageW = Math.min(landW, Math.round(stageH * targetAsp));
    float hDp = stageH / dm.density;
    scaleH = Math.max(0.70f, Math.min(1.12f, hDp / 435f));
    float leftW = (aspChoice == 0) ? 0.61f : 0.54f;
    FrameLayout outer = new FrameLayout(this) { @Override protected void onMeasure(int wSpec, int hSpec) { int mw = MeasureSpec.getSize(wSpec), mh = MeasureSpec.getSize(hSpec); if (mw > 0 && mh > 0 && getChildCount() > 0) { View ch = getChildAt(0); FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) ch.getLayoutParams(); int sh = mh, sw = Math.min(mw, Math.round(sh * targetAsp)); if (lp.width != sw || lp.height != sh) { lp.width = sw; lp.height = sh; } } super.onMeasure(wSpec, hSpec); } }; outer.setBackgroundColor(0xFF000000); outer.setFitsSystemWindows(true);
    LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
    root.setBackgroundColor(0xFF090C12); root.setPadding(spx(10), spx(8), spx(10), spx(8)); outer.addView(root, new FrameLayout.LayoutParams(stageW, stageH, Gravity.CENTER));
    LinearLayout hdr = new LinearLayout(this); hdr.setOrientation(LinearLayout.HORIZONTAL); hdr.setGravity(Gravity.CENTER_VERTICAL); hdr.setPadding(spx(2), 0, spx(2), spx(6));
    TextView title = new TextView(this); title.setText("POCKET-HOLLOW"); title.setTextColor(0xFFF3C669); title.setTextSize(TypedValue.COMPLEX_UNIT_PX, spx(16)); title.setTypeface(Typeface.DEFAULT_BOLD); hdr.addView(title);
    topIsoPill = new TextView(this); topIsoPill.setTextColor(0xFF34D399); topIsoPill.setTextSize(TypedValue.COMPLEX_UNIT_PX, spx(9)); topIsoPill.setTypeface(Typeface.DEFAULT_BOLD); topIsoPill.setBackground(boxBg(0xFF102A24, 0xFF0B1F1B, 12, 1f, 0xFF10B981)); topIsoPill.setPadding(spx(8), spx(2), spx(8), spx(2));
    LinearLayout.LayoutParams lpI = new LinearLayout.LayoutParams(-2, -2); lpI.setMargins(spx(8), 0, 0, 0); hdr.addView(topIsoPill, lpI);
    topDrvPill = new TextView(this); topDrvPill.setTextColor(0xFF93C5FD); topDrvPill.setTextSize(TypedValue.COMPLEX_UNIT_PX, spx(9)); topDrvPill.setTypeface(Typeface.DEFAULT_BOLD); topDrvPill.setBackground(boxBg(0xFF152238, 0xFF0F172A, 12, 1f, 0xFF3B82F6)); topDrvPill.setPadding(spx(8), spx(2), spx(8), spx(2));
    LinearLayout.LayoutParams lpD = new LinearLayout.LayoutParams(-2, -2); lpD.setMargins(spx(6), 0, 0, 0); hdr.addView(topDrvPill, lpD);
    hdr.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
    String hwTag = (Build.MODEL != null && !Build.MODEL.isEmpty()) ? Build.MODEL.toUpperCase() + " - " : "";
    TextView asp = new TextView(this); asp.setText(hwTag + (aspChoice == 0 ? "16:9" : "4:3")); asp.setTextColor(0xFF94A3B8); asp.setTextSize(TypedValue.COMPLEX_UNIT_PX, spx(9)); asp.setTypeface(Typeface.DEFAULT_BOLD); asp.setBackground(boxBg(0xFF131926, 0xFF131926, 7, 1f, 0xFF253047)); asp.setPadding(spx(7), spx(2), spx(7), spx(2)); hdr.addView(asp);
    root.addView(hdr);
    LinearLayout tabStrip = new LinearLayout(this); tabStrip.setOrientation(LinearLayout.HORIZONTAL);
    tabStrip.setBackground(boxBg(0xFF0E131F, 0xFF0B0F19, 10, 1f, 0xFF1F2A40)); tabStrip.setPadding(spx(3), spx(3), spx(3), spx(3));
    LinearLayout.LayoutParams lpTs = new LinearLayout.LayoutParams(-1, -2); lpTs.setMargins(0, 0, 0, spx(7));
    String[] tabNames = {"GRAPHICS", "CONTROLS", "MODS", "STATS"};
    for (int t = 0; t < 4; t++) {
      final int idx = t + 1; LinearLayout cell = new LinearLayout(this); cell.setOrientation(LinearLayout.VERTICAL); cell.setGravity(Gravity.CENTER);
      prep(cell);
      cell.setBackground(focusBg(0x00000000, 0x00000000, 8, 0, 0xFFF59E0B));
      cell.setOnClickListener(v -> selectTab(curTab == idx ? 0 : idx));
      TextView tb = new TextView(this); tb.setText((String) Array.get(tabNames, t)); tb.setGravity(Gravity.CENTER);
      tb.setTextSize(TypedValue.COMPLEX_UNIT_PX, spx(11)); tb.setTypeface(Typeface.DEFAULT_BOLD); tb.setLetterSpacing(0.06f);
      tb.setLayoutParams(new LinearLayout.LayoutParams(-1, -2)); tb.setPadding(spx(8), spx(5), spx(8), spx(5));
      Array.set(tabBtns, t, tb); cell.addView(tb); tabStrip.addView(cell, new LinearLayout.LayoutParams(0, -2, 1f));
    }
    root.addView(tabStrip, lpTs);

    FrameLayout pageHost = new FrameLayout(this); root.addView(pageHost, new LinearLayout.LayoutParams(-1, 0, 1f));
    LinearLayout playPage = new LinearLayout(this); playPage.setOrientation(LinearLayout.HORIZONTAL);
    Array.set(tabPages, 0, playPage); pageHost.addView(playPage, new FrameLayout.LayoutParams(-1, -1));

    FrameLayout heroCard = new FrameLayout(this); heroCard.setBackground(boxBg(0xFF141D2E, 0xFF0C121D, 14, 1.4f, 0xFFF59E0B));
    Bitmap art = loadHeroArt();
    if (art != null) {
      ImageView iv = new ImageView(this); iv.setImageBitmap(art); iv.setScaleType(ImageView.ScaleType.FIT_CENTER); heroCard.setClipToOutline(true); heroCard.setOutlineProvider(ViewOutlineProvider.BACKGROUND); heroCard.setPadding(spx(3), spx(3), spx(3), spx(3));
      heroCard.addView(iv, new FrameLayout.LayoutParams(-1, -1));
    } else {
      TextView fb = new TextView(this); fb.setText("POCKET-HOLLOW\nSTAR FOX ADVENTURES"); fb.setGravity(Gravity.CENTER);
      fb.setTextColor(0xFFF3C669); fb.setTextSize(TypedValue.COMPLEX_UNIT_PX, spx(22)); fb.setTypeface(Typeface.DEFAULT_BOLD);
      heroCard.addView(fb, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
    }
    LinearLayout.LayoutParams lpHero = new LinearLayout.LayoutParams(0, -1, leftW); lpHero.setMargins(0, 0, spx(8), 0);
    playPage.addView(heroCard, lpHero);

    LinearLayout rightCol = new LinearLayout(this); rightCol.setOrientation(LinearLayout.VERTICAL);
    playPage.addView(rightCol, new LinearLayout.LayoutParams(0, -1, 1f - leftW));

    LinearLayout dCard = box(rightCol, 0.85f, true);
    isoBadge = cardHdr(dCard, "GAME DISC", "MISSING", 0xFFFBBF24);
    isoTxt = bodyTxt(dCard, "No legal disc selected.");
    Button bIso = secBtn("Select Legal ISO / RVZ"); bIso.setNextFocusLeftId(1001); bIso.setOnClickListener(v -> promptIso()); dCard.addView(bIso);

    LinearLayout vCard = box(rightCol, 1.25f, true);
    drvBadge = cardHdr(vCard, "VULKAN DRIVER", "AUTO", 0xFF60A5FA);
    drvTxt = bodyTxt(vCard, "Auto GPU Driver");
    optRow(vCard, "Driver Mode", 8); vCard.getChildAt(vCard.getChildCount() - 1).setNextFocusLeftId(1001);
    Button bDrv = secBtn("Import Driver (.zip)"); bDrv.setNextFocusLeftId(1001);
    bDrv.setOnClickListener(v -> { Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT); i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("*/*"); startActivityForResult(i, REQ_DRV); });
    vCard.addView(bDrv);

    LinearLayout sCard = box(rightCol, 0.90f, false);
    cardHdr(sCard, "LAUNCH STATUS", "READY", 0xFF34D399);
    if (topDrvPill != null && topDrvPill.getParent() instanceof LinearLayout) {
      LinearLayout topRow = (LinearLayout) topDrvPill.getParent();
      TextView bugPill = new TextView(this);
      bugPill.setText("REPORT BUG");
      bugPill.setTextColor(0xFFFACC15);
      bugPill.setTextSize(TypedValue.COMPLEX_UNIT_PX, spx(9f));
      bugPill.setTypeface(Typeface.DEFAULT_BOLD);
      bugPill.setBackground(boxBg(0xFF1E293B, 0xFF151D2E, 99, 1.0f, 0xFFF59E0B));
      bugPill.setPadding(spx(8), spx(2), spx(8), spx(2));
      LinearLayout.LayoutParams lpBug = new LinearLayout.LayoutParams(-2, -2);
      lpBug.setMargins(spx(6), 0, 0, 0);
      prep(bugPill);
      bugPill.setOnFocusChangeListener((v, has) -> bugPill.setBackground(has ? boxBg(0xFF334155, 0xFF1E293B, 99, 1.5f, 0xFFFFFFFF) : boxBg(0xFF1E293B, 0xFF151D2E, 99, 1.0f, 0xFFF59E0B)));
      bugPill.setOnClickListener(v -> openBugReport());
      topRow.addView(bugPill, topRow.indexOfChild(topDrvPill) + 1, lpBug);
    }
    progBar = new FrameLayout(this); progBar.setBackground(boxBg(0xFF0A0E17, 0xFF0A0E17, 6, 1f, 0xFF1F2A40));
    LinearLayout.LayoutParams lpPb = new LinearLayout.LayoutParams(-1, spx(8)); lpPb.setMargins(0, spx(3), 0, spx(3));
    progFill = new View(this); progFill.setBackground(boxBg(0xFFF59E0B, 0xFFD97706, 5, 0f, 0)); progBar.addView(progFill, new FrameLayout.LayoutParams(-1, -1));
    sCard.addView(progBar, lpPb);
    progTxt = new TextView(this); progTxt.setText("Ready for Launch"); progTxt.setGravity(Gravity.CENTER); progTxt.setTextColor(0xFFCBD5E1); progTxt.setTextSize(TypedValue.COMPLEX_UNIT_PX, spx(10)); progTxt.setTypeface(Typeface.DEFAULT_BOLD); sCard.addView(progTxt);
    playBtn = new Button(this); playBtn.setId(1001); playBtn.setText("LAUNCH GAME"); playBtn.setTextColor(0xFFF1F5F9); playBtn.setTextSize(TypedValue.COMPLEX_UNIT_PX, spx(13)); playBtn.setTypeface(Typeface.DEFAULT_BOLD);
    playBtn.setMinHeight(0); playBtn.setMinimumHeight(0); playBtn.setPadding(spx(16), spx(7), spx(16), spx(7)); prep(playBtn);
    playBtn.setBackground(focusBg(0xFF1E293B, 0xFF151D2E, 10, 0xFF334155, 0xFFFFFFFF)); playBtn.setOnFocusChangeListener((v, has) -> { playBtn.setTextColor(has ? 0xFF090C12 : 0xFFF1F5F9); playBtn.setBackground(has ? boxBg(0xFFFBBF24, 0xFFD97706, 10, 2.2f, 0xFFFFFFFF) : boxBg(0xFF1E293B, 0xFF151D2E, 10, 1.0f, 0xFF334155)); });
    LinearLayout.LayoutParams lpPlay = new LinearLayout.LayoutParams(-1, -2); lpPlay.setMargins(0, spx(5), 0, 0);
    playBtn.setOnClickListener(v -> launch()); sCard.addView(playBtn, lpPlay);
 if (hdDownloading) { playBtn.setEnabled(false); playBtn.setText("DOWNLOADING..."); }
    LinearLayout gPage = makeTabPage(pageHost, 1);
    optRow(gPage, "Resolution Scale", 0); optRow(gPage, "Aspect Ratio", 1); optRow(gPage, "Graphics API", 4);
    optRow(gPage, "Anisotropic", 3); optRow(gPage, "Language", 5);

    LinearLayout cPage = makeTabPage(pageHost, 2); LinearLayout mPage = makeTabPage(pageHost, 3);
    iosSwitchRow(mPage, "HD Texture Pack (4x)", 0); iosSwitchRow(mPage, "Water Reflections", 1); iosSwitchRow(mPage, "Hold-A Auto-Mash", 2); buildControlsTab(cPage);

    LinearLayout sPage = makeTabPage(pageHost, 4);
    iosSwitchRow(sPage, "FPS Counter", 3); iosSwitchRow(sPage, "Frame Time (ms)", 6); iosSwitchRow(sPage, "CPU / SoC Temp (Â°C)", 4);
    iosSwitchRow(sPage, "Battery % & Wattage", 7); iosSwitchRow(sPage, "RAM Memory Usage", 8); iosSwitchRow(sPage, "Peak CPU Clock (GHz)", 9);
    optRow(sPage, "Overlay Position", 6); optRow(sPage, "Overlay Size", 7);

    setContentView(outer); loadPrefs(); refresh(); selectTab(curTab); outer.post(this::hideSystemBars);
  }
  LinearLayout makeTabPage(FrameLayout host, int idx) {
    ScrollView sv = new ScrollView(this); sv.setFillViewport(true); sv.setVisibility(View.GONE);
    LinearLayout p = new LinearLayout(this); p.setOrientation(LinearLayout.VERTICAL); p.setPadding(spx(4), spx(2), spx(4), spx(6));
    sv.addView(p, new FrameLayout.LayoutParams(-1, -2)); Array.set(tabPages, idx, sv); host.addView(sv, new FrameLayout.LayoutParams(-1, -1)); return p;
  }
  LinearLayout box(LinearLayout p, float w, boolean gap) {
    LinearLayout b = new LinearLayout(this); b.setOrientation(LinearLayout.VERTICAL); b.setGravity(Gravity.CENTER_VERTICAL);
    b.setBackground(boxBg(0xFF121826, 0xFF0E131F, 12, 1f, 0xFF1F2A40)); b.setPadding(spx(11), spx(7), spx(11), spx(7));
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, 0, w); if (gap) lp.setMargins(0, 0, 0, spx(5)); p.addView(b, lp); return b;
  }
  TextView cardHdr(LinearLayout p, String t, String bdg, int c) {
    LinearLayout r = new LinearLayout(this); r.setOrientation(LinearLayout.HORIZONTAL); r.setGravity(Gravity.CENTER_VERTICAL);
    TextView tv = new TextView(this); tv.setText(t); tv.setTextColor(0xFF94A3B8); tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, spx(9.5f)); tv.setTypeface(Typeface.DEFAULT_BOLD); tv.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
    TextView bg = new TextView(this); bg.setText(bdg); bg.setTextColor(c); bg.setTextSize(TypedValue.COMPLEX_UNIT_PX, spx(9)); bg.setTypeface(Typeface.DEFAULT_BOLD); bg.setBackground(boxBg(0xFF1E293B, 0xFF1E293B, 6, 0f, 0)); bg.setPadding(spx(6), spx(1), spx(6), spx(1));
    r.addView(tv); r.addView(bg); p.addView(r); return bg;
  }
  TextView bodyTxt(LinearLayout p, String t) {
    TextView tv = new TextView(this); tv.setText(t); tv.setTextColor(0xFFE2E8F0); tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, spx(11)); tv.setPadding(0, spx(2), 0, spx(4)); p.addView(tv); return tv;
  }
  Button secBtn(String t) {
    Button b = new Button(this); b.setText(t); b.setTextColor(0xFFF1F5F9); b.setTextSize(TypedValue.COMPLEX_UNIT_PX, spx(11)); b.setTypeface(Typeface.DEFAULT_BOLD);
    b.setMinHeight(0); b.setMinimumHeight(0); b.setPadding(spx(10), spx(6), spx(10), spx(6)); prep(b);
    b.setBackground(focusBg(0xFF1E293B, 0xFF151D2E, 8, 0xFF334155, 0xFFF59E0B)); b.setLayoutParams(new LinearLayout.LayoutParams(-1, -2)); return b;
  }
  void showDropdown(final TextView anchor, final int k) {
    LinearLayout menu = new LinearLayout(this); menu.setOrientation(LinearLayout.VERTICAL); menu.setBackground(boxBg(0xFF141C2B, 0xFF0E1420, 10, 1.4f, 0xFFF59E0B)); menu.setPadding(spx(6), spx(6), spx(6), spx(6));
    final PopupWindow pop = new PopupWindow(menu, -2, -2, true); pop.setElevation(spx(14));
    String[] row = (String[]) Array.get(optItems, k);
    for (int j = 0; j < row.length; j++) {
      final int idx = j; TextView item = new TextView(this); String lbl = (String) Array.get(row, j); item.setText(lbl);
      boolean sel = (idx == Array.getInt(optIdx, k)); item.setTextColor(sel ? 0xFFFACC15 : 0xFFF1F5F9); item.setTypeface(sel ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
      item.setTextSize(TypedValue.COMPLEX_UNIT_PX, spx(12)); item.setPadding(spx(14), spx(8), spx(18), spx(8)); prep(item);
      item.setBackground(focusBg(sel ? 0xFF1E293B : 0xFF141C2B, 0xFF182236, 8, 0, 0xFFF59E0B));
      item.setOnClickListener(v -> { Array.setInt(optIdx, k, idx); anchor.setText(lbl + " â–¾"); savePrefs(); pop.dismiss(); if (k == 1) { buildUi(); return; } refresh(); });
      menu.addView(item); if (sel) item.post(item::requestFocus);
    }
    menu.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
    int mh = menu.getMeasuredHeight();
    int[] loc = (int[]) Array.newInstance(int.class, 2); anchor.getLocationOnScreen(loc);
    int sh = getResources().getDisplayMetrics().heightPixels;
    int yOff = (Array.getInt(loc, 1) + anchor.getHeight() + mh + spx(12) > sh) ? -(anchor.getHeight() + mh + spx(6)) : spx(4);
    pop.showAsDropDown(anchor, 0, yOff);
  }
  void optRow(LinearLayout p, String l, final int k) {
    LinearLayout r = new LinearLayout(this); r.setOrientation(LinearLayout.HORIZONTAL); r.setGravity(Gravity.CENTER_VERTICAL); prep(r);
    r.setBackground(focusBg(0xFF121826, 0xFF0E131F, 10, 0xFF1F2A40, 0xFFF59E0B)); r.setPadding(spx(12), spx(7), spx(12), spx(7));
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.setMargins(0, 0, 0, spx(5));
    TextView tk = new TextView(this); tk.setText(l); tk.setTextColor(0xFFF1F5F9); tk.setTextSize(TypedValue.COMPLEX_UNIT_PX, spx(11.5f)); tk.setTypeface(Typeface.DEFAULT_BOLD); tk.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
    TextView tv = new TextView(this); tv.setTextColor(0xFFFACC15); tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, spx(11.5f)); tv.setTypeface(Typeface.DEFAULT_BOLD);
    r.setOnClickListener(w -> showDropdown(tv, k)); Array.set(optVal, k, tv); r.addView(tk); r.addView(tv); p.addView(r, lp);
  }
  void setIosSwitchVisual(int idx, boolean on, boolean anim) {
    Array.setBoolean(togState, idx, on); FrameLayout tr = (FrameLayout) Array.get(togTrack, idx); View th = (View) Array.get(togThumb, idx);
    if (tr != null && th != null) { tr.setBackground(on ? boxBg(0xFF10B981, 0xFF059669, 13, 1f, 0xFF34D399) : boxBg(0xFF1E293B, 0xFF162033, 13, 1f, 0xFF334155)); float tx = on ? spx(18) : 0f; if (anim) th.animate().translationX(tx).setDuration(140).start(); else th.setTranslationX(tx); }
  }
  void iosSwitchRow(LinearLayout p, String l, final int idx) {
    LinearLayout r = new LinearLayout(this); r.setOrientation(LinearLayout.HORIZONTAL); r.setGravity(Gravity.CENTER_VERTICAL); prep(r);
    r.setBackground(focusBg(0xFF121826, 0xFF0E131F, 10, 0xFF1F2A40, 0xFFF59E0B)); r.setPadding(spx(12), spx(7), spx(12), spx(7));
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.setMargins(0, 0, 0, spx(5));
    TextView tk = new TextView(this); tk.setText(l); tk.setTextColor(0xFFF1F5F9); tk.setTextSize(TypedValue.COMPLEX_UNIT_PX, spx(11.5f)); tk.setTypeface(Typeface.DEFAULT_BOLD); tk.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
    FrameLayout track = new FrameLayout(this); track.setLayoutParams(new LinearLayout.LayoutParams(spx(42), spx(24))); track.setPadding(spx(3), spx(3), spx(3), spx(3));
    View thumb = new View(this); thumb.setLayoutParams(new LinearLayout.LayoutParams(spx(18), spx(18))); thumb.setBackground(boxBg(0xFFFFFFFF, 0xFFE2E8F0, 9, 0f, 0)); track.addView(thumb);
    Array.set(togTrack, idx, track); Array.set(togThumb, idx, thumb);
    r.setOnClickListener(w -> { setIosSwitchVisual(idx, !Array.getBoolean(togState, idx), true); savePrefs(); refresh(); });
    r.addView(tk); r.addView(track); p.addView(r, lp);
  }
  String detectGpuRenderer() {
    if (detectedGpu != null && !detectedGpu.isEmpty()) return detectedGpu;
    try {
      EGL10 egl = (EGL10) EGLContext.getEGL(); EGLDisplay dpy = egl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY);
      egl.eglInitialize(dpy, (int[]) Array.newInstance(int.class, 2));
      Object cs = Array.newInstance(int.class, 3); Array.setInt(cs, 0, EGL10.EGL_RENDERABLE_TYPE); Array.setInt(cs, 1, 4); Array.setInt(cs, 2, EGL10.EGL_NONE);
      Object cfgs = Array.newInstance(EGLConfig.class, 1); Object num = Array.newInstance(int.class, 1);
      egl.eglChooseConfig(dpy, (int[]) cs, (EGLConfig[]) cfgs, 1, (int[]) num); EGLConfig cfg = (EGLConfig) Array.get(cfgs, 0);
      if (cfg == null) { egl.eglTerminate(dpy); detectedGpu = "Adreno"; return detectedGpu; }
      Object ca = Array.newInstance(int.class, 3); Array.setInt(ca, 0, 0x3098); Array.setInt(ca, 1, 2); Array.setInt(ca, 2, EGL10.EGL_NONE);
      EGLContext ctx = egl.eglCreateContext(dpy, cfg, EGL10.EGL_NO_CONTEXT, (int[]) ca);
      Object sa = Array.newInstance(int.class, 5); Array.setInt(sa, 0, EGL10.EGL_WIDTH); Array.setInt(sa, 1, 1); Array.setInt(sa, 2, EGL10.EGL_HEIGHT); Array.setInt(sa, 3, 1); Array.setInt(sa, 4, EGL10.EGL_NONE);
      EGLSurface surf = egl.eglCreatePbufferSurface(dpy, cfg, (int[]) sa); egl.eglMakeCurrent(dpy, surf, surf, ctx);
      String r = GLES20.glGetString(GLES20.GL_RENDERER);
      egl.eglMakeCurrent(dpy, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_CONTEXT);
      egl.eglDestroySurface(dpy, surf); egl.eglDestroyContext(dpy, ctx); egl.eglTerminate(dpy);
      if (r != null) detectedGpu = r.trim();
    } catch (Throwable ignored) {}
    if (detectedGpu == null || detectedGpu.isEmpty()) detectedGpu = Build.HARDWARE != null ? Build.HARDWARE : "Unknown GPU";
    return detectedGpu;
  }
  int detectGpuTier() {
    String g = detectGpuRenderer().toLowerCase();
    if (g.contains("adreno")) {
      if (g.contains("740") || g.contains("750") || g.contains("810") || g.contains("820") || g.contains("825") || g.contains("830") || g.contains("840") || g.contains(" 8")) return 2;
      if (g.contains("640") || g.contains("650") || g.contains("660") || g.contains("710") || g.contains("720") || g.contains("725") || g.contains("730") || g.contains("732") || g.contains("735")) return 1;
      return 0;
    }
    if (g.contains("immortalis") || g.contains("g715") || g.contains("g720") || g.contains("g925") || g.contains("xclipse 940") || g.contains("xclipse 950")) return 2;
    if (g.contains("g610") || g.contains("g615") || g.contains("g710") || g.contains("g77") || g.contains("g78") || g.contains("xclipse")) return 1;
    return 0;
  }
  String resolveAutoDriverAsset() {
    String g = detectGpuRenderer().toLowerCase();
    if (g.contains("adreno")) {
      if (g.contains("618") || g.contains("619") || g.contains("620") || g.contains("630") || g.contains("640") || g.contains("642") || g.contains("650") || g.contains("660") || g.contains("740") || g.contains("750")) {
        return "drivers/turnip_ci.zip";
      }
      return null;
    }
    return null;
  }
  File prepareActiveDriver() throws Exception {
    int dMode = Array.getInt(optIdx, 8); if (dMode == 4) return null;
    SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
    File bg = new File(new File(baseDir, "cache"), "boot_guard.flag");
    if (dMode == 0 && bg.exists()) { bg.delete(); sp.edit().putBoolean("auto_force_sys", true).apply(); }
    if (dMode == 0 && sp.getBoolean("auto_force_sys", false)) return null;
    if (dMode == 5) { File icd = new File(new File(getCodeCacheDir(), "custom_driver"), "vk_icd.json"); return icd.exists() ? icd : null; }
    String ap = dMode == 0 ? resolveAutoDriverAsset() : (dMode == 1 ? "drivers/turnip_ci.zip" : (dMode == 2 ? "drivers/turnip_a8xx.zip" : "drivers/qualcomm_adreno.zip"));
    if (ap == null) return null;
    File dd = new File(getCodeCacheDir(), "bundled_driver_" + dMode); File icd = new File(dd, "vk_icd.json"); if (icd.exists()) return icd;
    del(dd); dd.mkdirs(); String so = null;
    try (ZipInputStream z = new ZipInputStream(new BufferedInputStream(getAssets().open(ap)))) {
      ZipEntry e; byte[] b = (byte[]) Array.newInstance(byte.class, 65536);
      while ((e = z.getNextEntry()) != null) {
        if (e.isDirectory()) continue; String nm = new File(e.getName()).getName(); File out = new File(dd, nm);
        try (FileOutputStream fo = new FileOutputStream(out)) { int n; while ((n = z.read(b)) > 0) fo.write(b, 0, n); }
        if (nm.endsWith(".so")) { out.setReadable(true, false); out.setExecutable(true, false); so = nm; }
      }
    }
    if (so == null) return null;
    try (FileWriter fw = new FileWriter(icd)) { fw.write("{\"file_format_version\":\"1.0.0\",\"ICD\":{\"library_path\":\"./" + so + "\",\"api_version\":\"1.3.0\"}}"); }
    return icd;
  }
  void promptIso() { Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT); i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("*/*"); startActivityForResult(i, REQ_ISO); }
  @Override protected void onActivityResult(int req, int res, Intent d) {
    super.onActivityResult(req, res, d);
    if (res != RESULT_OK || d == null || d.getData() == null) return;
    final Uri u = d.getData();
    if (req == REQ_ISO) {
      busy(true, "Importing legal disc..."); setProgPermille(10);
      new Thread(() -> {
        try {
          File dst = new File(baseDir, "game.iso"), tmp = new File(baseDir, "game.iso.tmp"); long tot = -1;
          try (android.database.Cursor c = getContentResolver().query(u, new String[]{android.provider.OpenableColumns.SIZE}, null, null, null)) { if (c != null && c.moveToFirst() && !c.isNull(0)) tot = c.getLong(0); } catch (Throwable ignored) {}
          try (InputStream in = getContentResolver().openInputStream(u); OutputStream out = new FileOutputStream(tmp)) {
            byte[] b = (byte[]) Array.newInstance(byte.class, 65536); int n; long cp = 0; int lm = -1;
            while ((n = in.read(b)) > 0) { out.write(b, 0, n); cp += n; int pm = tot > 0 ? (int)Math.min(990L, (cp*1000L)/tot) : 500; if (pm >= lm + 15) { lm = pm; setProgPermille(pm); } }
          }
          if (dst.exists()) dst.delete(); if (!tmp.renameTo(dst)) throw new IOException("Cannot finalize game.iso");
          runOnUiThread(() -> { setProgPermille(1000); busy(false, "Ready for Launch"); refresh(); });
        } catch (Exception ex) { final String em = String.valueOf(ex.getMessage()); runOnUiThread(() -> { busy(false, "Ready for Launch"); err("Disc Import Failed", em); }); }
      }).start();
    } else if (req == REQ_DRV) {
      busy(true, "Importing Vulkan driver...");
      new Thread(() -> {
        try {
          File dd = new File(getCodeCacheDir(), "custom_driver"); del(dd); dd.mkdirs(); String so = null;
          try (ZipInputStream z = new ZipInputStream(new BufferedInputStream(getContentResolver().openInputStream(u)))) {
            ZipEntry e; byte[] b = (byte[]) Array.newInstance(byte.class, 65536);
            while ((e = z.getNextEntry()) != null) {
              if (e.isDirectory()) continue; String nm = new File(e.getName()).getName(); File out = new File(dd, nm);
              try (FileOutputStream fo = new FileOutputStream(out)) { int n; while ((n = z.read(b)) > 0) fo.write(b, 0, n); }
              if (nm.endsWith(".so")) { out.setReadable(true, false); out.setExecutable(true, false); so = nm; }
            }
          }
          if (so == null) throw new IOException("No .so Vulkan driver found");
          try (FileWriter fw = new FileWriter(new File(dd, "vk_icd.json"))) { fw.write("{\"file_format_version\":\"1.0.0\",\"ICD\":{\"library_path\":\"./" + so + "\",\"api_version\":\"1.3.0\"}}"); }
          final String fSo = so;
          runOnUiThread(() -> { getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString("driver_name", fSo).apply(); Array.setInt(optIdx, 8, 5); TextView dv = (TextView) Array.get(optVal, 8); if (dv != null) dv.setText("Custom (.zip) â–¾"); savePrefs(); busy(false, "Ready for Launch"); refresh(); });
        } catch (Exception ex) { final String em = String.valueOf(ex.getMessage()); runOnUiThread(() -> { busy(false, "Ready for Launch"); err("Driver Import Failed", em); }); }
      }).start();
    }
  }
  void extractAssetZip(String assetName, File destDir, String label, int p0, int p1) throws Exception {
    File marker = new File(destDir, ".extracted_ok"); if (marker.exists()) { setProgPermille(p1); return; }
    del(destDir); destDir.mkdirs();
    long estTot = 320000000L;
    try (android.content.res.AssetFileDescriptor afd = getAssets().openFd(assetName)) { if (afd.getLength() > 0) estTot = (long)(afd.getLength() * 1.15); } catch (Throwable ignored) {}
    long readBytes = 0; int files = 0; long lastUi = 0;
    try (InputStream raw = getAssets().open(assetName); ZipInputStream z = new ZipInputStream(new BufferedInputStream(raw, 262144))) {
      ZipEntry e; byte[] b = (byte[]) Array.newInstance(byte.class, 65536);
      while ((e = z.getNextEntry()) != null) {
        if (e.isDirectory()) continue;
        String rel = e.getName().replace((char) 92, (char) 47); if (rel.endsWith("/")) continue;
        File out = new File(destDir, rel); File par = out.getParentFile(); if (par != null) par.mkdirs();
        try (FileOutputStream fo = new FileOutputStream(out)) { int n; while ((n = z.read(b)) > 0) { fo.write(b, 0, n); readBytes += n; } }
        files++; long now = SystemClock.uptimeMillis();
        if (now - lastUi >= 140) {
          lastUi = now; final int fFiles = files;
          int frac = (int) Math.min(980L, (readBytes * 1000L) / Math.max(1L, estTot));
          final int pm = p0 + ((p1 - p0) * frac) / 1000; final int pct = pm / 10;
          setProgPermille(pm);
          runOnUiThread(() -> { if (progTxt != null) progTxt.setText("Unpacking " + label + " (" + pct + "% - " + fFiles + " files)"); });
        }
      }
    }
    setProgPermille(p1);
    try (FileWriter fw = new FileWriter(marker)) { fw.write("1"); }
  }
  void ensureHdTexturesReady() {
    if (hdDownloading || !Array.getBoolean(togState, 0)) return;
    File md = new File(baseDir, "mods"); md.mkdirs();
    File hd = new File(md, "com.marcelus.hd-textures");
    if (new File(hd, ".extracted_ok").exists()) return;
    hdDownloading = true;
    if (playBtn != null) { playBtn.setEnabled(false); playBtn.setText("DOWNLOADING..."); }
    busy(true, "Preparing HD Textures..."); setProgPermille(20);
    new Thread(() -> {
      try {
        extractHdTextures(hd, 20, 1000);
        hdDownloading = false;
        runOnUiThread(() -> {
          if (playBtn != null) { playBtn.setEnabled(true); playBtn.setText("LAUNCH GAME"); }
          setProgPermille(1000); busy(false, "Ready for Launch"); refresh();
        });
      } catch (Exception ex) {
        hdDownloading = false;
        final String em = String.valueOf(ex.getMessage());
        runOnUiThread(() -> {
          busy(false, "HD Textures Download Needed"); setProgPermille(0);
          new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("HD Textures Download Failed")
            .setMessage(em + "\n\nMake sure you are online or disable HD Textures to launch without them.")
            .setCancelable(false)
            .setPositiveButton("Retry", (dd, w) -> ensureHdTexturesReady())
            .setNegativeButton("Disable HD Textures", (dd, w) -> {
              Array.setBoolean(togState, 0, false);
              savePrefs();
              if (playBtn != null) { playBtn.setEnabled(true); playBtn.setText("LAUNCH GAME"); }
              busy(false, "Ready for Launch"); refresh();
            }).show();
        });
      }
    }).start();
  }

  void extractHdTextures(File destDir, int p0, int p1) throws Exception {
    File marker = new File(destDir, ".extracted_ok"); if (marker.exists()) { setProgPermille(p1); return; }
    try (android.content.res.AssetFileDescriptor afd = getAssets().openFd("bundled_hd_textures.zip")) {
      extractAssetZip("bundled_hd_textures.zip", destDir, "HD Textures", p0, p1);
      return;
    } catch (Throwable ignored) {}
    int pMid = p0 + ((p1 - p0) * 55) / 100;
    File tmpZip = new File(getCacheDir(), "hd_textures_dl.zip");
    if (tmpZip.exists()) tmpZip.delete();
    String urlStr = "https://github.com";
    java.net.HttpURLConnection conn = null;
    for (int hop = 0; hop < 8; hop++) {
      java.net.URL u = new java.net.URL(urlStr);
      conn = (java.net.HttpURLConnection) u.openConnection();
      conn.setInstanceFollowRedirects(false);
      conn.setConnectTimeout(15000);
      conn.setReadTimeout(30000);
      conn.setRequestProperty("User-Agent", "Pocket-Hollow-Android");
      int code = conn.getResponseCode();
      if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
        String loc = conn.getHeaderField("Location");
        conn.disconnect();
        if (loc == null || loc.isEmpty()) throw new IOException("Empty redirect location");
        urlStr = new java.net.URL(u, loc).toString();
        continue;
      }
      if (code != 200) throw new IOException("HD Textures download failed (HTTP " + code + ")");
      break;
    }
    if (conn == null) throw new IOException("Failed to connect to GitHub Release");
    long totBytes = conn.getContentLengthLong();
    if (totBytes <= 0L) totBytes = 415130000L;
    long dlBytes = 0L; long lastUi = 0L;
    try (InputStream in = new BufferedInputStream(conn.getInputStream(), 262144); FileOutputStream fo = new FileOutputStream(tmpZip)) {
      byte[] buf = (byte[]) Array.newInstance(byte.class, 65536);
      int n;
      while ((n = in.read(buf)) > 0) {
        fo.write(buf, 0, n);
        dlBytes += n;
        long now = SystemClock.uptimeMillis();
        if (now - lastUi >= 150L) {
          lastUi = now;
          int frac = (int) Math.min(1000L, (dlBytes * 1000L) / Math.max(1L, totBytes));
          final int pm = p0 + ((pMid - p0) * frac) / 1000;
          final int pct = frac / 10;
          final long mbDone = dlBytes / 1048576L;
          final long mbTot = totBytes / 1048576L;
          setProgPermille(pm);
          runOnUiThread(() -> { if (progTxt != null) progTxt.setText("Downloading HD Textures: " + pct + "% (" + mbDone + " / " + mbTot + " MB)"); });
        }
      }
    } finally { conn.disconnect(); }
    del(destDir); destDir.mkdirs();
    long estUnpacked = Math.max(1L, tmpZip.length());
    long readBytes = 0L; int files = 0; lastUi = 0L;
    try (ZipInputStream z = new ZipInputStream(new BufferedInputStream(new FileInputStream(tmpZip), 262144))) {
      ZipEntry e; byte[] b = (byte[]) Array.newInstance(byte.class, 65536);
      while ((e = z.getNextEntry()) != null) {
        if (e.isDirectory()) continue;
        String rel = e.getName().replace((char) 92, (char) 47); if (rel.endsWith("/")) continue;
        File out = new File(destDir, rel); File par = out.getParentFile(); if (par != null) par.mkdirs();
        try (FileOutputStream fo = new FileOutputStream(out)) { int n; while ((n = z.read(b)) > 0) { fo.write(b, 0, n); readBytes += n; } }
        files++; long now = SystemClock.uptimeMillis();
        if (now - lastUi >= 140L) {
          lastUi = now; final int fFiles = files;
          int frac = (int) Math.min(980L, (readBytes * 1000L) / estUnpacked);
          final int pm = pMid + ((p1 - pMid) * frac) / 1000; final int pct = pm / 10;
          setProgPermille(pm);
          runOnUiThread(() -> { if (progTxt != null) progTxt.setText("Unpacking HD Textures (" + pct + "% - " + fFiles + " files)"); });
        }
      }
    } finally { tmpZip.delete(); }
    setProgPermille(p1);
    try (FileWriter fw = new FileWriter(marker)) { fw.write("1"); }
  }

  void refresh() {
    File iso = new File(baseDir, "game.iso"); boolean hasIso = iso.exists() && iso.length() > 1048576L;
    if (isoBadge != null) { isoBadge.setText(hasIso ? "READY" : "MISSING"); isoBadge.setTextColor(hasIso ? 0xFF34D399 : 0xFFFBBF24); }
    if (topIsoPill != null) topIsoPill.setText(hasIso ? "DISC: READY" : "DISC: MISSING");
    if (isoTxt != null) isoTxt.setText(hasIso ? "game.iso (" + (iso.length()/1048576L) + " MB)" : "No legal disc selected.");
    int dMode = Array.getInt(optIdx, 8); String gpu = detectGpuRenderer(); String autoAsset = resolveAutoDriverAsset();
    String bdg = dMode == 0 ? "AUTO" : (dMode == 4 ? "SYSTEM" : (dMode == 5 ? "CUSTOM" : "BUNDLED"));
    String desc = dMode == 0 ? (autoAsset != null ? "Auto: Turnip (" + gpu + ")" : "Auto: System (" + gpu + ")") : (dMode == 4 ? "System Vulkan (" + gpu + ")" : getSharedPreferences(PREFS, MODE_PRIVATE).getString("driver_name", "Custom (.zip)"));
    int mCnt = (Array.getBoolean(togState, 0) ? 1 : 0) + (Array.getBoolean(togState, 1) ? 1 : 0) + (Array.getBoolean(togState, 2) ? 1 : 0);
    if (drvBadge != null) drvBadge.setText(bdg);
    if (topDrvPill != null) topDrvPill.setText("DRIVER: " + bdg);
    if (drvTxt != null) drvTxt.setText(desc + "  -  " + mCnt + " Mods");
  }
  void openBugReport() {
    try {
      String[] sc = {"4.0x", "3.0x", "2.5x", "2.0x", "1.5x", "1.0x"};
      String[] dm = {"Auto", "Turnip (A6xx/A7xx)", "Turnip (A8xx)", "Qualcomm (v840)", "System Default", "Custom (.zip)"};
      String body = "**Device:** " + Build.MANUFACTURER + " " + Build.MODEL + "\n"
          + "**Android:** " + Build.VERSION.RELEASE + " (SDK " + Build.VERSION.SDK_INT + ")\n"
          + "**GPU:** " + detectGpuRenderer() + "\n"
          + "**App Version:** v1.2.2\n"
          + "**Driver Mode:** " + (String) Array.get(dm, Math.min(5, Array.getInt(optIdx, 8))) + "\n"
          + "**Resolution Scale:** " + (String) Array.get(sc, Math.min(5, Array.getInt(optIdx, 0))) + "\n"
          + "**HD Textures:** " + (Array.getBoolean(togState, 0) ? "ON" : "OFF") + "\n\n"
          + "**What happened:**\n";
      String url = "https://github.com"
          + Uri.encode("Bug Report - " + Build.MODEL)
          + "&body=" + Uri.encode(body);
      startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
    } catch (Throwable ex) {
      err("Bug Report", "Visit https://github.com to report a bug.");
    }
  }

  void launch() {
    File chkIso = new File(baseDir, "game.iso"); if (!chkIso.exists() || chkIso.length() < 1048576L) { promptIso(); return; }
    savePrefs(); if (playBtn != null) playBtn.setEnabled(false); busy(true, "Unpacking mods..."); setProgPermille(40);
    new Thread(() -> {
      try {
        File md = new File(baseDir, "mods"); md.mkdirs();
        File hd = new File(md, "com.marcelus.hd-textures"); if (Array.getBoolean(togState, 0)) extractAssetZip("bundled_hd_textures.zip", hd, "HD Textures", 50, 820); else if (hd.exists()) del(hd);
        File rf = new File(md, "dev.foxhollow.reflections"); if (Array.getBoolean(togState, 1)) extractAssetZip("bundled_reflections.zip", rf, "Reflections", 820, 980); else if (rf.exists()) del(rf);
      } catch (Exception ex) { final String em = String.valueOf(ex.getMessage()); runOnUiThread(() -> { if (playBtn != null) playBtn.setEnabled(true); busy(false, "Ready for Launch"); err("Mod Unpack Failed", em); }); return; }
      runOnUiThread(() -> {
        if (playBtn != null) playBtn.setEnabled(true); setProgPermille(1000); busy(false, "Ready for Launch");
        String[] sc = {"4.0","3.0","2.5","2.0","1.5","1.0"}, an = {"16","8","4","2","1"}, lg = {"en","fr","de","it","es"};
        Intent i = new Intent(this, FoxhollowActivity.class);
        i.putExtra("FOXHOLLOW_USER_DIR", baseDir.getAbsolutePath()); i.putExtra("FOXHOLLOW_DISC", new File(baseDir, "game.iso").getAbsolutePath());
        i.putExtra("FOXHOLLOW_MEMORY_CARD", new File(baseDir, "MemoryCardA.USA.raw").getAbsolutePath()); i.putExtra("FOXHOLLOW_AUTOSAVE", new File(baseDir, "autosave.bin").getAbsolutePath());
        i.putExtra("FOXHOLLOW_CACHE_DIR", new File(baseDir, "cache").getAbsolutePath());
        i.putExtra("FOXHOLLOW_HD_TEXTURES", Array.getBoolean(togState, 0) ? "1" : "0"); i.putExtra("FOXHOLLOW_REFLECTIONS", Array.getBoolean(togState, 1) ? "1" : "0");
        i.putExtra("FOXHOLLOW_HOLD_A", Array.getBoolean(togState, 2) ? "1" : "0"); i.putExtra("FOXHOLLOW_TOUCH_CONTROLS", Array.getBoolean(togState, 10) ? "1" : "0"); i.putExtra("FOXHOLLOW_TOUCH_HYBRID", Array.getBoolean(togState, 12) ? "1" : "0");  i.putExtra("FOXHOLLOW_TOUCH_HAPTICS", Array.getBoolean(togState, 11) ? "1" : "0"); String[] tsc = {"0.85", "1.0", "1.15", "1.30"}; i.putExtra("FOXHOLLOW_TOUCH_SCALE", (String) Array.get(tsc, Array.getInt(optIdx, 9))); i.putExtra("FOXHOLLOW_SHOW_FPS", Array.getBoolean(togState, 3) ? "1" : "0"); i.putExtra("FOXHOLLOW_SHOW_TEMP", Array.getBoolean(togState, 4) ? "1" : "0");
        i.putExtra("FOXHOLLOW_SHOW_FRAMETIME", Array.getBoolean(togState, 6) ? "1" : "0"); i.putExtra("FOXHOLLOW_SHOW_BATTERY", Array.getBoolean(togState, 7) ? "1" : "0");
        i.putExtra("FOXHOLLOW_SHOW_RAM", Array.getBoolean(togState, 8) ? "1" : "0"); i.putExtra("FOXHOLLOW_SHOW_CPU_FREQ", Array.getBoolean(togState, 9) ? "1" : "0");
        i.putExtra("FOXHOLLOW_STAT_POS", String.valueOf(Array.getInt(optIdx, 6))); i.putExtra("FOXHOLLOW_STAT_SIZE", String.valueOf(Array.getInt(optIdx, 7)));
        i.putExtra("FOXHOLLOW_RENDER_SCALE", (String) Array.get(sc, Array.getInt(optIdx, 0))); i.putExtra("FOXHOLLOW_SCREEN_STYLE", Array.getInt(optIdx, 1) == 0 ? "wide" : "narrow");
        i.putExtra("FOXHOLLOW_FRAME_LIMIT", "60"); i.putExtra("FOXHOLLOW_VSYNC", Array.getInt(optIdx, 2) == 0 ? "1" : "0");
        i.putExtra("FOXHOLLOW_ANISO", (String) Array.get(an, Array.getInt(optIdx, 3))); i.putExtra("FOXHOLLOW_RENDERER", Array.getInt(optIdx, 4) == 0 ? "vulkan" : "opengles");
        i.putExtra("FOXHOLLOW_LANGUAGE", (String) Array.get(lg, Array.getInt(optIdx, 5))); i.putExtra("FOXHOLLOW_DEBUG", "0");
        File icd = null; try { icd = prepareActiveDriver(); } catch (Throwable ignored) {}
        if (icd != null && icd.exists()) { i.putExtra("CUSTOM_VK_ICD", icd.getAbsolutePath()); i.putExtra("CUSTOM_VK_DIR", icd.getParentFile().getAbsolutePath()); }
        if (Array.getInt(optIdx, 8) == 0) { try (FileWriter fw = new FileWriter(new File(new File(baseDir, "cache"), "boot_guard.flag"))) { fw.write("1"); } catch (Throwable ignored) {} } startActivity(i);
      });
    }).start();
  }
  void savePrefs() { SharedPreferences.Editor e = getSharedPreferences(PREFS, MODE_PRIVATE).edit(); for (int k = 0; k < 10; k++) e.putInt("o" + k, Array.getInt(optIdx, k)); for (int t = 0; t < 13; t++) e.putBoolean("t" + t, Array.getBoolean(togState, t)); e.apply(); }
  void loadPrefs() { SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE); int tier = detectGpuTier(); int[] def = {tier == 2 ? 3 : (tier == 1 ? 4 : 5), 1, 0, tier == 2 ? 1 : (tier == 1 ? 2 : 3), 0, 0, 0, 1, 0, 1}; for (int k = 0; k < 10; k++) { int v = p.getInt("o" + k, Array.getInt(def, k)); Array.setInt(optIdx, k, v); String[] row = (String[]) Array.get(optItems, k); TextView tv = (TextView) Array.get(optVal, k); if (tv != null) tv.setText((String) Array.get(row, v) + " â–¾"); } boolean[] tdef = {false, false, false, false, false, false, false, false, false, false, false, true, false}; for (int t = 0; t < 13; t++) setIosSwitchVisual(t, p.getBoolean("t" + t, Array.getBoolean(tdef, t)), false); }
  void busy(boolean b, String m) { if (progTxt != null) progTxt.setText(m); }
  void err(String t, String m) { new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert).setTitle(t).setMessage(m).setPositiveButton("OK", null).show(); }
  void del(File f) { if (f.isDirectory()) { File[] c = f.listFiles(); if (c != null) for (File x : c) del(x); } f.delete(); }

    void openTouchLayoutEditor() {
    float d = getResources().getDisplayMetrics().density;
    Dialog dlg = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
    FrameLayout root = new FrameLayout(this); root.setBackgroundColor(0xFF070B14);
    View grid = new View(this) { final Paint gp = new Paint(Paint.ANTI_ALIAS_FLAG); @Override protected void onDraw(Canvas c) { int w=getWidth(),h=getHeight(); gp.setStyle(Paint.Style.STROKE); gp.setStrokeWidth(d); gp.setColor(0x1894A3B8); for(int x=0;x<w;x+=Math.round(32f*d))c.drawLine(x,0,x,h,gp); for(int y=0;y<h;y+=Math.round(32f*d))c.drawLine(0,y,w,y,gp); int w43=Math.min(w,Math.round(h*4f/3f)); gp.setColor(0x3338BDF8); c.drawRect((w-w43)*0.5f,4f*d,(w+w43)*0.5f,h-4f*d,gp); } };
    root.addView(grid, new FrameLayout.LayoutParams(-1, -1));
    FoxhollowActivity.GcTouchView tv = new FoxhollowActivity.GcTouchView(this, getSharedPreferences("fh_touch_cfg", MODE_PRIVATE), Array.getBoolean(togState, 11), 1.0f);
    tv.editMode = true; root.addView(tv, new FrameLayout.LayoutParams(-1, -1));
    String[] names = {"Left Stick", "C-Stick", "D-Pad", "A", "B", "X", "Y", "L", "R", "Z", "Start"};
    LinearLayout panel = new LinearLayout(this); panel.setOrientation(LinearLayout.VERTICAL); panel.setBackground(boxBg(0xEB0F172A, 0xEB090D16, 12, 1.2f, 0xFF334155)); panel.setPadding(Math.round(12f*d), Math.round(7f*d), Math.round(12f*d), Math.round(7f*d));
    LinearLayout r1 = new LinearLayout(this); r1.setOrientation(LinearLayout.HORIZONTAL); r1.setGravity(Gravity.CENTER_VERTICAL);
    TextView scLbl = new TextView(this); scLbl.setTextColor(0xFFFACC15); scLbl.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f); scLbl.setTypeface(Typeface.DEFAULT_BOLD); scLbl.setMinWidth(Math.round(112f*d));
    SeekBar scBar = new SeekBar(this); scBar.setMax(210);
    scBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
      @Override public void onProgressChanged(SeekBar b, int p, boolean u) { float sc = (40 + p) / 100f; if (u) tv.setSelScale(sc); scLbl.setText((String) Array.get(names, tv.selIdx) + " Size: " + (40 + p) + "%"); }
      @Override public void onStartTrackingTouch(SeekBar b) {} @Override public void onStopTrackingTouch(SeekBar b) {}
    });
    tv.onSel = () -> { int pct = Math.round(Array.getFloat(tv.itemSc, tv.selIdx) * 100f); scBar.setProgress(Math.max(0, Math.min(210, pct - 40))); scLbl.setText((String) Array.get(names, tv.selIdx) + " Size: " + pct + "%"); };
    tv.onSel.run();
    r1.addView(scLbl); r1.addView(scBar, new LinearLayout.LayoutParams(Math.round(145f*d), -2));
    LinearLayout r2 = new LinearLayout(this); r2.setOrientation(LinearLayout.HORIZONTAL); r2.setGravity(Gravity.CENTER_VERTICAL);
    TextView opLbl = new TextView(this); opLbl.setText("Opacity: " + Math.round(tv.uiAlpha*100f) + "%"); opLbl.setTextColor(0xFFE2E8F0); opLbl.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f); opLbl.setTypeface(Typeface.DEFAULT_BOLD); opLbl.setMinWidth(Math.round(112f*d));
    SeekBar opBar = new SeekBar(this); opBar.setMax(80); opBar.setProgress(Math.max(0, Math.min(80, Math.round(tv.uiAlpha*100f) - 20)));
    opBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
      @Override public void onProgressChanged(SeekBar b, int p, boolean u) { if (u) { tv.uiAlpha = (20 + p) / 100f; tv.prefs.edit().putFloat("alpha", tv.uiAlpha).apply(); tv.invalidate(); } opLbl.setText("Opacity: " + (20 + p) + "%"); }
      @Override public void onStartTrackingTouch(SeekBar b) {} @Override public void onStopTrackingTouch(SeekBar b) {}
    });
    TextView rs = new TextView(this); rs.setText("RESET"); rs.setTextColor(0xFFF87171); rs.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f); rs.setTypeface(Typeface.DEFAULT_BOLD); rs.setPadding(Math.round(10f*d), Math.round(4f*d), Math.round(8f*d), Math.round(4f*d));
    rs.setOnClickListener(v -> { tv.resetLayoutToDefault(); tv.onSel.run(); Toast.makeText(this, "Reset to default", Toast.LENGTH_SHORT).show(); });
    TextView sv = new TextView(this); sv.setText("SAVE"); sv.setTextColor(0xFF34D399); sv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f); sv.setTypeface(Typeface.DEFAULT_BOLD); sv.setPadding(Math.round(8f*d), Math.round(4f*d), Math.round(4f*d), Math.round(4f*d));
    sv.setOnClickListener(v -> { tv.saveLayout(); Toast.makeText(this, "Custom layout saved", Toast.LENGTH_SHORT).show(); dlg.dismiss(); });
    r2.addView(opLbl); r2.addView(opBar, new LinearLayout.LayoutParams(Math.round(115f*d), -2)); r2.addView(rs); r2.addView(sv);
    panel.addView(r1); panel.addView(r2);
    FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL); lp.topMargin = Math.round(8f*d);
    root.addView(panel, lp); dlg.setContentView(root); dlg.show();
  }
  final String[] gcLbl = {"A", "B", "X", "Y", "L", "R", "Z", "Start", "Up", "Down", "Left", "Right"};
  final int[] defXbox = {96, 97, 99, 100, 102, 103, 105, 108, 19, 20, 21, 22};
  final int[] defSwitch = {97, 96, 100, 99, 102, 103, 105, 108, 19, 20, 21, 22};
  final TextView[] bindTxt = (TextView[]) Array.newInstance(TextView.class, 12);
  int detectPadType() { for (int id : InputDevice.getDeviceIds()) { InputDevice d = InputDevice.getDevice(id); if (d == null) continue; int sr = d.getSources(); if ((sr & 0x401) == 0 && (sr & 0x1000010) == 0) continue; String n = d.getName() != null ? d.getName().toLowerCase() : ""; int v = d.getVendorId(); if (v == 0x057E || n.contains("nintendo") || n.contains("switch") || n.contains("joy-con") || n.contains("pro controller")) return 3; if (v == 0x054C || n.contains("sony") || n.contains("playstation") || n.contains("dualsense") || n.contains("dualshock")) return 2; } return 1; }
  int activePadType(SharedPreferences sp) { int m = sp.getInt("pad_prof", 0); return m == 0 ? detectPadType() : m; }
  int[] activeDefaults(SharedPreferences sp) { return activePadType(sp) == 3 ? defSwitch : defXbox; }
  String profLabel(SharedPreferences sp) { int m = sp.getInt("pad_prof", 0), d = detectPadType(); String dn = d == 3 ? "Switch" : (d == 2 ? "PlayStation" : "Xbox"); return m == 0 ? ("Auto (" + dn + ")") : (m == 3 ? "Nintendo Switch" : (m == 2 ? "PlayStation" : "Xbox / Standard")); }
  String kcName(int kc, int pt) { if (kc == -102) return pt == 2 ? "L2 Trigger" : (pt == 3 ? "ZL Trigger" : "Trigger L2"); if (kc == -103) return pt == 2 ? "R2 Trigger" : (pt == 3 ? "ZR Trigger" : "Trigger R2"); if (kc <= 0) return "Unbound"; if (pt == 2) { if (kc == 96) return "Cross (\u2715)"; if (kc == 97) return "Circle (\u25CB)"; if (kc == 99) return "Square (\u25A1)"; if (kc == 100) return "Triangle (\u25B3)"; if (kc == 102) return "L1"; if (kc == 103) return "R1"; if (kc == 105) return "R2"; if (kc == 108) return "Options"; } if (pt == 3) { if (kc == 97) return "B (Bottom)"; if (kc == 96) return "A (Right)"; if (kc == 100) return "Y (Left)"; if (kc == 99) return "X (Top)"; if (kc == 102) return "L"; if (kc == 103) return "R"; if (kc == 105) return "ZR"; if (kc == 108) return "Plus (+)"; } return KeyEvent.keyCodeToString(kc).replace("KEYCODE_BUTTON_", "Button ").replace("KEYCODE_DPAD_", "D-Pad ").replace("KEYCODE_", ""); }
  void applyProfileDefaults(SharedPreferences sp) { int[] defs = activeDefaults(sp); int pt = activePadType(sp); SharedPreferences.Editor e = sp.edit().putBoolean("kc_custom", pt == 3); for (int k = 0; k < 12; k++) { int dk = Array.getInt(defs, k); e.putInt("kc_" + k, dk); TextView tv = (TextView) Array.get(bindTxt, k); if (tv != null) tv.setText(kcName(dk, pt)); } e.apply(); }
  void secHdr(LinearLayout p, String t, boolean g) { TextView h = new TextView(this); h.setText(t); h.setTextColor(0xFF94A3B8); h.setTextSize(TypedValue.COMPLEX_UNIT_PX, spx(9.5f)); h.setTypeface(Typeface.DEFAULT_BOLD); h.setLetterSpacing(0.08f); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.setMargins(spx(4), g ? spx(8) : spx(2), 0, spx(5)); p.addView(h, lp); }
  void actionRow(LinearLayout p, String l, String tag, int col, Runnable fn) { LinearLayout r = new LinearLayout(this); r.setOrientation(LinearLayout.HORIZONTAL); r.setGravity(Gravity.CENTER_VERTICAL); prep(r); r.setBackground(focusBg(0xFF121826, 0xFF0E131F, 10, 0xFF1F2A40, 0xFFF59E0B)); r.setPadding(spx(12), spx(7), spx(12), spx(7)); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.setMargins(0, 0, 0, spx(5)); TextView tk = new TextView(this); tk.setText(l); tk.setTextColor(0xFFF1F5F9); tk.setTextSize(TypedValue.COMPLEX_UNIT_PX, spx(11.5f)); tk.setTypeface(Typeface.DEFAULT_BOLD); tk.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f)); TextView tv = new TextView(this); tv.setText(tag); tv.setTextColor(col); tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, spx(11.5f)); tv.setTypeface(Typeface.DEFAULT_BOLD); r.setOnClickListener(w -> fn.run()); r.addView(tk); r.addView(tv); p.addView(r, lp); }
  void bindRow(LinearLayout p, final int slot, final SharedPreferences sp) { int[] defs = activeDefaults(sp); int pt = activePadType(sp); int kc = sp.getInt("kc_" + slot, Array.getInt(defs, slot)); LinearLayout r = new LinearLayout(this); r.setOrientation(LinearLayout.HORIZONTAL); r.setGravity(Gravity.CENTER_VERTICAL); prep(r); r.setBackground(focusBg(0xFF121826, 0xFF0E131F, 10, 0xFF1F2A40, 0xFFF59E0B)); r.setPadding(spx(12), spx(7), spx(12), spx(7)); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.setMargins(0, 0, 0, spx(5)); TextView tk = new TextView(this); tk.setText((String) Array.get(gcLbl, slot)); tk.setTextColor(0xFFF1F5F9); tk.setTextSize(TypedValue.COMPLEX_UNIT_PX, spx(11.5f)); tk.setTypeface(Typeface.DEFAULT_BOLD); tk.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f)); TextView tv = new TextView(this); tv.setText(kcName(kc, pt)); tv.setTextColor(0xFFFACC15); tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, spx(11.5f)); tv.setTypeface(Typeface.DEFAULT_BOLD); Array.set(bindTxt, slot, tv); r.setOnClickListener(w -> promptBind(slot, sp)); r.addView(tk); r.addView(tv); p.addView(r, lp); }
  void promptBind(int slot, SharedPreferences sp) { int pt = activePadType(sp); Dialog d = new Dialog(this) { void bc(int kc) { sp.edit().putInt("kc_" + slot, kc).putBoolean("kc_custom", true).apply(); TextView tv = (TextView) Array.get(bindTxt, slot); if (tv != null) tv.setText(kcName(kc, pt)); dismiss(); } @Override public boolean dispatchKeyEvent(KeyEvent e) { if (e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) bc(e.getKeyCode()); return true; } @Override public boolean dispatchGenericMotionEvent(MotionEvent e) { float l2 = Math.max(e.getAxisValue(MotionEvent.AXIS_LTRIGGER), e.getAxisValue(MotionEvent.AXIS_BRAKE)), r2 = Math.max(e.getAxisValue(MotionEvent.AXIS_RTRIGGER), e.getAxisValue(MotionEvent.AXIS_GAS)); if (l2 > 0.55f) { bc(-102); return true; } if (r2 > 0.55f) { bc(-103); return true; } return super.dispatchGenericMotionEvent(e); } }; d.requestWindowFeature(Window.FEATURE_NO_TITLE); d.setCancelable(false); LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(spx(22), spx(18), spx(22), spx(14)); box.setBackground(boxBg(0xFF121826, 0xFF0E131F, 12, 1.5f, 0xFFF59E0B)); TextView t1 = new TextView(this); t1.setText((String) Array.get(gcLbl, slot)); t1.setTextColor(0xFFFACC15); t1.setTextSize(TypedValue.COMPLEX_UNIT_PX, spx(15)); t1.setTypeface(Typeface.DEFAULT_BOLD); box.addView(t1); TextView t2 = new TextView(this); t2.setText("Waiting for input..."); t2.setTextColor(0xFFE2E8F0); t2.setTextSize(TypedValue.COMPLEX_UNIT_PX, spx(12)); t2.setPadding(0, spx(8), 0, spx(16)); box.addView(t2); LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.END); TextView clr = new TextView(this); clr.setText("CLEAR"); clr.setTextColor(0xFFF87171); clr.setTextSize(TypedValue.COMPLEX_UNIT_PX, spx(11)); clr.setTypeface(Typeface.DEFAULT_BOLD); clr.setPadding(spx(12), spx(6), spx(12), spx(6)); clr.setFocusable(false); clr.setOnClickListener(v -> { sp.edit().putInt("kc_" + slot, -1).putBoolean("kc_custom", true).apply(); TextView tv = (TextView) Array.get(bindTxt, slot); if (tv != null) tv.setText("Unbound"); d.dismiss(); }); TextView cnl = new TextView(this); cnl.setText("CANCEL"); cnl.setTextColor(0xFF94A3B8); cnl.setTextSize(TypedValue.COMPLEX_UNIT_PX, spx(11)); cnl.setTypeface(Typeface.DEFAULT_BOLD); cnl.setPadding(spx(12), spx(6), spx(4), spx(6)); cnl.setFocusable(false); cnl.setOnClickListener(v -> d.dismiss()); row.addView(clr); row.addView(cnl); box.addView(row, new LinearLayout.LayoutParams(-1, -2)); d.setContentView(box); d.show(); }
  void buildControlsTab(LinearLayout p) { SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE); if (!sp.getBoolean("kc_custom", false) && activePadType(sp) == 3) applyProfileDefaults(sp); secHdr(p, "ON-SCREEN TOUCH", false); iosSwitchRow(p, "On-Screen Touch Controls", 10); iosSwitchRow(p, "Touch Haptic Vibration", 11); iosSwitchRow(p, "Keep Touch With Controller (Accessibility)", 12); actionRow(p, "Set Custom Touch Layout & Size", "Edit \u203A", 0xFF34D399, () -> openTouchLayoutEditor()); actionRow(p, "Reset Touch Layout & Sizes", "Reset \u203A", 0xFF94A3B8, () -> { getSharedPreferences("fh_touch_cfg", MODE_PRIVATE).edit().clear().apply(); Toast.makeText(this, "Touch layout & sizes reset", Toast.LENGTH_SHORT).show(); }); secHdr(p, "CONTROLLER PROFILE", true); LinearLayout pr = new LinearLayout(this); pr.setOrientation(LinearLayout.HORIZONTAL); pr.setGravity(Gravity.CENTER_VERTICAL); prep(pr); pr.setBackground(focusBg(0xFF121826, 0xFF0E131F, 10, 0xFF1F2A40, 0xFFF59E0B)); pr.setPadding(spx(12), spx(7), spx(12), spx(7)); LinearLayout.LayoutParams lpPr = new LinearLayout.LayoutParams(-1, -2); lpPr.setMargins(0, 0, 0, spx(5)); TextView pk = new TextView(this); pk.setText("Controller Button Layout"); pk.setTextColor(0xFFF1F5F9); pk.setTextSize(TypedValue.COMPLEX_UNIT_PX, spx(11.5f)); pk.setTypeface(Typeface.DEFAULT_BOLD); pk.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f)); TextView pv = new TextView(this); pv.setText(profLabel(sp) + " \u25BE"); pv.setTextColor(0xFF38BDF8); pv.setTextSize(TypedValue.COMPLEX_UNIT_PX, spx(11.5f)); pv.setTypeface(Typeface.DEFAULT_BOLD); pr.setOnClickListener(w -> { sp.edit().putInt("pad_prof", (sp.getInt("pad_prof", 0) + 1) % 4).apply(); pv.setText(profLabel(sp) + " \u25BE"); applyProfileDefaults(sp); }); pr.addView(pk); pr.addView(pv); p.addView(pr, lpPr); secHdr(p, "BUTTONS", true); for (int idx : new int[]{0, 1, 2, 3, 6, 7}) bindRow(p, idx, sp); secHdr(p, "TRIGGERS", true); for (int idx : new int[]{4, 5}) bindRow(p, idx, sp); secHdr(p, "D-PAD", true); for (int idx : new int[]{8, 9, 10, 11}) bindRow(p, idx, sp); secHdr(p, "DEFAULTS", true); actionRow(p, "Reset Controller Mapping", "Default \u203A", 0xFFF87171, () -> { applyProfileDefaults(sp); Toast.makeText(this, "Reset to " + profLabel(sp), Toast.LENGTH_SHORT).show(); }); }
}
