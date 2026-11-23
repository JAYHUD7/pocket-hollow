package dev.foxhollow.android;

import android.app.*; import android.content.*; import android.content.res.Configuration;
import android.graphics.*; import android.graphics.drawable.*; import android.net.*;
import android.os.*; import android.util.*; import android.view.*; import android.widget.*;
import java.io.*; import java.lang.reflect.Array; import java.util.zip.*;

public class LauncherActivity extends Activity {
  static final int REQ_ISO = 101, REQ_DRV = 102;
  static final String PREFS = "foxhollow_pocket_prefs_v6";
  File baseDir; TextView isoBadge, isoTxt, drvBadge, drvTxt, progTxt;
  FrameLayout progBar; View progFill; Button playBtn;

  final String[] optLabels = {"Resolution Scale", "Aspect Ratio", "V-Sync", "Anisotropic", "Backend", "Language"};
  final String[][] optItems = {
      {"4.0x (2160p)", "3.0x (1440p)", "2.5x", "2.0x (1080p)", "1.5x", "1.0x (Native)"},
      {"Widescreen (16:9)", "Original (4:3)"},
      {"On", "Off"},
      {"16x", "8x", "4x", "2x", "1x"},
      {"Vulkan", "OpenGL ES"},
      {"English", "Francais", "Deutsch", "Italiano", "Espanol"}
  };
  final int[] optIdx = {3, 0, 0, 0, 0, 0};
  final TextView[] optVal = (TextView[]) Array.newInstance(TextView.class, 6);
  final boolean[] togState = {true, true, true, false, false, false};
  final FrameLayout[] togTrack = (FrameLayout[]) Array.newInstance(FrameLayout.class, 6);
  final View[] togThumb = (View[]) Array.newInstance(View.class, 6);

