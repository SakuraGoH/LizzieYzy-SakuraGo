package featurecat.lizzie.analysis;

import featurecat.lizzie.Lizzie;
import featurecat.lizzie.gui.LizzieFrame;
import featurecat.lizzie.rules.Board;
import featurecat.lizzie.rules.Movelist;
import featurecat.lizzie.rules.SGFParser;
import featurecat.lizzie.rules.Stone;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 多场对战中单独的一盘对局。
 *
 * <p>每盘对局拥有自己的棋盘和两个引擎进程，在独立线程中推进，互不阻塞。 引擎命令行的读线程把 genmove 结果交给本类处理，从而绕开全局单局的界面状态。
 */
public class EngineGameSession implements Runnable {
  private static final int PENDING_MOVE = 1;
  private static final int PENDING_PASS = 2;
  private static final int PENDING_RESIGN = 3;

  private final EngineGameArena arena;
  private final int lane;
  private final int blackSourceIndex;
  private final int whiteSourceIndex;

  private Board board;
  private EngineGameInfo info;
  private Leelaz engineA;
  private Leelaz engineB;
  private Leelaz blackEngine;
  private Leelaz whiteEngine;
  private Leelaz expectedEngine;

  private volatile boolean stopRequested;
  private volatile boolean gameRunning;
  private boolean enginesReady;
  private boolean lastMoveWasPass;
  private boolean engineFailure;
  private int gameNo;
  /** 每局递增；旧对局迟到的 genmove 结果带有旧代号，会被丢弃。 */
  private long generation;

  private Pending pending;

  private static final class Pending {
    final int type;
    final Leelaz engine;
    final int[] coords;
    final long generation;

    Pending(int type, Leelaz engine, int[] coords, long generation) {
      this.type = type;
      this.engine = engine;
      this.coords = coords;
      this.generation = generation;
    }
  }

  public EngineGameSession(
      EngineGameArena arena, int lane, int blackSourceIndex, int whiteSourceIndex) {
    this.arena = arena;
    this.lane = lane;
    this.blackSourceIndex = blackSourceIndex;
    this.whiteSourceIndex = whiteSourceIndex;
    this.info =
        arena
            .getTemplate()
            .copyForSession(blackSourceIndex, whiteSourceIndex, blackSourceIndex, whiteSourceIndex);
    this.board = new Board(true);
  }

  public void startThread() {
    Thread thread = new Thread(this, "engine-game-lane-" + lane);
    thread.setDaemon(true);
    thread.start();
  }

  @Override
  public void run() {
    try {
      if (!startEngines()) {
        arena.onSessionError(
            this, Lizzie.resourceBundle.getString("EnginePkConfig.arenaEngineStartFailed"));
        return;
      }
      enginesReady = true;
      beginNextGame();
    } catch (Exception e) {
      e.printStackTrace();
      arena.onSessionError(
          this, Lizzie.resourceBundle.getString("EnginePkConfig.arenaEngineStartFailed"));
    }
  }

  private boolean startEngines() {
    try {
      engineA = createEngine(blackSourceIndex);
      engineB = createEngine(whiteSourceIndex);
      if (engineA == null || engineB == null) return false;
      if (!waitLoaded(engineA) || !waitLoaded(engineB)) return false;
      applyRules(engineA);
      applyRules(engineB);
      sendCommand(engineA, "clear_board");
      sendCommand(engineB, "clear_board");
      return true;
    } catch (Exception e) {
      e.printStackTrace();
      return false;
    }
  }

