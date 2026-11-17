package featurecat.lizzie.analysis;

import featurecat.lizzie.Lizzie;
import featurecat.lizzie.gui.LizzieFrame;
import featurecat.lizzie.gui.Menu;
import featurecat.lizzie.rules.Board;
import featurecat.lizzie.rules.Movelist;
import featurecat.lizzie.util.Utils;
import java.io.File;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import javax.swing.SwingUtilities;

/**
 * 多场引擎对战管理器。
 *
 * <p>每场对局拥有独立的棋盘与两个引擎进程，互不干扰地同时进行。 屏幕下端的翻页键用于在不同对局之间切换查看；总场数由批量对局数决定， 同时进行的场数不能超过总场数。
 */
public final class EngineGameArena {
  private static EngineGameArena instance;

  public static EngineGameArena getInstance() {
    if (instance == null) instance = new EngineGameArena();
    return instance;
  }

  public static boolean isRunning() {
    return instance != null && instance.running;
  }

  private final List<EngineGameSession> sessions =
      new java.util.concurrent.CopyOnWriteArrayList<>();
  private volatile boolean running;
  private volatile boolean paused;
  private volatile int activeIndex;

  private EngineGameInfo template;
  private int blackIndex;
  private int whiteIndex;
  private int totalGames;
  private int nextGameNumber = 1;
  private volatile int finishedGames;
  private int activeLanes;
  private double komi = 7.5;
  private int handicap;
  private String batchGameName;
  private ArrayList<Movelist> continueStartList;
  private int sgfStartIndex;
  private Board mainBoard;
  private EngineGameInfo mainInfo;
  private Leelaz mainLeelaz;

  private volatile int firstWins;
  private volatile int secondWins;
  private int draws;
  private int doublePassGames;
  private int refereeGames;
  private int maxMoveGames;
  private int abortedGames;
  private boolean resultWritten;

  private EngineGameArena() {}

  public boolean isPaused() {
    return paused;
  }

  public String getBatchGameName() {
    return batchGameName;
  }

  public double getKomi() {
    return komi;
  }

  public int getHandicap() {
    return handicap;
  }

  public EngineGameInfo getTemplate() {
    return template;
  }

  /**
   * 启动多场对战。必须在 EDT 中调用。
   *
   * @param blackIndex 黑方引擎序号
   * @param whiteIndex 白方引擎序号
   * @param parallel 同时进行的场数
   * @param totalGames 总场数
   */
  public void start(
      int blackIndex,
      int whiteIndex,
      int parallel,
      int totalGames,
      EngineGameInfo template,
      Board currentBoard) {
    this.template = template;
    this.blackIndex = blackIndex;
    this.whiteIndex = whiteIndex;
    this.totalGames = Math.max(1, totalGames);
    int lanes = Math.max(1, Math.min(parallel, this.totalGames));
    this.komi = currentBoard.getHistory().getGameInfo().getKomi();
    this.handicap = currentBoard.getHistory().getGameInfo().getHandicap();
    this.mainBoard = currentBoard;
    this.mainInfo = template;
    this.mainLeelaz = Lizzie.leelaz;
    this.continueStartList =
        template.isContinueGame ? new ArrayList<>(currentBoard.getMoveList()) : null;
    this.batchGameName =
        template.batchGameName != null && !template.batchGameName.isEmpty()
            ? template.batchGameName
            : "EngineGames_" + new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
    this.batchGameName = batchGameName.replaceAll("[/\\\\:*?|]", ".");
    this.batchGameName = batchGameName.replaceAll("[\"<>]", "'");
    this.nextGameNumber = 1;
    this.finishedGames = 0;
    this.firstWins = 0;
    this.secondWins = 0;
    this.draws = 0;
    this.doublePassGames = 0;
    this.refereeGames = 0;
    this.maxMoveGames = 0;
    this.abortedGames = 0;
    this.resultWritten = false;
    this.sgfStartIndex = 0;
    this.paused = false;
    this.running = true;
    this.activeIndex = 0;
    this.activeLanes = lanes;
    sessions.clear();

    for (int i = 0; i < lanes; i++) {
      sessions.add(new EngineGameSession(this, i, blackIndex, whiteIndex));
    }
    EngineManager.isEngineGame = true;
    EngineManager.isPreEngineGame = false;
    Lizzie.frame.setResult("");
    if (Lizzie.leelaz != null) {
      Lizzie.leelaz.notPondering();
      Lizzie.leelaz.nameCmd();
    }
    switchTo(0);
    LizzieFrame.toolbar.setEngineGamePagingVisible(true);
    updatePagingLabel();
    for (EngineGameSession session : sessions) session.startThread();
  }

