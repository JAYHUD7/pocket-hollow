#include <dolphin/types.h>
#include <dolphin/vi.h>
#include <aurora/aurora.h>
#include <aurora/dvd.h>
#include <aurora/event.h>
#include <SDL3/SDL_timer.h>
#include <stdlib.h>
#include "foxhollow_autosave.h"
#include "foxhollow_compat.h"
#include "foxhollow_config.h"
#include "foxhollow_mods.h"
#include "foxhollow_quit.h"
#include "foxhollow_splash.h"
#include "shim_log.h"

typedef void (*VIRetraceCallback)(u32 retraceCount);

#if defined(FOXHOLLOW_DEBUG_SHORTCUTS)
void fhDebugOverlayDraw(void);
void fhGalleryDrawOverlay(void);
#endif
#if defined(FOXHOLLOW_DEBUG_INPUT_TIMING)
void fhDebugOverlayFrameBegin(void);
void fhDebugOverlayFramePresented(void);
#endif
void fhGalleryUpdate(void);

static VIRetraceCallback sPreRetraceCallback;
static VIRetraceCallback sPostRetraceCallback;
static void* sNextFrameBuffer;
static u32 sRetraceCount;
static int sFrameOpen;
static int sBlack;
static Uint64 sNextRetraceNs;

void fhGXCompleteFrame(void);

enum { VI_RETRACE_DEFAULT_HZ = 60 };

static void wait_for_retrace_deadline(void) {
  const int limit = fhConfigFrameLimit();
  Uint64 periodNs;
  Uint64 now;

  if (limit == 0) {
    sNextRetraceNs = 0;
    return;
  }

  periodNs = SDL_NS_PER_SECOND / (Uint64)(limit > 0 ? limit : VI_RETRACE_DEFAULT_HZ);
  now = SDL_GetTicksNS();

  if (sNextRetraceNs == 0) {
    sNextRetraceNs = now + periodNs;
  } else if (now >= sNextRetraceNs) {
    sNextRetraceNs = now + periodNs;
    return;
  }

  const Uint64 deadline = sNextRetraceNs;
  sNextRetraceNs += periodNs;
  if (now + 1200000ULL < deadline) {
    SDL_DelayPrecise(deadline - now - 1200000ULL);
  }
  while (SDL_GetTicksNS() + 150000ULL < deadline) {
#if defined(__aarch64__)
    __asm__ __volatile__("yield" ::: "memory");
#endif
  }
}

static void shutdown_and_exit(void) {
  fhAutosaveShutdown();
  fhModsShutdown();
  aurora_dvd_close();
  aurora_shutdown();
  exit(0);
}

static void pump_events(void) {
  const AuroraEvent* event = aurora_update();
  int quitting = fhQuitRequested();

  while (event != NULL && event->type != AURORA_NONE) {
    if (event->type == AURORA_EXIT) {
      quitting = 1;
    }
    ++event;
  }
  if (quitting) {
    shutdown_and_exit();
  }
}

#include <android/log.h>
#include <dlfcn.h>
#include <time.h>
#include <unistd.h>

static void* sAdpfSession = NULL;
static int (*sAdpfReportWork)(void*, int64_t) = NULL;
static int64_t sFrameWorkStartNs = 0;

static int64_t fhNowNs(void) {
  struct timespec ts;
  clock_gettime(CLOCK_MONOTONIC, &ts);
  return (int64_t)ts.tv_sec * 1000000000LL + (int64_t)ts.tv_nsec;
}

static void fhAdpfInitIfNeeded(void) {
  static int sTried = 0;
  if (sTried) return;
  sTried = 1;
  void* lib = dlopen("libandroid.so", RTLD_NOW | RTLD_LOCAL);
  if (!lib) return;
  typedef void* (*FnGetMgr)(void);
  typedef void* (*FnCreateSess)(void*, const int32_t*, size_t, int64_t);
  FnGetMgr getMgr = (FnGetMgr)dlsym(lib, "APerformanceHint_getManager");
  FnCreateSess createSess = (FnCreateSess)dlsym(lib, "APerformanceHint_createSession");
  sAdpfReportWork = (int (*)(void*, int64_t))dlsym(lib, "APerformanceHint_reportActualWorkDuration");
  if (getMgr && createSess) {
    void* mgr = getMgr();
    if (mgr) {
      int32_t tid = (int32_t)gettid(); sAdpfSession = createSess(mgr, &tid, 1, 16666667LL);
    }
  }
}

