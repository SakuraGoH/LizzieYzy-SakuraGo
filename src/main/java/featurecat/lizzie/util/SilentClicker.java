package featurecat.lizzie.util;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;

/**
 * 不移动鼠标的 Windows 点击。
 *
 * <p>使用 WindowFromPoint + ScreenToClient 找到目标窗口，再向其消息队列发送 WM_LBUTTONDOWN/UP。
 * 这样自动落子不会占用或移动用户当前鼠标，也不会打断用户的其他点击。
 */
public final class SilentClicker {
  private static final int WM_MOUSEMOVE = 0x0200;
  private static final int WM_LBUTTONDOWN = 0x0201;
  private static final int WM_LBUTTONUP = 0x0202;
  private static final boolean WINDOWS =
      System.getProperty("os.name", "").toLowerCase().contains("win");

  private static final User32 USER32 = WINDOWS ? Native.load("user32", User32.class) : null;

  private SilentClicker() {}

  public interface User32 extends Library {
    Pointer WindowFromPoint(POINT.ByValue point);

    boolean ScreenToClient(Pointer hwnd, POINT point);

    boolean PostMessageW(Pointer hwnd, int msg, long wParam, long lParam);
  }

  @Structure.FieldOrder({"x", "y"})
  public static class POINT extends Structure {
    public int x;
    public int y;

    public POINT() {}

    public POINT(int x, int y) {
      this.x = x;
      this.y = y;
    }

    public static class ByValue extends POINT implements Structure.ByValue {
      public ByValue(int x, int y) {
        super(x, y);
      }
    }
  }

  /** 在屏幕坐标处静默点击。返回 false 表示没有找到目标窗口或发送失败。 */
  public static boolean click(int screenX, int screenY) {
    if (!WINDOWS || USER32 == null) return false;
    try {
      POINT.ByValue screen = new POINT.ByValue(screenX, screenY);
      Pointer hwnd = USER32.WindowFromPoint(screen);
      if (hwnd == null) return false;
      POINT client = new POINT(screenX, screenY);
      if (!USER32.ScreenToClient(hwnd, client)) return false;
      long lParam = ((long) (client.y & 0xFFFF) << 16) | (client.x & 0xFFFF);
      USER32.PostMessageW(hwnd, WM_MOUSEMOVE, 1L, lParam);
      boolean down = USER32.PostMessageW(hwnd, WM_LBUTTONDOWN, 1L, lParam);
      boolean up = USER32.PostMessageW(hwnd, WM_LBUTTONUP, 0L, lParam);
      return down && up;
    } catch (Throwable ignored) {
      return false;
    }
  }
}
