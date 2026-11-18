package featurecat.lizzie.util;

import java.awt.SplashScreen;

/** 关闭由 jar manifest 的 SplashScreen-Image 创建的启动画面。 */
public final class StartupSplash {
  private StartupSplash() {}

  public static void close() {
    try {
      SplashScreen splash = SplashScreen.getSplashScreen();
      if (splash != null) splash.close();
    } catch (Throwable ignored) {
    }
  }
}
