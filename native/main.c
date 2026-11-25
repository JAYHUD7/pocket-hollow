#define _GNU_SOURCE
#include <dlfcn.h>
#include <unistd.h>
#include <malloc.h>
#include <sched.h>
#include <sys/resource.h>
#include <SDL3/SDL_main.h>
#include <android/log.h>
#include <aurora/aurora.h>
#include <aurora/dvd.h>
#include <aurora/event.h>
#include <aurora/main.h>
#include <dolphin/dvd.h>
#include <dolphin/gx/GXAurora.h>
#include <dolphin/vi.h>
#include <SDL3/SDL_filesystem.h>
#include <SDL3/SDL_video.h>

#include "foxhollow_config.h"
#include "foxhollow_crash.h"
#include "foxhollow_mods.h"
#include "foxhollow_splash.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

static void fhEnableAndroidAdpfSession(void) {
  void* lib = dlopen("libandroid.so", RTLD_NOW | RTLD_LOCAL);
  if (!lib) return;
  typedef void* (*FnGetMgr)(void);
  typedef void* (*FnCreateSess)(void*, const int32_t*, size_t, int64_t);
  typedef int (*FnReportWork)(void*, int64_t);
  FnGetMgr getMgr = (FnGetMgr)dlsym(lib, "APerformanceHint_getManager");
  FnCreateSess createSess = (FnCreateSess)dlsym(lib, "APerformanceHint_createSession");
  FnReportWork reportWork = (FnReportWork)dlsym(lib, "APerformanceHint_reportActualWorkDuration");
  if (getMgr && createSess) {
    void* mgr = getMgr();
    if (mgr) {
      int32_t tids[1] = { (int32_t)gettid() };
      void* sess = createSess(mgr, tids, 1, 16666667LL);
      if (sess && reportWork) {
        reportWork(sess, 16500000LL);
      }
    }
  }
}

int gameMain(int argc, char** argv);
void foxhollowFramePumpInit(void);

typedef struct FoxhollowDiscMapping {
  char gameName[4];
  unsigned char revision;
  const char* label;
} FoxhollowDiscMapping;

static const FoxhollowDiscMapping sDiscMappings[] = {
    {{'G', 'S', 'A', 'E'}, 0, "USA 1.0"},
    {{'G', 'S', 'A', 'E'}, 1, "USA 1.1"},
    {{'G', 'S', 'A', 'J'}, 0, "Japan 1.0"},
    {{'G', 'S', 'A', 'P'}, 0, "Europe 1.0"},
    {{'G', 'S', 'A', 'P'}, 1, "Europe 1.1"},
};

static const FoxhollowDiscMapping* disc_mapping(const DVDDiskID* id) {
  if (id == NULL || memcmp(id->company, "01", sizeof(id->company)) != 0 || id->diskNumber != 0) {
    return NULL;
  }

  for (size_t i = 0; i < sizeof(sDiscMappings) / sizeof(sDiscMappings[0]); i++) {
    const FoxhollowDiscMapping* mapping = &sDiscMappings[i];
    if (memcmp(id->gameName, mapping->gameName, sizeof(id->gameName)) == 0 && id->gameVersion == mapping->revision) {
      return mapping;
    }
  }

  return NULL;
}

static void log_callback(AuroraLogLevel level, const char* module, const char* message, unsigned int len) {
  const char* levelStr;
  FILE* out = stdout;
  switch (level) {
  case LOG_DEBUG:
    levelStr = "DEBUG";
    break;
  case LOG_INFO:
    levelStr = "INFO";
    break;
  case LOG_WARNING:
    levelStr = "WARNING";
    break;
  case LOG_ERROR:
    levelStr = "ERROR";
    out = stderr;
    break;
  case LOG_FATAL:
    levelStr = "FATAL";
    out = stderr;
    break;
  }
  __android_log_print(ANDROID_LOG_INFO, "foxhollow", "[%s] %s: %s", levelStr, module, message); fprintf(out, "[%s] %s: %s\n", levelStr, module, message);
  if (level == LOG_FATAL) {
    fflush(out);
    abort();
  }
}

static const char* disc_path(int argc, char* argv[]) {
  if (argc > 1) {
    return argv[1];
  }
  const char* d = getenv("FOXHOLLOW_DISC"); if (d && d[0]) return d; return "/storage/emulated/0/pocket-hollow/game.iso";
}

static int texture_dumps_enabled(void) {
  const char* value = getenv("FOXHOLLOW_TEXTURE_DUMPS");
  if (value == NULL || value[0] == '\0') {
    return 0;
  }
  return strcmp(value, "0") != 0 && strcmp(value, "false") != 0 && strcmp(value, "off") != 0;
}

