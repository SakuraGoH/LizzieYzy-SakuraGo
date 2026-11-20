package featurecat.lizzie.util;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import java.awt.Window;

/** 启动时把主窗口真正带到前台。Windows 下仅靠 Java 的 toFront() 常被前台窗口限制忽略。 */
public final class WindowFocusHelper {
  private static final boolean WINDOWS =
      System.getProperty("os.name", "").toLowerCase().contains("win");
  private static final User32 USER32 = WINDOWS ? Native.load("user32", User32.class) : null;
  private static final Kernel32 KERNEL32 = WINDOWS ? Native.load("kernel32", Kernel32.class) : null;

  private WindowFocusHelper() {}

  private interface User32 extends Library {
    boolean ShowWindow(Pointer hwnd, int cmd);

    boolean BringWindowToTop(Pointer hwnd);

    boolean SetForegroundWindow(Pointer hwnd);

    Pointer SetFocus(Pointer hwnd);

    Pointer SetActiveWindow(Pointer hwnd);

    void SwitchToThisWindow(Pointer hwnd, boolean altTab);

    Pointer GetForegroundWindow();

    int GetWindowThreadProcessId(Pointer hwnd, IntByReference processId);

    boolean AttachThreadInput(int idAttach, int idAttachTo, boolean attach);

    void keybd_event(byte bVk, byte bScan, int dwFlags, int dwExtraInfo);

    int GetWindowLong(Pointer hwnd, int index);

    int SetWindowLong(Pointer hwnd, int index, int value);
  }

  private interface Kernel32 extends Library {
    int GetCurrentThreadId();
  }

  public static void setClickThrough(Window window) {
    if (window == null || !WINDOWS || USER32 == null) return;
    try {
      Pointer hwnd = Native.getWindowPointer(window);
      if (hwnd == null) return;
      final int gwlExStyle = -20;
      final int wsExLayered = 0x00080000;
      final int wsExTransparent = 0x00000020;
      int style = USER32.GetWindowLong(hwnd, gwlExStyle);
      USER32.SetWindowLong(hwnd, gwlExStyle, style | wsExLayered | wsExTransparent);
    } catch (Throwable ignored) {
    }
  }

  public static void forceForeground(Window window) {
    if (window == null) return;
    if (WINDOWS && USER32 != null && KERNEL32 != null) {
      try {
        Pointer hwnd = Native.getWindowPointer(window);
        if (hwnd != null) {
          Pointer currentForeground = USER32.GetForegroundWindow();
          if (currentForeground != null && currentForeground.equals(hwnd)) {
            window.toFront();
            return;
          }
          USER32.ShowWindow(hwnd, 9); // SW_RESTORE
          USER32.BringWindowToTop(hwnd);

          Pointer foreground = USER32.GetForegroundWindow();
          int foregroundThread =
              foreground == null
                  ? 0
                  : USER32.GetWindowThreadProcessId(foreground, new IntByReference());
          int currentThread = KERNEL32.GetCurrentThreadId();
          boolean attached =
              foregroundThread != 0
                  && foregroundThread != currentThread
                  && USER32.AttachThreadInput(currentThread, foregroundThread, true);
          // 模拟一次 Alt 键，绕过 Windows 的前台窗口锁定。
          USER32.keybd_event((byte) 0x12, (byte) 0, 0, 0);
          USER32.keybd_event((byte) 0x12, (byte) 0, 2, 0);
          boolean focused = USER32.SetForegroundWindow(hwnd);
          USER32.SetActiveWindow(hwnd);
          USER32.SetFocus(hwnd);
          USER32.SwitchToThisWindow(hwnd, true);
          if (attached) USER32.AttachThreadInput(currentThread, foregroundThread, false);
          if (!focused) {
            window.toFront();
            window.requestFocus();
          }
        }
      } catch (Throwable ignored) {
      }
    }
    window.toFront();
    window.requestFocus();
  }
}
