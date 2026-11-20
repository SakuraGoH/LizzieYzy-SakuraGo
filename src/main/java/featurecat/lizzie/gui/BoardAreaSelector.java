package featurecat.lizzie.gui;

import featurecat.lizzie.Lizzie;
import featurecat.lizzie.analysis.fox.FoxDirectOverlay;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;
import javax.swing.JWindow;
import javax.swing.SwingUtilities;

/** 全屏框选一次野狐棋盘区域，保存为原棋盘覆盖层的位置。 */
public final class BoardAreaSelector extends JWindow {
  private final Consumer<Rectangle> callback;
  private Point start;
  private Point current;

  private BoardAreaSelector(Consumer<Rectangle> callback) {
    this.callback = callback;
    Rectangle screen = FoxDirectOverlay.virtualScreenBounds();
    setBounds(screen);
    setAlwaysOnTop(true);
    setBackground(new Color(0, 0, 0, 0));
    try {
      setOpacity(0.35f);
    } catch (Throwable ignored) {
    }
    MouseAdapter mouse =
        new MouseAdapter() {
          @Override
          public void mousePressed(MouseEvent e) {
            start = e.getLocationOnScreen();
            current = start;
            repaint();
          }

          @Override
          public void mouseDragged(MouseEvent e) {
            if (start == null) return;
            current = e.getLocationOnScreen();
            repaint();
          }

          @Override
          public void mouseReleased(MouseEvent e) {
            if (start == null) return;
            current = e.getLocationOnScreen();
            int x = Math.min(start.x, current.x);
            int y = Math.min(start.y, current.y);
            int w = Math.abs(start.x - current.x);
            int h = Math.abs(start.y - current.y);
            dispose();
            if (w >= 50 && h >= 50) callback.accept(new Rectangle(x, y, w, h));
          }
        };
    getContentPane().addMouseListener(mouse);
    getContentPane().addMouseMotionListener(mouse);
  }

  public static void open() {
    if (Lizzie.frame == null) return;
    SwingUtilities.invokeLater(
        () -> {
          BoardAreaSelector selector =
              new BoardAreaSelector(
                  rect -> {
                    Lizzie.config.foxDirectShowOnBoard = true;
                    Lizzie.config.uiConfig.put("fox-direct-show-on-board", true);
                    FoxDirectOverlay.setBoardRect(rect);
                  });
          selector.setVisible(true);
        });
  }

  @Override
  public void paint(Graphics g) {
    super.paint(g);
    if (start == null || current == null) return;
    Graphics2D g2 = (Graphics2D) g;
    int x = Math.min(start.x, current.x) - getX();
    int y = Math.min(start.y, current.y) - getY();
    int w = Math.abs(start.x - current.x);
    int h = Math.abs(start.y - current.y);
    g2.setColor(new Color(255, 60, 60));
    g2.drawRect(x, y, w, h);
    g2.setColor(new Color(255, 60, 60, 60));
    g2.fillRect(x, y, w, h);
  }
}
