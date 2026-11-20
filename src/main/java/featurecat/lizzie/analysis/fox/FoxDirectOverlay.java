package featurecat.lizzie.analysis.fox;

import featurecat.lizzie.Lizzie;
import featurecat.lizzie.gui.FloatBoard;
import featurecat.lizzie.util.WindowFocusHelper;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;

/** 野狐协议直连的原棋盘选点覆盖层。 */
public final class FoxDirectOverlay {
  private FoxDirectOverlay() {}

  public static void sync() {
    boolean enabled =
        Lizzie.config.foxDirectShowOnBoard
            && FoxDirectController.getInstance().isRunning()
            && Lizzie.config.foxDirectBoardWidth > 0
            && Lizzie.config.foxDirectBoardHeight > 0;
    if (!enabled) {
      hide();
      return;
    }
    if (Lizzie.frame == null) return;
    FloatBoard overlay = Lizzie.frame.foxDirectOverlay;
    if (overlay == null || !overlay.isDisplayable()) {
      overlay =
          new FloatBoard(
              Lizzie.config.foxDirectBoardX,
              Lizzie.config.foxDirectBoardY,
              Lizzie.config.foxDirectBoardWidth,
              Lizzie.config.foxDirectBoardHeight,
              0,
              false);
      overlay.setFocusableWindowState(false);
      overlay.setAutoRequestFocus(false);
      WindowFocusHelper.setClickThrough(overlay);
      Lizzie.frame.foxDirectOverlay = overlay;
    } else {
      overlay.setPos(
          Lizzie.config.foxDirectBoardX,
          Lizzie.config.foxDirectBoardY,
          Lizzie.config.foxDirectBoardWidth,
          Lizzie.config.foxDirectBoardHeight,
          0);
      overlay.setVisible(true);
    }
  }

  public static void hide() {
    if (Lizzie.frame != null && Lizzie.frame.foxDirectOverlay != null) {
      try {
        Lizzie.frame.foxDirectOverlay.dispose();
      } catch (Throwable ignored) {
      }
      Lizzie.frame.foxDirectOverlay = null;
    }
  }

  public static void setBoardRect(Rectangle rect) {
    if (rect == null || rect.width < 50 || rect.height < 50) return;
    Lizzie.config.foxDirectBoardX = rect.x;
    Lizzie.config.foxDirectBoardY = rect.y;
    Lizzie.config.foxDirectBoardWidth = rect.width;
    Lizzie.config.foxDirectBoardHeight = rect.height;
    Lizzie.config.uiConfig.put("fox-direct-board-x", rect.x);
    Lizzie.config.uiConfig.put("fox-direct-board-y", rect.y);
    Lizzie.config.uiConfig.put("fox-direct-board-width", rect.width);
    Lizzie.config.uiConfig.put("fox-direct-board-height", rect.height);
    sync();
  }

  public static Rectangle virtualScreenBounds() {
    Rectangle bounds = null;
    for (java.awt.GraphicsDevice device :
        GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
      Rectangle b = device.getDefaultConfiguration().getBounds();
      bounds = bounds == null ? b : bounds.union(b);
    }
    return bounds == null ? new Rectangle(0, 0, 1024, 768) : bounds;
  }
}
