package featurecat.lizzie.gui;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

/** 释放 Windows LAF 为菜单/按钮缓存的大量皮肤位图。 */
public final class UiMemoryCleaner {
  private UiMemoryCleaner() {}

  public static void flushWindowsSkinCache() {
    try {
      // CachedPainter 的静态 cacheMap 会为不同尺寸的按钮/菜单项各保留一份皮肤位图。
      Class<?> cachedPainter = Class.forName("sun.swing.CachedPainter");
      Field cacheField = cachedPainter.getDeclaredField("cacheMap");
      cacheField.setAccessible(true);
      Object cache = cacheField.get(null);
      if (cache instanceof Map) {
        Map<?, ?> map = (Map<?, ?>) cache;
        for (Object value : map.values()) {
          try {
            Method setMax = value.getClass().getDeclaredMethod("setMaxCount", int.class);
            setMax.setAccessible(true);
            setMax.invoke(value, 32);
          } catch (Throwable ignored) {
          }
          try {
            Method flush = value.getClass().getMethod("flush");
            flush.setAccessible(true);
            flush.invoke(value);
          } catch (Throwable ignored) {
          }
        }
      }
    } catch (Throwable ignored) {
      // 模块未开放时忽略。
    }
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