static const char* shader_cache_path(void) {
  const char* path = getenv("FOXHOLLOW_SHADER_CACHE");
  if (path == NULL || path[0] == '\0') {
    return NULL;
  }
  return path;
}

static const char* prepare_directory(const char* path) {
  if (path == NULL) {
    return NULL;
  }
  if (!SDL_CreateDirectory(path)) {
    __android_log_print(ANDROID_LOG_ERROR, "foxhollow", "foxhollow: failed to create directory %s: %s\n", path, SDL_GetError());
    return NULL;
  }
  return path;
}

int main(int argc, char* argv[]) { setpriority(PRIO_PROCESS, 0, -19);
        cpu_set_t bigCores;
        CPU_ZERO(&bigCores);
        int nCpu = (int)sysconf(_SC_NPROCESSORS_CONF); if (nCpu < 4) nCpu = 8; for (int c = nCpu / 2; c < nCpu; ++c) { CPU_SET(c, &bigCores); }
        sched_setaffinity(0, sizeof(bigCores), &bigCores);
        fhEnableAndroidAdpfSession();
        setenv("FOXHOLLOW_USER_DIR", "/storage/emulated/0/pocket-hollow", 0); setenv("FOXHOLLOW_CACHE_DIR", "/storage/emulated/0/pocket-hollow/cache", 0); setenv("FOXHOLLOW_FULLSCREEN", "1", 0); setenv("FOXHOLLOW_BACKEND", "vulkan", 0); setenv("FOXHOLLOW_SCREEN_STYLE", "wide", 0);
  const char* disc;
  fhInstallCrashHandler();
  disc = disc_path(argc, argv);
  const FoxhollowDiscMapping* mapping;
  const int revision = fhConfigRevision();
  if (revision < 0) {
    __android_log_print(ANDROID_LOG_ERROR, "foxhollow", "foxhollow: FOXHOLLOW_REV must be 0 or 1\n");
    return 1;
  }
  if (!disc) {
    __android_log_print(ANDROID_LOG_ERROR, "foxhollow", "usage: foxhollow <path-to-disc.iso|rvz>\n(or set FOXHOLLOW_DISC)\n");
    return 1;
  }

  const AuroraConfig config = {
      .appName = "Foxhollow",
      .userPath = prepare_directory(fhConfigUserPath()),
      .cachePath = prepare_directory(fhConfigCachePath()),
      .resourcesPath = NULL,
      .desiredBackend = fhConfigBackend(),
        .msaa = 1,
      .logCallback = &log_callback,
      .vsync = fhConfigVsync() != 0,
      .startFullscreen = fhConfigFullscreen() != 0,
      .allowTextureDumps = texture_dumps_enabled() != 0,
      .windowPosX = SDL_WINDOWPOS_CENTERED,
      .windowPosY = SDL_WINDOWPOS_CENTERED,
      .mem1Size = 128 * 1024 * 1024,
      .mem2Size = ARAM_DEFAULT_SIZE,
  };
  const AuroraInfo info = aurora_initialize(argc, argv, &config);

  AuroraSetViewportPolicy(AURORA_VIEWPORT_FIT);
  AuroraSetDisplayAspect(fhConfigDisplayAspect());
  VISetFrameBufferScale(fhConfigRenderScale());

  if (!aurora_dvd_open(disc)) {
    __android_log_print(ANDROID_LOG_ERROR, "foxhollow", "foxhollow: failed to open disc image: %s\n", disc);
    aurora_shutdown();
    return 1;
  }

  mapping = disc_mapping(DVDGetCurrentDiskID());
  if (mapping == NULL) {
    const DVDDiskID* id = DVDGetCurrentDiskID();
    __android_log_print(ANDROID_LOG_ERROR, "foxhollow", "foxhollow: unsupported disc %.4s%.2s revision %u, disc %u\n", id->gameName, id->company,
            id->gameVersion, id->diskNumber);
    aurora_dvd_close();
    aurora_shutdown();
    return 1;
  }
  __android_log_print(ANDROID_LOG_INFO, "foxhollow", "foxhollow: using %s disc with the GSAE01 asset mapping and USA 1.%d code\n", mapping->label,
          revision);

  fhModsInit(argc, argv, info.userPath);

  extern void aurora_dvd_preload_full_iso(void);
  /* Stream ISO from storage to keep RAM usage low */
  if (!fhSplashRun()) {
    fhModsShutdown();
    aurora_dvd_close();
    aurora_shutdown();
    return 0;
  }

  foxhollowFramePumpInit();
  return gameMain(argc, argv);
}

