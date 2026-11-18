package featurecat.lizzie;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;
import featurecat.lizzie.analysis.EngineManager;
import featurecat.lizzie.gui.UiMemoryCleaner;

/**
 * 定时释放长期运行积累的界面缓存，并把进程工作集交还给系统。
 *
 * <p>使用 System.gc + Windows EmptyWorkingSet/SetProcessWorkingSetSize，等价于任务管理器/PC Manager
 * 对进程执行的“清空工作集”。只回收空闲内存，不释放正在使用的棋局数据。
 */
public final class MemoryJanitor {
  private static final long FIRST_DELAY_MS = 20_000L;
  private static final long INTERVAL_MS = 180_000L;
  private static volatile boolean started;

  private MemoryJanitor() {}

  public static void start() {
    if (started) return;
    started = true;
    Thread thread =
        new Thread(
            () -> {
              try {
                Thread.sleep(FIRST_DELAY_MS);
              } catch (InterruptedException ignored) {
                return;
              }
              while (true) {
                trimIfIdle();
                try {
                  Thread.sleep(INTERVAL_MS);
                } catch (InterruptedException ignored) {
                  return;
                }
              }
            },
            "memory-janitor");
    thread.setDaemon(true);
    thread.start();
  }

  private static void trimIfIdle() {
    try {
      if (Lizzie.frame == null || !Lizzie.frame.isVisible()) return;
      // 引擎正在思考/对局时不做强制 GC，避免影响计算。
      if (EngineManager.isEngineGame
          || (Lizzie.leelaz != null && (Lizzie.leelaz.isThinking || Lizzie.leelaz.isPondering()))) {
        return;
      }
      UiMemoryCleaner.flushWindowsSkinCache();
      System.gc();
      trimWorkingSet();
    } catch (Throwable ignored) {
    }
  }

  private interface Psapi extends Library {
    Psapi INSTANCE = Native.load("psapi", Psapi.class);

    boolean EmptyWorkingSet(Pointer process);
  }

  private interface Kernel32 extends Library {
    Kernel32 INSTANCE = Native.load("kernel32", Kernel32.class);

    Pointer GetCurrentProcess();

    boolean SetProcessWorkingSetSize(Pointer process, NativeLong min, NativeLong max);
  }

  private static void trimWorkingSet() {
    if (!System.getProperty("os.name", "").toLowerCase().contains("win")) return;
    try {
      Pointer process = Kernel32.INSTANCE.GetCurrentProcess();
      Psapi.INSTANCE.EmptyWorkingSet(process);
      Kernel32.INSTANCE.SetProcessWorkingSetSize(process, new NativeLong(-1), new NativeLong(-1));
    } catch (Throwable ignored) {
    }
  }
}
