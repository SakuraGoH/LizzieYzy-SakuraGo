package featurecat.lizzie.gui;

import static java.awt.event.KeyEvent.*;

import featurecat.lizzie.Lizzie;
import java.awt.event.*;

public class InputIndependentSubboard implements KeyListener, MouseListener, MouseWheelListener {

  @Override
  public void mouseClicked(MouseEvent e) {}

  @Override
  public void mousePressed(MouseEvent e) {
    Lizzie.frame.processIndependentPressOnSub(e);
  }

  @Override
  public void mouseWheelMoved(MouseWheelEvent e) {

    Lizzie.frame.processIndependentSubboardMouseWheelMoved(e);
  }

  @Override
  public void mouseReleased(MouseEvent e) {}

  @Override
  public void mouseEntered(MouseEvent e) {
    Lizzie.frame.processIndependentSubboardMouseEntered();
  }

  @Override
  public void mouseExited(MouseEvent e) {
    Lizzie.frame.processIndependentSubboardMouseExited();
  }

  @Override
  public void keyPressed(KeyEvent e) {
    switch (e.getKeyCode()) {
      case VK_SPACE:
        Lizzie.frame.togglePonderMannul();
        break;
      case VK_Q:
        if (e.isAltDown()) {
          Lizzie.frame.toggleIndependentMainBoard();
        }
        break;
      case VK_W:
        if (e.isAltDown()) {
          Lizzie.frame.toggleIndependentSubBoard();
        }
        break;
    }
  }

  @Override
  public void keyReleased(KeyEvent arg0) {}

  @Override
  public void keyTyped(KeyEvent arg0) {}
}
