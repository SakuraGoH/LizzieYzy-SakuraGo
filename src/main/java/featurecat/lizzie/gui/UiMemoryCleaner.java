package featurecat.lizzie.gui;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** 释放 Windows LAF 为菜单/按钮缓存的大量皮肤位图。 */
public final class UiMemoryCleaner {
  private UiMemoryCleaner() {}

  public static void flushWindowsSkinCache() {
    try {
      Class<?> xp = Class.forName("com.sun.java.swing.plaf.windows.XPStyle");
      Field field = xp.getDeclaredField("skinPainter");
      field.setAccessible(true);
      Object painter = field.get(null);
      if (painter == null) return;
      Method flush = painter.getClass().getDeclaredMethod("flush");
      flush.setAccessible(true);
      flush.invoke(painter);
    } catch (Throwable ignored) {
      // 非 Windows LAF 或模块未开放时忽略。
    }
  }
}