  private Leelaz createEngine(int sourceIndex) throws Exception {
    Leelaz source = Lizzie.engineManager.engineList.get(sourceIndex);
    if (source.engineCommand == null || source.engineCommand.trim().isEmpty()) return null;
    Leelaz engine = new Leelaz(source.engineCommand);
    engine.headless = true;
    engine.gameSession = this;
    engine.gameTag = "赛道" + (lane + 1);
    engine.width = Board.boardWidth;
    engine.height = Board.boardHeight;
    engine.oriWidth = Board.boardWidth;
    engine.oriHeight = Board.boardHeight;
    engine.komi = (float) arena.getKomi();
    engine.orikomi = (float) arena.getKomi();
    engine.initialCommand = source.initialCommand;
    engine.useJavaSSH = source.useJavaSSH;
    engine.ip = source.ip;
    engine.port = source.port;
    engine.useKeyGen = source.useKeyGen;
    engine.keyGenPath = source.keyGenPath;
    engine.userName = source.userName;
    engine.password = source.password;
    engine.startEngine(sourceIndex);
    return engine;
  }

  private boolean waitLoaded(Leelaz engine) {
    long deadline = System.currentTimeMillis() + 180_000L;
    while (!engine.isLoaded()) {
      if (stopRequested || engine.isProcessDead() || engine.isDownWithError) return false;
      if (System.currentTimeMillis() > deadline) return false;
      try {
        Thread.sleep(100);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return false;
      }
    }
    return true;
  }

  private void applyRules(Leelaz engine) {
    if (!engine.isKatago) return;
    String rules = Lizzie.config.kataRules;
    if (rules == null || rules.trim().isEmpty()) return;
    sendCommand(engine, "kata-set-rules " + rules);
  }

  private void sendCommand(Leelaz engine, String command) {
    if (engine != null) engine.sendCommand(command);
  }

  /** 取下一场并开始。由首局线程或上一局结束回调触发。 */
  public synchronized void beginNextGame() {
    if (stopRequested || !enginesReady || engineFailure) {
      if (!stopRequested) arena.onLaneFinished(this);
      return;
    }
    int no = arena.claimGameNumber();
    if (no < 0) {
      arena.onLaneFinished(this);
      return;
    }
    try {
      startGame(no);
    } catch (Exception e) {
      e.printStackTrace();
      arena.onSessionError(
          this, Lizzie.resourceBundle.getString("EnginePkConfig.arenaEngineStartFailed"));
    }
  }

  private void startGame(int no) {
    gameNo = no;
    boolean exchange = info.isExchange && (no - 1) % 2 == 1;
    blackEngine = exchange ? engineB : engineA;
    whiteEngine = exchange ? engineA : engineB;
    // 等上一局的 stop/genmove 应答彻底处理完，避免旧着法落到新棋盘上。
    waitGtpIdle(blackEngine);
    waitGtpIdle(whiteEngine);
    generation++;
    blackEngine.gameTag = "第" + no + "局";
    whiteEngine.gameTag = "第" + no + "局";
    info.blackEngineIndex = exchange ? whiteSourceIndex : blackSourceIndex;
    info.whiteEngineIndex = exchange ? blackSourceIndex : whiteSourceIndex;

    board = new Board(true);
    board.getHistory().getGameInfo().setKomiNoMenu(arena.getKomi());
    board.getHistory().getGameInfo().setHandicap(arena.getHandicap());
    board.getHistory().getGameInfo().setPlayerBlack(blackEngine.oriEnginename);
    board.getHistory().getGameInfo().setPlayerWhite(whiteEngine.oriEnginename);
    lastMoveWasPass = false;
    pending = null;

    sendCommand(blackEngine, "clear_board");
    sendCommand(whiteEngine, "clear_board");
    sendCommand(blackEngine, "boardsize " + Board.boardWidth + " " + Board.boardHeight);
    sendCommand(whiteEngine, "boardsize " + Board.boardWidth + " " + Board.boardHeight);
    sendCommand(blackEngine, "komi " + arena.getKomi());
    sendCommand(whiteEngine, "komi " + arena.getKomi());
    applyRules(blackEngine);
    applyRules(whiteEngine);
    configureEngine(blackEngine, true);
    configureEngine(whiteEngine, false);

    ArrayList<Movelist> startList = arena.nextStartList();
    if (startList != null) replayStartList(startList);
    else {
      // 让子棋：开局黑方连续摆子后由白方先走。
      if (arena.getHandicap() >= 2) placeHandicap(arena.getHandicap());
    }

    gameRunning = true;
    arena.onBoardChanged(this);
    boolean blackToPlay = board.getHistory().isBlacksTurn();
    expectedEngine = blackToPlay ? blackEngine : whiteEngine;
    expectedEngine.genmoveForPk(blackToPlay ? "B" : "W", generation);
    refreshIfActive();
  }

