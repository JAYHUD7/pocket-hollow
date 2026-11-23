package dev.foxhollow.android;

import android.content.Intent;
import android.os.Bundle;
import android.system.Os;
import dev.encounter.borealis.BorealisActivity;

public class FoxhollowActivity extends BorealisActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Intent i = getIntent();
        if (i != null) {
            String[] keys = {
                "FOXHOLLOW_USER_DIR", "FOXHOLLOW_CACHE_DIR", "FOXHOLLOW_DISC", "FOXHOLLOW_MEMORY_CARD", "FOXHOLLOW_AUTOSAVE", "FOXHOLLOW_REFLECTIONS", "FOXHOLLOW_DISC",
                "FOXHOLLOW_HD_TEXTURES", "FOXHOLLOW_HOLD_A",
                "FOXHOLLOW_SHOW_FPS", "FOXHOLLOW_SHOW_TEMP",
                "FOXHOLLOW_RENDER_SCALE", "FOXHOLLOW_SCREEN_STYLE",
                "FOXHOLLOW_FRAME_LIMIT", "FOXHOLLOW_VSYNC",
                "FOXHOLLOW_ANISO", "FOXHOLLOW_RENDERER",
                "FOXHOLLOW_LANGUAGE", "FOXHOLLOW_DEBUG"
            };
            for (String k : keys) {
                String v = i.getStringExtra(k);
                if (v != null && !v.isEmpty()) {
                    try { Os.setenv(k, v, true); } catch (Throwable ignored) {}
                }
            }
            String renderer = i.getStringExtra("FOXHOLLOW_RENDERER");
            if (renderer != null && !renderer.isEmpty()) {
                try { Os.setenv("FOXHOLLOW_BACKEND", renderer, true); } catch (Throwable ignored) {}
            }
            String icd = i.getStringExtra("CUSTOM_VK_ICD");
            if (icd != null && !icd.isEmpty()) {
                try {
                    Os.setenv("VK_ICD_FILENAMES", icd, true);
                    Os.setenv("VK_DRIVER_FILES", icd, true);
                } catch (Throwable ignored) {}
            }
        }
        super.onCreate(savedInstanceState);
    }
}