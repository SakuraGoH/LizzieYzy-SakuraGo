package featurecat.lizzie.gui;

import static java.awt.event.KeyEvent.*;

import featurecat.lizzie.Lizzie;
import java.awt.event.*;

public class InputSubboard implements KeyListener, MouseListener, MouseWheelListener {

  @Override
  public void mouseClicked(MouseEvent e) {}

  @Override
  public void mousePressed(MouseEvent e) {
    Lizzie.frame.processPressOnSub(e);
  }

  //  @Override
  //  public void mouseWheelMoved(MouseWheelEvent e) {
  //    Lizzie.frame.processSubboardMouseWheelMoved(e);
  //  }

  private long wheelWhen;

  @Override
  public void mouseWheelMoved(MouseWheelEvent e) {
    // if (isinsertmode) {
    // return;
    // }
    //    if (Lizzie.frame.processCommentMouseWheelMoved(e)) {
    //      return;
    //    }
    if (Lizzie.frame.processSubboardMouseWheelMoved(e)) {
      return;
    }

    if (e.getWhen() - wheelWhen > 0) {
      wheelWhen = e.getWhen();
      if (e.getWheelRotation() > 0) {
        if (LizzieFrame.boardRenderer.isShowingBranch()) {
          Lizzie.frame.doBranch(1);
          Lizzie.frame.refresh();
        } else {
          if (LizzieFrame.boardRenderer.incrementDisplayedBranchLength(1)) {
            Lizzie.frame.refresh();
          }
        }
      } else if (e.getWheelRotation() < 0) {
        if (LizzieFrame.boardRenderer.isShowingBranch()) {
          Lizzie.frame.doBranch(-1);
          Lizzie.frame.refresh();
        }
      }
    }
  }

  @Override
  public void mouseReleased(MouseEvent e) {}

  @Override
  public void mouseEntered(MouseEvent e) {}

  @Override
  public void mouseExited(MouseEvent e) {
    Lizzie.frame.onMouseExited();
  }

  @Override
  public void keyPressed(KeyEvent e) {
    switch (e.getKeyCode()) {
      case VK_R:
        if (e.isAltDown()) Lizzie.engineManager.stopEngineGame(-1, true);
        break;
      case VK_T:
        if (e.isAltDown()) LizzieFrame.toolbar.btnEnginePkStop.doClick();
        break;
    }
  }

  @Override
  public void keyReleased(KeyEvent arg0) {}

  @Override
  public void keyTyped(KeyEvent arg0) {}
}