  public boolean isActive(EngineGameSession session) {
    return session != null && sessions.indexOf(session) == activeIndex;
  }

  public boolean isPauseRequested() {
    return paused;
  }

  public synchronized int claimGameNumber() {
    if (!running || nextGameNumber > totalGames) return -1;
    return nextGameNumber++;
  }

  /** 为下一盘取得 SGF 开局；顺序/随机由配置决定。 */
  public ArrayList<Movelist> nextStartList() {
    if (continueStartList != null) return continueStartList;
    if (!Lizzie.config.chkEngineSgfStart) return null;
    if (Lizzie.frame.enginePKSgfString == null || Lizzie.frame.enginePKSgfString.isEmpty())
      return null;
    int size = Lizzie.frame.enginePKSgfString.size();
    int index;
    synchronized (this) {
      if (Lizzie.config.engineSgfStartRandom) index = (int) (Math.random() * size);
      else {
        index = sgfStartIndex % size;
        sgfStartIndex++;
      }
    }
    LizzieFrame.toolbar.currentEnginePkSgfNum = index;
    return Lizzie.frame.enginePKSgfString.get(index);
  }

  public void onGameFinished(
      EngineGameSession session, int winnerColor, String reason, String score) {
    synchronized (this) {
      finishedGames++;
      if ("doublepass".equals(reason)) {
        doublePassGames++;
        if (score != null && !score.isEmpty()) refereeGames++;
      } else if ("maxMoves".equals(reason)) {
        maxMoveGames++;
      } else if ("aborted".equals(reason)) {
        abortedGames++;
      }
      if (winnerColor == 0) draws++;
      else {
        int winnerIndex =
            winnerColor == 1 ? session.getBlackEngineIndex() : session.getWhiteEngineIndex();
        boolean first = winnerIndex == session.getFirstEngineIndex();
        boolean asBlack = winnerColor == 1;
        if (first) {
          firstWins++;
          if (asBlack) template.firstEngineWinAsBlack++;
          else template.firstEngineWinAsWhite++;
        } else {
          secondWins++;
          if (asBlack) template.secondEngineWinAsBlack++;
          else template.secondEngineWinAsWhite++;
        }
      }
      template.batchNumberCurrent = finishedGames;
    }
    updateToolbarResult();
    updatePagingLabel();
    session.beginNextGame();
  }

  /** 某条赛道已无后续对局。 */
  public void onLaneFinished(EngineGameSession session) {
    session.stopEngines();
    synchronized (this) {
      if (activeLanes > 0) activeLanes--;
      if (activeLanes > 0) return;
    }
    finishArena(false);
  }

  public void onSessionError(EngineGameSession session, String message) {
    if (session != null) session.stopSession();
    // 任一赛道启动失败都不再保留“对战中”状态，直接结束整场多局对战。
    abortAll(message);
  }

  /** 引擎启动/运行失败时立即结束全部对局并恢复主界面。 */
  public void abortAll(String message) {
    if (!running) return;
    running = false;
    paused = false;
    EngineManager.isEngineGame = false;
    EngineManager.isPreEngineGame = false;
    writeResultFile();
    for (EngineGameSession session : sessions) session.stopSession();
    restoreMainUi();
    sessions.clear();
    if (message != null) showMessage(message);
  }

  private void showMessage(String message) {
    Runnable show = () -> Utils.showMsgNoModal(message);
    if (SwingUtilities.isEventDispatchThread()) show.run();
    else SwingUtilities.invokeLater(show);
  }

