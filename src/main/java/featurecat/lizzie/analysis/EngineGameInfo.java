package featurecat.lizzie.analysis;

import featurecat.lizzie.gui.LizzieFrame;
import featurecat.lizzie.gui.SgfWinLossList;
import featurecat.lizzie.rules.Board;
import featurecat.lizzie.rules.Movelist;
import java.util.ArrayList;

public class EngineGameInfo {
  public ArrayList<SgfWinLossList> engineGameSgfWinLoss;
  public boolean isGenmove;
  public boolean isContinueGame;
  public ArrayList<Movelist> continueGameList;
  public int blackEngineIndex; // 当前黑白方
  public int whiteEngineIndex;
  public int firstEngineIndex; // 第一第二引擎
  public int secondEngineIndex;
  public boolean isBatchGame; // 是否批量对局
  public int batchNumber;
  public int batchNumberCurrent;
  public boolean isExchange; // 是否交换黑白

  public int firstEngineWinAsBlack;
  public int firstEngineWinAsWhite;
  public long firstEngineTotlePlayouts;
  public int firstEngineTotleTime;

  public int secondEngineWinAsBlack;
  public int secondEngineWinAsWhite;
  public long secondEngineTotlePlayouts;
  public int secondEngineTotleTime;

  public int timeBlack;
  public int timeWhite;
  public int playoutsBlack;
  public int playoutsWhite;
  public int firstPlayoutsBlack;
  public int firstPlayoutsWhite;

  public int timeFirstEngine;
  public int timeSecondEngine;
  public int playoutsFirstEngine;
  public int playoutsSecondEngine;
  public int firstPlayoutsFirstEngine;
  public int firstPlayoutsSecondEngine;

  public String batchGameName;
  public String SF;

  public int doublePassGame;
  public int maxMoveGame;
  public String settingAll, settingFirst, settingSecond;
  public String resultOther, resultFirst, resultSecond;

  /** 双pass时裁判引擎给出的原始结果(如 "B+3.5")，null 表示未判定。 */
  public String refereeScore;

  /** 裁判判定: 1=黑胜 2=白胜 3=和棋 0=未判定。 */
  public int refereeWinner;

  public int blackMinMove;
  public int blackResignMoveCounts;
  public Double blackResignWinrate;

  public int whiteMinMove;
  public int whiteResignMoveCounts;
  public Double whiteResignWinrate;

  private int maxGameMoves;

  public int getFirstEngineWins() {
    return firstEngineWinAsBlack + firstEngineWinAsWhite;
  }

  public int getSecondEngineWins() {
    return secondEngineWinAsBlack + secondEngineWinAsWhite;
  }

  public void exChangeBlackWhite() {

    int temp = blackEngineIndex;
    blackEngineIndex = whiteEngineIndex;
    whiteEngineIndex = temp;
    LizzieFrame.toolbar.enginePkBlack.setEnabled(true);
    LizzieFrame.toolbar.enginePkWhite.setEnabled(true);
    LizzieFrame.toolbar.enginePkBlack.setSelectedIndex(blackEngineIndex);
    LizzieFrame.toolbar.enginePkWhite.setSelectedIndex(whiteEngineIndex);
    LizzieFrame.toolbar.enginePkBlack.setEnabled(false);
    LizzieFrame.toolbar.enginePkWhite.setEnabled(false);
    if (firstEngineIndex == blackEngineIndex)
      LizzieFrame.toolbar.lblenginePkResult.setText(
          getFirstEngineWins() + ":" + getSecondEngineWins());
    else
      LizzieFrame.toolbar.lblenginePkResult.setText(
          getSecondEngineWins() + ":" + getFirstEngineWins());

    String temp1 = LizzieFrame.toolbar.txtenginePkFirstPlayputs.getText();
    LizzieFrame.toolbar.txtenginePkFirstPlayputs.setText(
        LizzieFrame.toolbar.txtenginePkFirstPlayputsWhite.getText());
    LizzieFrame.toolbar.txtenginePkFirstPlayputsWhite.setText(temp1);
    temp1 = LizzieFrame.toolbar.txtenginePkPlayputs.getText();
    LizzieFrame.toolbar.txtenginePkPlayputs.setText(
        LizzieFrame.toolbar.txtenginePkPlayputsWhite.getText());
    LizzieFrame.toolbar.txtenginePkPlayputsWhite.setText(temp1);
    temp1 = LizzieFrame.toolbar.txtenginePkTime.getText();
    LizzieFrame.toolbar.txtenginePkTime.setText(LizzieFrame.toolbar.txtenginePkTimeWhite.getText());
    LizzieFrame.toolbar.txtenginePkTimeWhite.setText(temp1);

    int temp2 = timeFirstEngine;
    timeFirstEngine = timeSecondEngine;
    timeSecondEngine = temp2;

    temp2 = playoutsBlack;
    playoutsBlack = playoutsWhite;
    playoutsWhite = temp2;

    temp2 = firstPlayoutsBlack;
    firstPlayoutsBlack = firstPlayoutsWhite;
    firstPlayoutsWhite = temp2;

    temp2 = blackMinMove;
    blackMinMove = whiteMinMove;
    whiteMinMove = temp2;

    temp2 = blackResignMoveCounts;
    blackResignMoveCounts = whiteResignMoveCounts;
    whiteResignMoveCounts = temp2;

    Double temp3 = blackResignWinrate;
    blackResignWinrate = whiteResignWinrate;
    whiteResignWinrate = temp3;
  }