  private void waitGtpIdle(Leelaz engine) {
    long deadline = System.currentTimeMillis() + 3000;
    while (engine != null && !engine.isGtpIdle() && System.currentTimeMillis() < deadline) {
      try {
        Thread.sleep(10);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return;
      }
    }
  }

  private void configureEngine(Leelaz engine, boolean isBlack) {
    if (Lizzie.config.pkAdvanceTimeSettings) {
      sendCommand(
          engine, isBlack ? Lizzie.config.advanceBlackTimeTxt : Lizzie.config.advanceWhiteTimeTxt);
    } else {
      int time = isBlack ? info.timeBlack : info.timeWhite;
      if (time > 0) sendCommand(engine, "time_settings 0 " + time + " 1");
    }
    int playouts = isBlack ? info.playoutsBlack : info.playoutsWhite;
    if (playouts > 0) {
      if (engine.isKatago) sendCommand(engine, "kata-set-param maxVisits " + playouts);
      else sendCommand(engine, "lz-setoption name Visits value " + playouts);
    }
  }

  private void replayStartList(ArrayList<Movelist> startList) {
    for (int i = startList.size() - 1; i >= 0; i--) {
      Movelist move = startList.get(i);
      Stone color = move.isblack ? Stone.BLACK : Stone.WHITE;
      if (move.ispass) {
        board.getHistory().pass(color);
        sendCommand(blackEngine, "play " + (move.isblack ? "B" : "W") + " pass");
        sendCommand(whiteEngine, "play " + (move.isblack ? "B" : "W") + " pass");
      } else {
        board.getHistory().place(move.x, move.y, color);
        String coord = Board.convertCoordinatesToName(move.x, move.y);
        sendCommand(blackEngine, "play " + (move.isblack ? "B " : "W ") + coord);
        sendCommand(whiteEngine, "play " + (move.isblack ? "B " : "W ") + coord);
      }
    }
  }

  private void placeHandicap(int handicap) {
    int[][] stones;
    switch (handicap) {
      case 2:
        stones = new int[][] {{3, 15}, {15, 3}};
        break;
      case 3:
        stones = new int[][] {{3, 3}, {15, 3}, {3, 15}};
        break;
      case 4:
        stones = new int[][] {{3, 3}, {3, 15}, {15, 3}, {15, 15}};
        break;
      case 5:
        stones = new int[][] {{3, 3}, {3, 15}, {15, 3}, {15, 15}, {9, 9}};
        break;
      case 6:
        stones = new int[][] {{3, 3}, {3, 15}, {15, 3}, {15, 15}, {3, 9}, {15, 9}};
        break;
      case 7:
        stones = new int[][] {{3, 3}, {3, 15}, {15, 3}, {15, 15}, {3, 9}, {15, 9}, {9, 9}};
        break;
      case 8:
        stones = new int[][] {{3, 3}, {3, 15}, {15, 3}, {15, 15}, {3, 9}, {15, 9}, {9, 3}, {9, 15}};
        break;
      case 9:
        stones =
            new int[][] {
              {3, 3}, {3, 15}, {15, 3}, {15, 15}, {9, 3}, {9, 15}, {3, 9}, {15, 9}, {9, 9}
            };
        break;
      default:
        stones = new int[0][];
    }
    if (Board.boardWidth != 19 || Board.boardHeight != 19) return;
    for (int[] stone : stones) {
      board.getHistory().place(stone[0], stone[1], Stone.BLACK);
      String coord = Board.convertCoordinatesToName(stone[0], stone[1]);
      sendCommand(blackEngine, "play B " + coord);
      sendCommand(whiteEngine, "play B " + coord);
    }
    board.getHistory().getData().blackToPlay = false;
  }