  int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
  GradientDrawable pill(int top, int bot, int radDp, int sc) {
    GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{top, bot});
    d.setCornerRadius(dp(radDp)); if (sc != 0) d.setStroke(dp(1), sc); return d;
  }
  String detectAspectLabel(int w, int h) {
    int big = Math.max(w, h), small = Math.max(1, Math.min(w, h)); float r = (float) big / (float) small;
    if (r >= 2.25f) return "21:9"; if (r >= 2.08f) return "20:9"; if (r >= 1.92f) return "19.5:9";
    if (r >= 1.70f) return "16:9"; if (r >= 1.55f) return "16:10"; if (r >= 1.42f) return "3:2"; return "4:3";
  }
  File resolveStorageDir() {
    File pub = new File(Environment.getExternalStorageDirectory(), "pocket-hollow");
    if (Build.VERSION.SDK_INT < 30 || Environment.isExternalStorageManager()) { pub.mkdirs(); if (pub.canWrite()) return pub; }
    File appDir = getExternalFilesDir(null); return appDir != null ? appDir : getFilesDir();
  }
  void ensureDirs() { baseDir.mkdirs(); new File(baseDir, "mods").mkdirs(); new File(baseDir, "cache").mkdirs(); }
  void setProgPermille(int pm) {
    if (progBar == null || progFill == null) return; final int cl = Math.max(0, Math.min(1000, pm));
    progBar.post(() -> { int tw = progBar.getWidth(); if (tw <= 0) tw = Math.max(dp(160), getResources().getDisplayMetrics().widthPixels - dp(48)); int fw = cl <= 0 ? dp(6) : Math.max(dp(8), (int)((tw * (long)cl)/1000L)); ViewGroup.LayoutParams lp = progFill.getLayoutParams(); lp.width = Math.min(tw, fw); progFill.setLayoutParams(lp); });
  }
  @Override protected void onCreate(Bundle b) {
    super.onCreate(b); getWindow().setStatusBarColor(0xFF0A0D12); getWindow().setNavigationBarColor(0xFF0A0D12);
    if (Build.VERSION.SDK_INT >= 30 && !Environment.isExternalStorageManager()) {
      new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert).setTitle("Storage Access Required")
        .setMessage("Allow \"All files access\" so Pocket-Hollow can store your saves, legal disc, and HD mods in /storage/emulated/0/pocket-hollow.")
        .setPositiveButton("Grant Access", (d, w) -> { try { startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + getPackageName()))); } catch (Throwable ignored) {} })
        .setNegativeButton("Later", null).show();
    }
    baseDir = resolveStorageDir(); ensureDirs(); buildUi();
  }
  @Override public void onConfigurationChanged(Configuration newConfig) { super.onConfigurationChanged(newConfig); savePrefs(); buildUi(); }
  @Override protected void onResume() { super.onResume(); File n = resolveStorageDir(); if (baseDir == null || !n.getAbsolutePath().equals(baseDir.getAbsolutePath())) { baseDir = n; ensureDirs(); } refresh(); }

  void buildUi() {
    DisplayMetrics dm = getResources().getDisplayMetrics();
    float wDp = dm.widthPixels / dm.density, hDp = dm.heightPixels / dm.density;
    boolean threeCol = wDp >= 680f;
    boolean twoCol = !threeCol && wDp >= 520f;
    boolean compactH = hDp < 500f;
    int padH = dp(wDp >= 860f ? 20 : 12), padV = dp(compactH ? 7 : 11);

    final LinearLayout root = new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(0xFF0A0D12); root.setPadding(padH, padV, padH, padV);
    if (Build.VERSION.SDK_INT >= 20) {
      root.setOnApplyWindowInsetsListener((v, ins) -> {
        int l = ins.getSystemWindowInsetLeft(), t = ins.getSystemWindowInsetTop(), r = ins.getSystemWindowInsetRight(), bt = ins.getSystemWindowInsetBottom();
        if (Build.VERSION.SDK_INT >= 28 && ins.getDisplayCutout() != null) {
          l = Math.max(l, ins.getDisplayCutout().getSafeInsetLeft()); t = Math.max(t, ins.getDisplayCutout().getSafeInsetTop());
          r = Math.max(r, ins.getDisplayCutout().getSafeInsetRight()); bt = Math.max(bt, ins.getDisplayCutout().getSafeInsetBottom());
        }
        v.setPadding(padH + l, padV + t, padH + r, padV + bt); return ins;
      });
    }

    // Minimalist Top Bar
    LinearLayout hdr = new LinearLayout(this); hdr.setOrientation(LinearLayout.HORIZONTAL); hdr.setGravity(Gravity.CENTER_VERTICAL); hdr.setPadding(dp(2), 0, dp(2), dp(compactH ? 6 : 9));
    TextView title = new TextView(this); title.setText("POCKET-HOLLOW"); title.setTextColor(0xFFFFFFFF); title.setTextSize(compactH ? 16.5f : 19f); title.setTypeface(Typeface.DEFAULT_BOLD); title.setLetterSpacing(0.06f); hdr.addView(title);
    TextView sub = new TextView(this); sub.setText("  •  ARM64"); sub.setTextColor(0xFFFF9E44); sub.setTextSize(compactH ? 10.5f : 11.5f); sub.setTypeface(Typeface.DEFAULT_BOLD); sub.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)); hdr.addView(sub);
    TextView asp = new TextView(this); asp.setText(detectAspectLabel(dm.widthPixels, dm.heightPixels) + "  •  " + dm.widthPixels + "x" + dm.heightPixels); asp.setTextColor(0xFF94A3B8); asp.setTextSize(9.5f); asp.setTypeface(Typeface.DEFAULT_BOLD);
    asp.setBackground(pill(0xFF131823, 0xFF131823, 8, 0xFF232B3E)); asp.setPadding(dp(9), dp(3), dp(9), dp(3)); hdr.addView(asp);
    root.addView(hdr, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

    // Zero-Scroll 3-Column Layout on Wide Landscape (Red Magic 8 Pro 20:9, Odin 3 16:9), Adaptive Stack on 4:3 / Portrait
    LinearLayout cols = new LinearLayout(this);
    cols.setOrientation((threeCol || twoCol) ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);

    LinearLayout cLeft = new LinearLayout(this); cLeft.setOrientation(LinearLayout.VERTICAL);
    LinearLayout cMid = new LinearLayout(this); cMid.setOrientation(LinearLayout.VERTICAL);
    LinearLayout cRight = new LinearLayout(this); cRight.setOrientation(LinearLayout.VERTICAL);

    LinearLayout dCard = box(cLeft, false, compactH);
    isoBadge = hdrBadge(dCard, "GAME DISC", "MISSING"); isoTxt = info(dCard, compactH);
    Button bIso = secBtn("Select Legal ISO / RVZ", compactH); bIso.setOnClickListener(v -> promptIso()); dCard.addView(bIso);

    LinearLayout vCard = box(cLeft, threeCol, compactH);
    drvBadge = hdrBadge(vCard, "VULKAN DRIVER", "SYSTEM"); drvTxt = info(vCard, compactH);
    iosSwitchRow(vCard, "Use Custom Driver", 5, compactH);
    Button bDrv = secBtn("Import Driver (.zip)", compactH);
    bDrv.setOnClickListener(v -> { Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT); i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("*/*"); startActivityForResult(i, REQ_DRV); });
    vCard.addView(bDrv);

    LinearLayout gCard = box(cMid, threeCol, compactH);
    hdrBadge(gCard, "DISPLAY & RENDERER", "VULKAN");
    LinearLayout gList = new LinearLayout(this); gList.setOrientation(LinearLayout.VERTICAL); gList.setPadding(0, dp(compactH ? 2 : 4), 0, 0);
    for (int k = 0; k < 6; k++) optRow(gList, optLabels[k], k, compactH);
    gCard.addView(gList);

    LinearLayout mCard = box(cRight, threeCol, compactH);
    hdrBadge(mCard, "MODS & OVERLAYS", "READY");
    LinearLayout mList = new LinearLayout(this); mList.setOrientation(LinearLayout.VERTICAL); mList.setPadding(0, dp(compactH ? 2 : 4), 0, 0);
    iosSwitchRow(mList, "HD Texture Pack", 0, compactH);
    iosSwitchRow(mList, "Water Reflections", 1, compactH);
    iosSwitchRow(mList, "Hold-A Auto-Mash", 2, compactH);
    iosSwitchRow(mList, "FPS Counter", 3, compactH);
    iosSwitchRow(mList, "CPU Temperature", 4, compactH);
    mCard.addView(mList);

    if (threeCol) {
      LinearLayout.LayoutParams lp1 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0.31f); lp1.setMargins(0, 0, dp(8), 0);
      LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0.37f); lp2.setMargins(0, 0, dp(8), 0);
      LinearLayout.LayoutParams lp3 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0.32f);
      cols.addView(cLeft, lp1); cols.addView(cMid, lp2); cols.addView(cRight, lp3);
      root.addView(cols, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
    } else {
      ScrollView sv = new ScrollView(this); sv.setFillViewport(true); sv.setVerticalScrollBarEnabled(false);
      if (twoCol) {
        LinearLayout.LayoutParams lp1 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.45f); lp1.setMargins(0, 0, dp(8), 0);
        LinearLayout rightWrap = new LinearLayout(this); rightWrap.setOrientation(LinearLayout.VERTICAL);
        rightWrap.addView(cMid); rightWrap.addView(cRight);
        cols.addView(cLeft, lp1); cols.addView(rightWrap, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.55f));
      } else {
        cols.addView(cLeft); cols.addView(cMid); cols.addView(cRight);
      }
      sv.addView(cols);
      root.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
    }

    // Pinned Bottom Action Dock
    LinearLayout dock = new LinearLayout(this); dock.setOrientation(LinearLayout.VERTICAL); dock.setPadding(0, dp(compactH ? 6 : 9), 0, 0);
    progBar = new FrameLayout(this); progBar.setBackground(pill(0xFF1A202E, 0xFF1A202E, 5, 0xFF2E374D));
    LinearLayout.LayoutParams lpProg = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(7)); lpProg.setMargins(0, 0, 0, dp(4)); progBar.setLayoutParams(lpProg);
    progFill = new View(this); progFill.setBackground(pill(0xFFFF8C38, 0xFFE05200, 5, 0)); progFill.setLayoutParams(new FrameLayout.LayoutParams(dp(6), ViewGroup.LayoutParams.MATCH_PARENT));
    progBar.addView(progFill); progBar.setVisibility(View.GONE);
    progTxt = new TextView(this); progTxt.setTextColor(0xFFFFB74D); progTxt.setTextSize(11f); progTxt.setVisibility(View.GONE); progTxt.setPadding(0, 0, 0, dp(3));
    dock.addView(progBar); dock.addView(progTxt);

    playBtn = new Button(this); playBtn.setText("PLAY GAME"); playBtn.setTextColor(0xFFFFFFFF); playBtn.setTextSize(compactH ? 13.5f : 15f); playBtn.setTypeface(Typeface.DEFAULT_BOLD); playBtn.setLetterSpacing(0.08f);
    playBtn.setBackground(pill(0xFFFF8C38, 0xFFE05200, 11, 0)); playBtn.setPadding(dp(16), dp(compactH ? 9 : 12), dp(16), dp(compactH ? 9 : 12)); playBtn.setOnClickListener(v -> launch());
    dock.addView(playBtn, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    root.addView(dock, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

    setContentView(root); loadPrefs(); refresh();
  }
  void promptIso() { Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT); i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("*/*"); startActivityForResult(i, REQ_ISO); }
  void extractAssetZip(String a, File d, String l) throws Exception {
    File mk = new File(d, ".ok_v3"); if (mk.exists()) return;
    del(d); d.mkdirs(); byte[] b = java.nio.ByteBuffer.allocate(262144).array(); int c = 0; long lu = 0L;
    try (ZipInputStream z = new ZipInputStream(new BufferedInputStream(getAssets().open(a), 262144))) {
      ZipEntry e; while ((e = z.getNextEntry()) != null) {
        String en = e.getName().replace((char)92, (char)47); if (en.contains("..")) continue;
        File t = new File(d, en); if (e.isDirectory()) { t.mkdirs(); continue; }
        if (t.getParentFile() != null) t.getParentFile().mkdirs();
        try (BufferedOutputStream o = new BufferedOutputStream(new FileOutputStream(t), 262144)) { int n; while ((n = z.read(b)) != -1) o.write(b, 0, n); }
        c++; long now = SystemClock.uptimeMillis();
        if (now - lu >= 120L) { lu = now; final int fc = c; final int pm = Math.min(980, (fc * 1000) / 2630); runOnUiThread(() -> { setProgPermille(pm); progTxt.setText("Unpacking " + l + " (" + fc + " files)..."); }); }
      }
    }
    new FileOutputStream(mk).close();
  }
  void copyIso(Uri u) {
    busy(true, "Preparing disc import..."); setProgPermille(0);
    new Thread(() -> {
      try {
        ensureDirs(); long totB = -1L;
        try (android.content.res.AssetFileDescriptor afd = getContentResolver().openAssetFileDescriptor(u, "r")) { if (afd != null && afd.getLength() > 0) totB = afd.getLength(); } catch (Throwable ignored) {}
        final long fTot = totB; File out = new File(baseDir, "game.iso");
        InputStream raw = getContentResolver().openInputStream(u); if (raw == null) throw new Exception("Cannot open file.");
        try (InputStream in = raw; BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(out), 1048576)) {
          byte[] buf = java.nio.ByteBuffer.allocate(1048576).array(); long tot = 0L; long lu = 0L; int r;
          while ((r = in.read(buf)) != -1) {
            bos.write(buf, 0, r); tot += r; long now = SystemClock.uptimeMillis();
            if (now - lu >= 120L) { lu = now; final long cm = tot / 1048576L; final long tm = fTot > 0L ? (fTot / 1048576L) : 0L; final int pm = fTot > 0L ? (int) Math.min(1000L, (tot * 1000L) / fTot) : 500; runOnUiThread(() -> { setProgPermille(pm); progTxt.setText(fTot > 0L ? ("Importing Disc: " + cm + " / " + tm + " MB (" + (pm/10) + "%)") : ("Importing Disc: " + cm + " MB...")); }); }
          }
        }
        runOnUiThread(() -> { setProgPermille(1000); busy(false, ""); refresh(); Toast.makeText(this, "Legal disc imported!", Toast.LENGTH_SHORT).show(); });
      } catch (Exception e) { runOnUiThread(() -> { setProgPermille(0); busy(false, ""); err("ISO Import Failed", String.valueOf(e.getMessage())); }); }
    }).start();
  }
  @Override protected void onActivityResult(int req, int res, Intent d) { super.onActivityResult(req, res, d); if (res != RESULT_OK || d == null || d.getData() == null) return; if (req == REQ_ISO) copyIso(d.getData()); else if (req == REQ_DRV) installDriver(d.getData()); }
  void installDriver(Uri u) {
    busy(true, "Installing custom Vulkan driver...");
    new Thread(() -> {
      try {
        File dir = new File(getCodeCacheDir(), "custom_driver"); del(dir); dir.mkdirs(); String so = null;
        InputStream raw = getContentResolver().openInputStream(u); if (raw == null) throw new Exception("Could not open driver archive.");
        try (ZipInputStream z = new ZipInputStream(new BufferedInputStream(raw))) {
          ZipEntry e; byte[] buf = java.nio.ByteBuffer.allocate(65536).array();
          while ((e = z.getNextEntry()) != null) {
            if (e.isDirectory()) continue;
            String nm = new File(e.getName()).getName(); File out = new File(dir, nm);
            try (FileOutputStream fos = new FileOutputStream(out)) { int r; while ((r = z.read(buf)) != -1) fos.write(buf, 0, r); }
            out.setReadable(true, false); out.setExecutable(true, false); if (nm.endsWith(".so")) so = nm;
          }
        }
        if (so == null) throw new Exception("No .so Vulkan driver found inside archive.");
        File icd = new File(dir, "vk_icd.json");
        if (!icd.exists()) { org.json.JSONObject o = new org.json.JSONObject().put("library_path", new File(dir, so).getAbsolutePath()).put("api_version", "1.3.0"); String j = new org.json.JSONObject().put("file_format_version", "1.0.0").put("ICD", o).toString(); try (FileOutputStream fos = new FileOutputStream(icd)) { fos.write(j.getBytes("UTF-8")); } }
        final String fSo = so; getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString("driver_name", fSo).putBoolean("drv", true).apply();
        runOnUiThread(() -> { busy(false, ""); setIosSwitchVisual(5, true, false); savePrefs(); refresh(); Toast.makeText(this, "Driver installed: " + fSo, Toast.LENGTH_SHORT).show(); });
      } catch (Exception e) { runOnUiThread(() -> { busy(false, ""); err("Driver Import Failed", String.valueOf(e.getMessage())); }); }
    }).start();
  }
  void refresh() {
    File iso = new File(baseDir, "game.iso");
    if (iso.exists() && iso.length() > 1048576L) { isoBadge.setText("READY"); isoBadge.setTextColor(0xFF34D399); isoBadge.setBackground(pill(0xFF064E3B, 0xFF064E3B, 6, 0)); isoTxt.setText("game.iso  (" + (iso.length() / 1048576L) + " MB)  •  Verified"); }
    else { isoBadge.setText("MISSING"); isoBadge.setTextColor(0xFFFBBF24); isoBadge.setBackground(pill(0xFF451A03, 0xFF451A03, 6, 0)); isoTxt.setText("No legal disc imported yet."); }
    drvBadge.setText(Array.getBoolean(togState, 5) ? "CUSTOM" : "SYSTEM"); drvBadge.setTextColor(Array.getBoolean(togState, 5) ? 0xFF60A5FA : 0xFF9CA3AF); drvBadge.setBackground(pill(0xFF1E293B, 0xFF1E293B, 6, 0));
    drvTxt.setText(Array.getBoolean(togState, 5) ? getSharedPreferences(PREFS, MODE_PRIVATE).getString("driver_name", "Custom Turnip / Adreno") : "System Default Vulkan");
  }
  void launch() {
    File chkIso = new File(baseDir, "game.iso"); if (!chkIso.exists() || chkIso.length() < 1048576L) { promptIso(); return; }
    savePrefs(); if (playBtn != null) playBtn.setEnabled(false); busy(true, "Unpacking bundled mods..."); setProgPermille(100);
    new Thread(() -> {
      try {
        File md = new File(baseDir, "mods"); md.mkdirs();
        File hd = new File(md, "com.marcelus.hd-textures"); if (Array.getBoolean(togState, 0)) extractAssetZip("bundled_hd_textures.zip", hd, "HD Texture Pack"); else if (hd.exists()) del(hd);
        File rf = new File(md, "dev.foxhollow.reflections"); if (Array.getBoolean(togState, 1)) extractAssetZip("bundled_reflections.zip", rf, "Water Reflections"); else if (rf.exists()) del(rf);
      } catch (Exception ex) { final String em = String.valueOf(ex.getMessage()); runOnUiThread(() -> { if (playBtn != null) playBtn.setEnabled(true); busy(false, ""); err("Mod Unpack Failed", em); }); return; }
      runOnUiThread(() -> {
        if (playBtn != null) playBtn.setEnabled(true); setProgPermille(1000); busy(false, "");
        String[] sc = {"4.0","3.0","2.5","2.0","1.5","1.0"}, an = {"16","8","4","2","1"}, lg = {"en","fr","de","it","es"};
        Intent i = new Intent(this, FoxhollowActivity.class);
        i.putExtra("FOXHOLLOW_USER_DIR", baseDir.getAbsolutePath()); i.putExtra("FOXHOLLOW_DISC", new File(baseDir, "game.iso").getAbsolutePath());
        i.putExtra("FOXHOLLOW_MEMORY_CARD", new File(baseDir, "MemoryCardA.USA.raw").getAbsolutePath()); i.putExtra("FOXHOLLOW_AUTOSAVE", new File(baseDir, "autosave.bin").getAbsolutePath());
        i.putExtra("FOXHOLLOW_CACHE_DIR", new File(baseDir, "cache").getAbsolutePath());
        i.putExtra("FOXHOLLOW_HD_TEXTURES", Array.getBoolean(togState, 0) ? "1" : "0"); i.putExtra("FOXHOLLOW_REFLECTIONS", Array.getBoolean(togState, 1) ? "1" : "0");
        i.putExtra("FOXHOLLOW_HOLD_A", Array.getBoolean(togState, 2) ? "1" : "0"); i.putExtra("FOXHOLLOW_SHOW_FPS", Array.getBoolean(togState, 3) ? "1" : "0"); i.putExtra("FOXHOLLOW_SHOW_TEMP", Array.getBoolean(togState, 4) ? "1" : "0");
        i.putExtra("FOXHOLLOW_RENDER_SCALE", (String) Array.get(sc, Array.getInt(optIdx, 0))); i.putExtra("FOXHOLLOW_SCREEN_STYLE", Array.getInt(optIdx, 1) == 0 ? "wide" : "narrow");
        i.putExtra("FOXHOLLOW_FRAME_LIMIT", "60"); i.putExtra("FOXHOLLOW_VSYNC", Array.getInt(optIdx, 2) == 0 ? "1" : "0");
        i.putExtra("FOXHOLLOW_ANISO", (String) Array.get(an, Array.getInt(optIdx, 3))); i.putExtra("FOXHOLLOW_RENDERER", Array.getInt(optIdx, 4) == 0 ? "vulkan" : "opengles");
        i.putExtra("FOXHOLLOW_LANGUAGE", (String) Array.get(lg, Array.getInt(optIdx, 5))); i.putExtra("FOXHOLLOW_DEBUG", "0");
        File icd = new File(new File(getCodeCacheDir(), "custom_driver"), "vk_icd.json");
        if (Array.getBoolean(togState, 5) && icd.exists()) { i.putExtra("CUSTOM_VK_ICD", icd.getAbsolutePath()); i.putExtra("CUSTOM_VK_DIR", icd.getParentFile().getAbsolutePath()); }
        startActivity(i);
      });
    }).start();
  }
  void setIosSwitchVisual(int idx, boolean on, boolean anim) { Array.setBoolean(togState, idx, on); FrameLayout tr = (FrameLayout) Array.get(togTrack, idx); View th = (View) Array.get(togThumb, idx); tr.setBackground(on ? pill(0xFF34C759, 0xFF30D158, 13, 0) : pill(0xFF282F40, 0xFF282F40, 13, 0xFF3A445C)); float tx = on ? dp(18) : 0f; if (anim) th.animate().translationX(tx).setDuration(140).start(); else th.setTranslationX(tx); if (idx == 5 && drvBadge != null) { savePrefs(); refresh(); } }
  void iosSwitchRow(LinearLayout p, String l, final int idx, boolean c) { LinearLayout r = new LinearLayout(this); r.setOrientation(LinearLayout.HORIZONTAL); r.setGravity(Gravity.CENTER_VERTICAL); r.setPadding(0, dp(c ? 3 : 5), 0, dp(c ? 3 : 5)); TextView v = new TextView(this); v.setText(l); v.setTextColor(0xFFE5E7EB); v.setTextSize(c ? 11.5f : 12.5f); v.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)); FrameLayout track = new FrameLayout(this); track.setLayoutParams(new LinearLayout.LayoutParams(dp(42), dp(24))); track.setPadding(dp(3), dp(3), dp(3), dp(3)); View thumb = new View(this); thumb.setLayoutParams(new FrameLayout.LayoutParams(dp(18), dp(18))); thumb.setBackground(pill(0xFFFFFFFF, 0xFFF3F4F6, 9, 0)); thumb.setElevation(dp(2)); track.addView(thumb); Array.set(togTrack, idx, track); Array.set(togThumb, idx, thumb); View.OnClickListener flip = w -> setIosSwitchVisual(idx, !Array.getBoolean(togState, idx), true); r.setOnClickListener(flip); track.setOnClickListener(flip); r.addView(v); r.addView(track); p.addView(r); }
  void savePrefs() { SharedPreferences.Editor e = getSharedPreferences(PREFS, MODE_PRIVATE).edit(); for (int k = 0; k < 6; k++) e.putInt("o" + k, Array.getInt(optIdx, k)); for (int t = 0; t < 6; t++) e.putBoolean("t" + t, Array.getBoolean(togState, t)); e.putBoolean("drv", Array.getBoolean(togState, 5)).apply(); }
  void loadPrefs() { SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE); int[] def = {3, 0, 0, 0, 0, 0}; for (int k = 0; k < 6; k++) { int v = p.getInt("o" + k, Array.getInt(def, k)); Array.setInt(optIdx, k, v); String[] row = (String[]) Array.get(optItems, k); ((TextView) Array.get(optVal, k)).setText(((String) Array.get(row, v)) + " ▾"); } boolean[] tdef = {true, true, true, false, false, false}; for (int t = 0; t < 6; t++) setIosSwitchVisual(t, p.getBoolean("t" + t, Array.getBoolean(tdef, t)), false);  }
  void busy(boolean b, String m) { progBar.setVisibility(b ? View.VISIBLE : View.GONE); progTxt.setVisibility(b ? View.VISIBLE : View.GONE); progTxt.setText(m); }
  void err(String t, String m) { new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert).setTitle(t).setMessage(m).setPositiveButton("OK", null).show(); }
  void del(File f) { if (f.isDirectory()) { File[] c = f.listFiles(); if (c != null) for (File x : c) del(x); } f.delete(); }
  LinearLayout box(LinearLayout p, boolean fill, boolean c) { LinearLayout b = new LinearLayout(this); b.setOrientation(LinearLayout.VERTICAL); b.setBackground(pill(0xFF131823, 0xFF131823, 14, 0xFF232B3E)); b.setPadding(dp(c ? 12 : 15), dp(c ? 10 : 13), dp(c ? 12 : 15), dp(c ? 10 : 13)); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, fill ? 0 : ViewGroup.LayoutParams.WRAP_CONTENT, fill ? 1f : 0f); if (!fill) lp.setMargins(0, 0, 0, dp(c ? 7 : 9)); b.setLayoutParams(lp); p.addView(b); return b; }
  TextView hdrBadge(LinearLayout p, String title, String badge) { LinearLayout r = new LinearLayout(this); r.setOrientation(LinearLayout.HORIZONTAL); r.setGravity(Gravity.CENTER_VERTICAL); p.addView(r); TextView tv = new TextView(this); tv.setText(title); tv.setTextColor(0xFFF3F4F6); tv.setTextSize(11f); tv.setTypeface(Typeface.DEFAULT_BOLD); tv.setLetterSpacing(0.06f); tv.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)); r.addView(tv); TextView bg = new TextView(this); bg.setText(badge); bg.setTextColor(0xFFFF9E44); bg.setTextSize(9f); bg.setTypeface(Typeface.DEFAULT_BOLD); bg.setBackground(pill(0xFF2A1D12, 0xFF2A1D12, 6, 0)); bg.setPadding(dp(6), dp(2), dp(6), dp(2)); r.addView(bg); return bg; }
  TextView info(LinearLayout p, boolean c) { TextView v = new TextView(this); v.setTextColor(0xFF9CA3AF); v.setTextSize(c ? 11f : 12f); v.setPadding(0, dp(c ? 4 : 6), 0, dp(c ? 5 : 7)); p.addView(v); return v; }
  Button secBtn(String l, boolean c) { Button b = new Button(this); b.setText(l); b.setTextColor(0xFFE5E7EB); b.setTextSize(c ? 11.5f : 12f); b.setTypeface(Typeface.DEFAULT_BOLD); b.setAllCaps(false); b.setBackground(pill(0xFF1C2333, 0xFF1C2333, 9, 0xFF333D54)); b.setPadding(dp(10), dp(c ? 6 : 8), dp(10), dp(c ? 6 : 8)); return b; }
  Switch tog(LinearLayout p, String l, boolean c) { Switch s = new Switch(this); s.setText(l); s.setTextColor(0xFFE5E7EB); s.setTextSize(c ? 11.5f : 12.5f); s.setPadding(0, dp(c ? 3 : 5), 0, dp(c ? 3 : 5)); p.addView(s); return s; }
  void showDropdown(final TextView anchor, final int k) { LinearLayout menu = new LinearLayout(this); menu.setOrientation(LinearLayout.VERTICAL); menu.setBackground(pill(0xFF1A202E, 0xFF131823, 12, 0xFF3A445E)); menu.setPadding(dp(6), dp(6), dp(6), dp(6)); final PopupWindow pop = new PopupWindow(menu, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true); pop.setElevation(dp(14)); String[] row = (String[]) Array.get(optItems, k); for (int j = 0; j < row.length; j++) { final int idx = j; TextView item = new TextView(this); String lbl = (String) Array.get(row, j); item.setText(lbl); boolean sel = (idx == Array.getInt(optIdx, k)); item.setTextColor(sel ? 0xFFFF9E44 : 0xFFE5E7EB); item.setTypeface(sel ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT); item.setTextSize(12f); item.setPadding(dp(14), dp(8), dp(18), dp(8)); if (sel) item.setBackground(pill(0xFF262E42, 0xFF262E42, 8, 0)); item.setOnClickListener(v -> { Array.setInt(optIdx, k, idx); anchor.setText(lbl + " ▾"); pop.dismiss(); }); menu.addView(item); } pop.showAsDropDown(anchor, 0, dp(4)); }
  void optRow(LinearLayout p, String l, final int k, boolean c) { LinearLayout r = new LinearLayout(this); r.setOrientation(LinearLayout.HORIZONTAL); r.setGravity(Gravity.CENTER_VERTICAL); r.setPadding(0, dp(c ? 2 : 4), 0, dp(c ? 2 : 4)); TextView v = new TextView(this); v.setText(l); v.setTextColor(0xFFE5E7EB); v.setTextSize(c ? 11.5f : 12.5f); v.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)); TextView pillBtn = new TextView(this); pillBtn.setTextColor(0xFFFF9E44); pillBtn.setTextSize(c ? 11f : 12f); pillBtn.setTypeface(Typeface.DEFAULT_BOLD); pillBtn.setBackground(pill(0xFF1C2333, 0xFF1C2333, 8, 0xFF333D54)); pillBtn.setPadding(dp(c ? 9 : 12), dp(c ? 5 : 7), dp(c ? 9 : 12), dp(c ? 5 : 7)); pillBtn.setOnClickListener(w -> showDropdown(pillBtn, k)); Array.set(optVal, k, pillBtn); r.addView(v); r.addView(pillBtn); p.addView(r); }
}