static void fhAdpfBeginFrame(void) {
  fhAdpfInitIfNeeded();
  sFrameWorkStartNs = fhNowNs();
}

static void fhAdpfReportFrameWork(void) {
  if (sFrameWorkStartNs > 0) {
    int64_t workNs = fhNowNs() - sFrameWorkStartNs;
    if (sAdpfSession && sAdpfReportWork && workNs > 100000LL && workNs < 100000000LL) {
      sAdpfReportWork(sAdpfSession, workNs);
    }
  }
}

void fhAdpfBoostMapLoad(void) {
  fhAdpfInitIfNeeded();
  if (sAdpfSession && sAdpfReportWork) {
    sAdpfReportWork(sAdpfSession, 33333333LL);
  }
}

#include <stdatomic.h>
static _Atomic uint32_t gFhPresentedFrames = 0;
static _Atomic uint32_t gFhLastFrameUs = 0;
static int64_t sPrevPresentNs = 0;
uint32_t fhGetPresentedFrames(void) { return atomic_load_explicit(&gFhPresentedFrames, memory_order_relaxed); }
static _Atomic uint32_t gFhFrameCount = 0; uint32_t fhGetLastFrameUs(void) { return atomic_load_explicit(&gFhLastFrameUs, memory_order_relaxed); }
void foxhollowFramePumpInit(void) {
  pump_events();
  sFrameOpen = aurora_begin_frame();
  fhAdpfBeginFrame();
}

void VIWaitForRetrace(void) {
  if (sFrameOpen) {
    fhModsDraw();
#if defined(FOXHOLLOW_DEBUG_SHORTCUTS)
    fhDebugOverlayDraw();
    fhGalleryDrawOverlay();
#endif
    if (sBlack) {
      fhDrawBlackScreen();
    }
    fhGXCompleteFrame();
    aurora_end_frame(); atomic_fetch_add_explicit(&gFhFrameCount, 1u, memory_order_relaxed);
    fhAdpfReportFrameWork();
#if defined(FOXHOLLOW_DEBUG_INPUT_TIMING)
    fhDebugOverlayFramePresented();
#endif
    sFrameOpen = 0;
  }
  fhModsUpdate();
  fhGalleryUpdate();
  fhAutosaveUpdate();
  wait_for_retrace_deadline();
  pump_events();
  while (!aurora_begin_frame()) {
    pump_events();
  }
  sFrameOpen = 1;
  fhAdpfBeginFrame();
#if defined(FOXHOLLOW_DEBUG_INPUT_TIMING)
  fhDebugOverlayFrameBegin();
#endif
  fhAIPump();

  sRetraceCount++;
  if (sPreRetraceCallback) {
    sPreRetraceCallback(sRetraceCount);
  }
  if (sPostRetraceCallback) {
    sPostRetraceCallback(sRetraceCount);
  }
}

VIRetraceCallback VISetPreRetraceCallback(VIRetraceCallback cb) {
  VIRetraceCallback prev = sPreRetraceCallback;
  sPreRetraceCallback = cb;
  return prev;
}

VIRetraceCallback VISetPostRetraceCallback(VIRetraceCallback cb) {
  VIRetraceCallback prev = sPostRetraceCallback;
  sPostRetraceCallback = cb;
  return prev;
}

void VISetNextFrameBuffer(void* fb) {
  sNextFrameBuffer = fb;
  int64_t nowNs = fhNowNs();
  if (sPrevPresentNs > 0) {
    int64_t dtUs = (nowNs - sPrevPresentNs) / 1000LL;
    if (dtUs > 0 && dtUs < 2000000LL) {
      atomic_store_explicit(&gFhLastFrameUs, (uint32_t)dtUs, memory_order_relaxed);
    }
  }
  sPrevPresentNs = nowNs;
  atomic_fetch_add_explicit(&gFhPresentedFrames, 1u, memory_order_relaxed);
}
void* VIGetNextFrameBuffer(void) { return sNextFrameBuffer; }
void VISetBlack(BOOL black) { sBlack = black ? 1 : 0; }
u32 VIGetRetraceCount(void) { return sRetraceCount; }
u32 VIGetNextField(void) { return 0; }
u32 VIGetDTVStatus(void) { return 1; }

#if defined(__ANDROID__)
#include <jni.h>

#endif