  // ---- 引擎输出回调(由 Leelaz 的读线程调用) ----

  public void onMove(Leelaz engine, int[] coords, long responseGeneration) {
    synchronized (this) {
      if (!gameRunning || engine != expectedEngine || responseGeneration != generation) return;
      if (arena.isPauseRequested()) {
        pending = new Pending(PENDING_MOVE, engine, coords, responseGeneration);
        return;
      }
      applyMove(engine, coords);
    }
  }

  public void onPass(Leelaz engine, long responseGeneration) {
    synchronized (this) {
      if (!gameRunning || engine != expectedEngine || responseGeneration != generation) return;
      if (arena.isPauseRequested()) {
        pending = new Pending(PENDING_PASS, engine, null, responseGeneration);
        return;
      }
      applyPass(engine);
    }
  }

  public void onResign(Leelaz engine, long responseGeneration) {
    synchronized (this) {
      if (!gameRunning || engine != expectedEngine || responseGeneration != generation) return;
      if (arena.isPauseRequested()) {
        pending = new Pending(PENDING_RESIGN, engine, null, responseGeneration);
        return;
      }
      finishGame(engine == blackEngine ? 2 : 1, "resign", null);
    }
  }

  public void onEngineDead(Leelaz engine) {
    synchronized (this) {
      if (!gameRunning) return;
      engineFailure = true;
      finishGame(engine == blackEngine ? 2 : 1, "engineDead", null);
    }
  }

  /** 某引擎输出 info 时更新本局棋盘数据；后台赛道也要记录，切换回来时曲线才连续。 */
  public void onEngineInfo(Leelaz engine, long responseGeneration) {
    synchronized (this) {
      if (!gameRunning
          || board == null
          || engine != expectedEngine
          || responseGeneration != generation) return;
      List<MoveData> bestMoves = engine.getBestMoves();
      if (bestMoves == null || bestMoves.isEmpty()) return;
      featurecat.lizzie.rules.BoardData data = board.getHistory().getCurrentHistoryNode().getData();
      data.bestMoves = new ArrayList<>(bestMoves);
      data.isKataData = engine.isKatago;
      data.engineName = engine.currentEnginename;
      data.winrate = bestMoves.get(0).winrate;
      data.setPlayouts(MoveData.getPlayouts(bestMoves));
    }
    refreshIfActive();
  }

  private void applyMove(Leelaz engine, int[] coords) {
    if (!gameRunning) return;
    boolean isBlack = engine == blackEngine;
    if (isBlack != board.getHistory().isBlacksTurn()) return;
    if (board.getHistory().getMoveNumber() >= info.getMaxGameMoves()) {
      finishGame(0, "maxMoves", null);
      return;
    }
    int before = board.getHistory().getMoveNumber();
    board.getHistory().place(coords[0], coords[1], isBlack ? Stone.BLACK : Stone.WHITE);
    if (board.getHistory().getMoveNumber() != before + 1) {
      // 引擎返回了非法着法，判对手胜，避免死循环。
      finishGame(isBlack ? 2 : 1, "illegal", null);
      return;
    }
    lastMoveWasPass = false;
    Leelaz other = isBlack ? whiteEngine : blackEngine;
    sendCommand(
        other,
        "play " + (isBlack ? "B " : "W ") + Board.convertCoordinatesToName(coords[0], coords[1]));
    expectedEngine = other;
    other.genmoveForPk(isBlack ? "W" : "B", generation);
    refreshIfActive();
  }