  /** 多盘对战结束时写一份与单局一致的统计结果，便于事后查看。 */
  private void writeResultFile() {
    if (resultWritten || finishedGames <= 0 || template == null) return;
    resultWritten = true;
    try {
      String firstEngine = getEngineName(template.firstEngineIndex);
      String secondEngine = getEngineName(template.secondEngineIndex);
      int decisive = firstWins + secondWins;
      double winrate = decisive == 0 ? 0 : firstWins * 100.0 / decisive;
      StringBuilder text = new StringBuilder();
      text.append(Lizzie.resourceBundle.getString("EngineGameInfo.totalGameResults"))
          .append(finishedGames)
          .append(Lizzie.resourceBundle.getString("EngineGameInfo.gameScore"))
          .append(firstWins)
          .append(":")
          .append(secondWins)
          .append(Lizzie.resourceBundle.getString("EngineGameInfo.gameWinrate"))
          .append(String.format(java.util.Locale.ENGLISH, "%.2f", winrate))
          .append("%\n\n");

      text.append(Lizzie.resourceBundle.getString("EngineGameInfo.engine1"))
          .append("(")
          .append(firstEngine)
          .append("):\n")
          .append(Lizzie.resourceBundle.getString("EngineGameInfo.allWins"))
          .append(": ")
          .append(template.getFirstEngineWins())
          .append(Lizzie.resourceBundle.getString("EngineGameInfo.sgfStartBlackWin"))
          .append(": ")
          .append(template.firstEngineWinAsBlack)
          .append(Lizzie.resourceBundle.getString("EngineGameInfo.sgfStartWhiteWin"))
          .append(": ")
          .append(template.firstEngineWinAsWhite)
          .append("\n\n");

      text.append(Lizzie.resourceBundle.getString("EngineGameInfo.engine2"))
          .append("(")
          .append(secondEngine)
          .append("):\n")
          .append(Lizzie.resourceBundle.getString("EngineGameInfo.allWins"))
          .append(": ")
          .append(template.getSecondEngineWins())
          .append(Lizzie.resourceBundle.getString("EngineGameInfo.sgfStartBlackWin"))
          .append(": ")
          .append(template.secondEngineWinAsBlack)
          .append(Lizzie.resourceBundle.getString("EngineGameInfo.sgfStartWhiteWin"))
          .append(": ")
          .append(template.secondEngineWinAsWhite)
          .append("\n\n");

      text.append(Lizzie.resourceBundle.getString("EngineGameInfo.doublePassGame"))
          .append(doublePassGames)
          .append(" ")
          .append(Lizzie.resourceBundle.getString("EngineGameInfo.outOfMoveGame"))
          .append(maxMoveGames)
          .append(" ")
          .append(Lizzie.resourceBundle.getString("EnginePkConfig.refereeCount"))
          .append(refereeGames)
          .append(" ")
          .append(Lizzie.resourceBundle.getString("EnginePkConfig.drawCount"))
          .append(draws)
          .append(" ")
          .append(Lizzie.resourceBundle.getString("EnginePkConfig.abortedFileName"))
          .append(abortedGames);
      File dir = new File("EngineGames", batchGameName);
      Files.createDirectories(dir.toPath());
      String stamp = template.SF == null || template.SF.isEmpty() ? batchGameName : template.SF;
      File file = new File(dir, Lizzie.resourceBundle.getString("Leelaz.result") + stamp + ".txt");
      Files.write(file.toPath(), text.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public synchronized void updateToolbarResult() {
    if (LizzieFrame.toolbar == null) return;
    Runnable r = () -> LizzieFrame.toolbar.lblenginePkResult.setText(firstWins + ":" + secondWins);
    if (SwingUtilities.isEventDispatchThread()) r.run();
    else SwingUtilities.invokeLater(r);
  }

  public void updatePagingLabel() {
    Runnable r =
        () ->
            LizzieFrame.toolbar.updateEngineGamePaging(
                activeIndex + 1, sessions.size(), finishedGames, totalGames);
    if (SwingUtilities.isEventDispatchThread()) r.run();
    else SwingUtilities.invokeLater(r);
  }

  public void next() {
    if (sessions.isEmpty()) return;
    switchTo((activeIndex + 1) % sessions.size());
  }

  public void prev() {
    if (sessions.isEmpty()) return;
    switchTo((activeIndex - 1 + sessions.size()) % sessions.size());
  }

  public void switchTo(int index) {
    if (!running || sessions.isEmpty()) return;
    if (!SwingUtilities.isEventDispatchThread()) {
      SwingUtilities.invokeLater(() -> switchTo(index));
      return;
    }
    activeIndex = Math.max(0, Math.min(index, sessions.size() - 1));
    EngineGameSession session = sessions.get(activeIndex);
    Board board = session.getBoard();
    if (board == null) return;
    Lizzie.board = board;
    EngineManager.engineGameInfo = session.getInfo();
    board.isPkBoard = true;
    Lizzie.frame.refreshWinrateForBoardSwitch();
    Lizzie.frame.setPlayers(session.getWhiteEngineName(), session.getBlackEngineName());
    Lizzie.frame.resetMovelistFrameandAnalysisFrame();
    Lizzie.frame.updateTitle();
    updateToolbarResult();
    updatePagingLabel();
    Lizzie.frame.refresh();
  }

  public void onBoardChanged(EngineGameSession session) {
    if (isActive(session)) switchTo(activeIndex);
  }

  /** 只结束当前查看的对局，其他赛道的对局不受影响。 */
  public void stopActiveGame() {
    if (!running || sessions.isEmpty()) return;
    EngineGameSession session = sessions.get(activeIndex);
    if (session != null) session.abortGame();
  }

  public void togglePause() {
    paused = !paused;
    if (!paused) for (EngineGameSession session : sessions) session.resumePending();
  }

  /** 供 EngineManager 的存活检查定时器调用。 */
  public void checkAlive() {
    for (EngineGameSession session : sessions) session.checkAlive();
  }

  /** 停止全部对局并恢复主界面。 */
  public void stop(boolean manual) {
    if (!running && sessions.isEmpty()) return;
    running = false;
    paused = false;
    writeResultFile();
    for (EngineGameSession session : sessions) session.stopSession();
    restoreMainUi();
    showSummary(manual);
    sessions.clear();
  }

  private void finishArena(boolean manual) {
    if (!running) return;
    running = false;
    paused = false;
    writeResultFile();
    for (EngineGameSession session : sessions) session.stopSession();
    restoreMainUi();
    showSummary(manual);
    sessions.clear();
  }

  private void restoreMainUi() {
    Runnable restore =
        () -> {
          Lizzie.board = mainBoard != null ? mainBoard : Lizzie.board;
          EngineManager.engineGameInfo = mainInfo != null ? mainInfo : EngineManager.engineGameInfo;
          if (mainLeelaz != null) {
            Lizzie.leelaz = mainLeelaz;
            Menu.engineMenu.setText(
                Lizzie.resourceBundle.getString("EngineManager.engine")
                    + (mainLeelaz.currentEngineN() + 1)
                    + ": "
                    + mainLeelaz.oriEnginename);
            Menu.engineMenu.setEnabled(true);
          }
          if (Lizzie.board != null) Lizzie.board.isPkBoard = false;
          EngineManager.isEngineGame = false;
          EngineManager.isPreEngineGame = false;
          restoreToolbar();
        };
    if (SwingUtilities.isEventDispatchThread()) restore.run();
    else SwingUtilities.invokeLater(restore);
  }

  private void restoreToolbar() {
    LizzieFrame.toolbar.setEngineGamePagingVisible(false);
    LizzieFrame.toolbar.enableDisabelForEngineGame(true);
    LizzieFrame.menu.toggleDoubleMenuGameStatus();
    Lizzie.frame.addInput(true);
    Lizzie.frame.updateTitle();
    Lizzie.frame.resetMovelistFrameandAnalysisFrame();
    Lizzie.frame.refresh();
    if (Lizzie.leelaz != null && Lizzie.leelaz.isLoaded()) Lizzie.leelaz.ponder();
  }

  private void showSummary(boolean manual) {
    if (!SwingUtilities.isEventDispatchThread()) {
      SwingUtilities.invokeLater(() -> showSummary(manual));
      return;
    }
    String passInfo =
        doublePassGames > 0
            ? " "
                + Lizzie.resourceBundle.getString("EngineGameInfo.doublePassGame")
                + doublePassGames
            : "";
    if (refereeGames > 0)
      passInfo +=
          " " + Lizzie.resourceBundle.getString("EnginePkConfig.refereeCount") + refereeGames;
    String drawInfo =
        draws > 0 ? " " + Lizzie.resourceBundle.getString("EnginePkConfig.drawCount") + draws : "";
    String message =
        (manual
                ? Lizzie.resourceBundle.getString("EnginePkConfig.arenaStopped")
                : Lizzie.resourceBundle.getString("EnginePkConfig.arenaFinished"))
            + " "
            + getEngineName(blackIndex)
            + "   "
            + firstWins
            + ":"
            + secondWins
            + "   "
            + getEngineName(whiteIndex)
            + passInfo
            + drawInfo;
    Utils.showMsgNoModal(message);
  }

  private String getEngineName(int index) {
    try {
      return Lizzie.engineManager.engineList.get(index).oriEnginename;
    } catch (Exception e) {
      return "";
    }
  }
}
