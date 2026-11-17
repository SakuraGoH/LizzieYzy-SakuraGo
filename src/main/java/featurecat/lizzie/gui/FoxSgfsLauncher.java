package featurecat.lizzie.gui;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import featurecat.lizzie.Lizzie;
import featurecat.lizzie.util.Utils;
import java.awt.Window;
import java.io.File;
import java.io.IOException;

/**
 * 以独立 exe 方式启动 FoxSgfsDownload，和 readboard.exe 的使用方式一致。 程序随 jar 一起分发，首次运行释放到 foxSgfsDownload 目录。
 */
public final class FoxSgfsLauncher {
  private static final String TITLE = "Fox Kifu Studio · 野狐棋谱库";
  private static final String EXE_NAME = "FoxSgfsDownload-Pro.exe";
  private static Process process;

  private FoxSgfsLauncher() {}

  public interface WinUser extends Library {
    WinUser INSTANCE = Native.load("user32", WinUser.class);

    Pointer FindWindow(String className, String windowName);

    boolean ShowWindow(Pointer hwnd, int cmd);

    boolean SetForegroundWindow(Pointer hwnd);
  }

  public static void launch(Window owner) {
    File dir = new File("foxSgfsDownload");
    File exe = new File(dir, EXE_NAME);
    if (!exe.exists()) {
      Utils.copyFoxSgfsDownload();
    }
    if (!exe.exists()) {
      Utils.showMsg(
          Lizzie.resourceBundle.getString("FoxKifuDownload.exeMissing")
              + "\n"
              + exe.getAbsolutePath());
      return;
    }
    if (process != null && process.isAlive()) {
      bringToFront();
      return;
    }
    if (bringToFront()) return;
    try {
      ProcessBuilder builder = new ProcessBuilder(exe.getAbsolutePath());
      builder.directory(dir);
      builder.redirectErrorStream(true);
      process = builder.start();
      new Thread(
              () -> {
                try {
                  Thread.sleep(1200);
                  bringToFront();
                } catch (InterruptedException ignored) {
                }
              },
              "fox-sgfs-front")
          .start();
    } catch (IOException e) {
      Utils.showMsg(
          Lizzie.resourceBundle.getString("FoxKifuDownload.exeLaunchFailed")
              + "\n"
              + e.getMessage());
      e.printStackTrace();
    }
  }

  private static boolean bringToFront() {
    if (!System.getProperty("os.name", "").toLowerCase().contains("win")) return false;
    try {
      Pointer hwnd = WinUser.INSTANCE.FindWindow(null, TITLE);
      if (hwnd != null) {
        WinUser.INSTANCE.ShowWindow(hwnd, 9);
        WinUser.INSTANCE.SetForegroundWindow(hwnd);
        return true;
      }
    } catch (Throwable ignored) {
    }
    return false;
  }
}
