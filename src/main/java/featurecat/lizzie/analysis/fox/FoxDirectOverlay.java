package featurecat.lizzie.analysis.fox;

import featurecat.lizzie.Lizzie;
import featurecat.lizzie.analysis.ReadBoard;
import featurecat.lizzie.gui.FloatBoard;
import featurecat.lizzie.util.WindowFocusHelper;

/** 野狐协议直连的原棋盘选点覆盖层，位置直接复用棋盘同步工具的 inboard 坐标。 */
public final class FoxDirectOverlay {
  private FoxDirectOverlay() {}

  public static void sync() {
    if (!Lizzie.config.foxDirectShowOnBoard
        || !FoxDirectController.getInstance().isRunning()
        || Lizzie.frame == null) {
      hide();
      return;
    }
    ReadBoard readBoard = Lizzie.frame.readBoard;
    if (readBoard == null || !readBoard.hasBoardScreenPosition()) {
      hide();
      return;
    }
    float scale = readBoard.getBoardScreenScale();
    if (scale <= 0) scale = 1f;
    int x = Math.round(readBoard.getBoardScreenX() * scale);
    int y = Math.round(readBoard.getBoardScreenY() * scale);
    int width = Math.round(readBoard.getBoardScreenWidth() * scale);
    int height = Math.round(readBoard.getBoardScreenHeight() * scale);
    if (width <= 0 || height <= 0) {
      hide();
      return;
    }

    FloatBoard overlay = Lizzie.frame.foxDirectOverlay;
    if (overlay == null || !overlay.isDisplayable()) {
      overlay = new FloatBoard(x, y, width, height, 0, false);
      overlay.setFocusableWindowState(false);
      overlay.setAutoRequestFocus(false);
      WindowFocusHelper.setClickThrough(overlay);
      Lizzie.frame.foxDirectOverlay = overlay;
    } else {
      overlay.setPos(x, y, width, height, 0);
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
}