  private void applyPass(Leelaz engine) {
    if (!gameRunning) return;
    boolean isBlack = engine == blackEngine;
    if (isBlack != board.getHistory().isBlacksTurn()) return;
    if (board.getHistory().getMoveNumber() >= info.getMaxGameMoves()) {
      finishGame(0, "maxMoves", null);
      return;
    }
    board.getHistory().pass(isBlack ? Stone.BLACK : Stone.WHITE);
    if (lastMoveWasPass) {
      judgeDoublePass();
      return;
    }
    lastMoveWasPass = true;
    Leelaz other = isBlack ? whiteEngine : blackEngine;
    sendCommand(other, "play " + (isBlack ? "B pass" : "W pass"));
    expectedEngine = other;
    other.genmoveForPk(isBlack ? "W" : "B", generation);
    refreshIfActive();
  }

  private void judgeDoublePass() {
    String score = null;
    if (Lizzie.config.refereeEngineCommand != null
        && !Lizzie.config.refereeEngineCommand.trim().isEmpty()) {
      score = RefereeEngine.finalScore(board, arena.getKomi());
    }
    int winner = RefereeEngine.parseWinner(score);
    finishGame(winner, "doublepass", score);
  }

  private void finishGame(int winnerColor, String reason, String score) {
    if (!gameRunning) return;
    gameRunning = false;
    pending = null;
    sendCommand(blackEngine, "stop");
    sendCommand(whiteEngine, "stop");
    String result = buildResult(winnerColor, reason, score);
    board.getHistory().getGameInfo().setResultNoMenu(result);
    if (info.isBatchGame || LizzieFrame.toolbar.AutosavePk) saveGameFile(result, reason);
    arena.onGameFinished(this, winnerColor, reason, score);
  }

  private String buildResult(int winnerColor, String reason, String score) {
    if ("doublepass".equals(reason)) {
      if (score != null && !score.trim().isEmpty()) return score.trim();
      return "0";
    }
    if ("maxMoves".equals(reason)) return "Void";
    if ("aborted".equals(reason)) return "Void";
    if (winnerColor == 1) return "B+R";
    if (winnerColor == 2) return "W+R";
    return "0";
  }