  public boolean isBlackEngine(int currentEngineIndex) {
    if (currentEngineIndex == this.blackEngineIndex) return true;
    else return false;
  }

  public boolean isFirstEnginePlayBlack() {
    return firstEngineIndex == blackEngineIndex;
  }

  public int getMaxGameMoves() {
    if (maxGameMoves > 0) return maxGameMoves;
    else return Board.boardWidth * Board.boardHeight * 2;
  }

  public void setMaxGameMoves(int maxMoves) {
    maxGameMoves = maxMoves;
  }

  /**
   * 为多场对战中的单个对局复制一份设置。统计字段全部清零，由每局单独维护。
   *
   * @param black 当前对局黑方引擎序号
   * @param white 当前对局白方引擎序号
   * @param first 第一引擎序号(统计口径)
   * @param second 第二引擎序号(统计口径)
   */
  public EngineGameInfo copyForSession(int black, int white, int first, int second) {
    EngineGameInfo info = new EngineGameInfo();
    info.isGenmove = isGenmove;
    info.isContinueGame = isContinueGame;
    info.isBatchGame = true;
    info.batchNumber = batchNumber;
    info.isExchange = isExchange;
    info.timeBlack = timeBlack;
    info.timeWhite = timeWhite;
    info.playoutsBlack = playoutsBlack;
    info.playoutsWhite = playoutsWhite;
    info.firstPlayoutsBlack = firstPlayoutsBlack;
    info.firstPlayoutsWhite = firstPlayoutsWhite;
    info.timeFirstEngine = timeFirstEngine;
    info.timeSecondEngine = timeSecondEngine;
    info.playoutsFirstEngine = playoutsFirstEngine;
    info.playoutsSecondEngine = playoutsSecondEngine;
    info.firstPlayoutsFirstEngine = firstPlayoutsFirstEngine;
    info.firstPlayoutsSecondEngine = firstPlayoutsSecondEngine;
    info.blackEngineIndex = black;
    info.whiteEngineIndex = white;
    info.firstEngineIndex = first;
    info.secondEngineIndex = second;
    info.blackMinMove = blackMinMove;
    info.blackResignMoveCounts = blackResignMoveCounts;
    info.blackResignWinrate = blackResignWinrate;
    info.whiteMinMove = whiteMinMove;
    info.whiteResignMoveCounts = whiteResignMoveCounts;
    info.whiteResignWinrate = whiteResignWinrate;
    info.batchGameName = batchGameName;
    info.SF = SF;
    info.settingFirst = settingFirst;
    info.settingSecond = settingSecond;
    info.settingAll = settingAll;
    info.setMaxGameMoves(maxGameMoves);
    return info;
  }
}