  private void saveGameFile(String result, String reason) {
    String dir =
        new File("").getAbsolutePath()
            + File.separator
            + "EngineGames"
            + File.separator
            + arena.getBatchGameName();
    try {
      Files.createDirectories(new File(dir).toPath());
      String black = sanitize(blackEngine.oriEnginename);
      String white = sanitize(whiteEngine.oriEnginename);
      String suffix;
      if ("doublepass".equals(reason)) {
        int winner = RefereeEngine.parseWinner(result);
        if (winner != 0 && result != null && !result.trim().isEmpty())
          suffix = "_" + sanitize(result);
        else suffix = "_" + Lizzie.resourceBundle.getString("EngineManager.doublePassFileName");
      } else if ("maxMoves".equals(reason))
        suffix = "_" + Lizzie.resourceBundle.getString("EngineManager.outOfMoveFileName");
      else if ("aborted".equals(reason))
        suffix = "_" + Lizzie.resourceBundle.getString("EnginePkConfig.abortedFileName");
      else suffix = "_" + (result.startsWith("B") ? "B" : "W") + "Win";
      String name =
          gameNo
              + "_"
              + black
              + "_VS_"
              + white
              + suffix
              + "_"
              + new SimpleDateFormat("yyyyMMddHHmmss").format(new Date())
              + ".sgf";
      writeSgf(new File(dir, name));
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private String sanitize(String name) {
    if (name == null) return "";
    return name.replaceAll("[/\\\\:*?|\"<>]", ".");
  }

  private void writeSgf(File file) throws IOException {
    StringBuilder builder = new StringBuilder("(;GM[1]FF[4]CA[UTF-8]AP[LizzieYzy]");
    builder.append("SZ[").append(Board.boardWidth);
    if (Board.boardWidth != Board.boardHeight) builder.append(":").append(Board.boardHeight);
    builder.append("]");
    double komi = arena.getKomi();
    builder
        .append("KM[")
        .append(komi == Math.rint(komi) ? String.valueOf((int) komi) : komi)
        .append("]");
    if (arena.getHandicap() >= 2) builder.append("HA[").append(arena.getHandicap()).append("]");
    builder.append("PB[").append(blackEngine.oriEnginename).append("]");
    builder.append("PW[").append(whiteEngine.oriEnginename).append("]");
    builder.append("DT[").append(new SimpleDateFormat("yyyy-MM-dd").format(new Date())).append("]");
    builder.append("RE[").append(board.getHistory().getGameInfo().getResult()).append("]");
    ArrayList<Movelist> moves = board.getMoveList();
    for (int i = moves.size() - 1; i >= 0; i--) {
      Movelist move = moves.get(i);
      String color = move.isblack ? "B" : "W";
      String coord =
          move.ispass ? SGFParser.passPos() : SGFParser.asCoord(new int[] {move.x, move.y});
      builder.append(";").append(color).append("[").append(coord).append("]");
    }
    builder.append(")");
    try (Writer writer =
        new OutputStreamWriter(Files.newOutputStream(file.toPath()), StandardCharsets.UTF_8)) {
      writer.write(builder.toString());
    }
  }

  public void resumePending() {
    Pending action;
    synchronized (this) {
      action = pending;
      pending = null;
      if (!gameRunning || action == null || action.engine != expectedEngine) return;
      if (action.generation != generation) return;
    }
    synchronized (this) {
      if (action.type == PENDING_MOVE) applyMove(action.engine, action.coords);
      else if (action.type == PENDING_PASS) applyPass(action.engine);
      else if (action.type == PENDING_RESIGN)
        finishGame(action.engine == blackEngine ? 2 : 1, "resign", null);
    }
  }

  public void checkAlive() {
    if (!gameRunning || !enginesReady) return;
    if (blackEngine != null && blackEngine.isProcessDead()) onEngineDead(blackEngine);
    else if (whiteEngine != null && whiteEngine.isProcessDead()) onEngineDead(whiteEngine);
  }

  public synchronized void stopSession() {
    stopRequested = true;
    gameRunning = false;
    pending = null;
    stopEngines();
  }

  /** 只结束当前这一局，其他赛道的对局继续。 */
  public synchronized void abortGame() {
    if (!gameRunning) return;
    finishGame(0, "aborted", null);
  }

  public void stopEngines() {
    Leelaz a = engineA;
    Leelaz b = engineB;
    engineA = null;
    engineB = null;
    quitEngine(a);
    quitEngine(b);
  }

  private void quitEngine(Leelaz engine) {
    if (engine == null) return;
    try {
      engine.forceQuit();
    } catch (Exception ignored) {
    }
  }

  private void refreshIfActive() {
    if (arena.isActive(this)) javax.swing.SwingUtilities.invokeLater(() -> Lizzie.frame.refresh(1));
  }

  // ---- 供 Arena 读取 ----

  public Board getBoard() {
    return board;
  }

  public EngineGameInfo getInfo() {
    return info;
  }

  public int getBlackEngineIndex() {
    return info.blackEngineIndex;
  }

  public int getWhiteEngineIndex() {
    return info.whiteEngineIndex;
  }

  public int getFirstEngineIndex() {
    return info.firstEngineIndex;
  }

  public String getBlackEngineName() {
    return blackEngine != null ? blackEngine.oriEnginename : "";
  }

  public String getWhiteEngineName() {
    return whiteEngine != null ? whiteEngine.oriEnginename : "";
  }

  public boolean isGameRunning() {
    return gameRunning;
  }
}
