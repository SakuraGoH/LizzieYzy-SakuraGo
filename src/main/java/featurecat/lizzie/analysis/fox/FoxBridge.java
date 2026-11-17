package featurecat.lizzie.analysis.fox;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 野狐命令分发 + GTP 桥接。
 *
 * <p>AI 回合只走 genmove；收到 KataGo 的 "play &lt;vertex&gt;" 后转成野狐 AFPLAY/AFSKIP/AFGIVEUP。 引擎的 info 行由
 * Leelaz 正常解析。
 */
public class FoxBridge {

  /** LizzieYzy 宿主接口，由 LizzieFoxHost 实现。 */
  public interface LizzieHost {
    boolean isEngineRunning();

    void sendGtp(String command);

    void onBoardUpdated();

    void onAiTurn(String color);

    void onGameEnd();

    void log(String msg);

    void onInputLockChanged(boolean locked);

    void onRuleChanged(int boardSize, double komi, List<String> handicap);

    void onReplayMoves(Map<Integer, String[]> foxMoves);

    void onFoxMove(int x, int y, String color);
  }

  private static final Pattern GTP_VERTEX = Pattern.compile("^([A-HJ-T])(\\d{1,2})$");
  private static final String LETTERS = "ABCDEFGHJKLMNOPQRST";

  private LizzieHost host;
  private final FoxClientServer fox;

  public int boardSize = 19;
  public double komi = 6.5;
  public String aiColor = "B";
  public Map<Integer, String[]> foxMoves = new LinkedHashMap<>();
  public int lastMoveNumber = 0;

  private volatile boolean waitingGenmoveResult = false;
  private String pendingScoreOpponent = "0";

  public FoxBridge(LizzieHost host, FoxClientServer fox) {
    this.host = host;
    this.fox = fox;
  }

  public void setHost(LizzieHost host) {
    this.host = host;
  }

  // ---------- 野狐命令分发 ----------
  public void handleFoxCommand(String command, String[] items) {
    if (host == null) return;
    switch (command) {
      case "FARULE":
        handleFarule(items);
        break;
      case "FASTATUS":
        handleFastatus(items);
        break;
      case "FAMOVE":
        handleFamove(items);
        break;
      case "FASKIP":
        handleFaskip(items);
        break;
      case "FATIMELEFT":
        handleFatimeleft(items);
        break;
      case "FARESULT":
        handleFaresult(items);
        break;
      case "FASCORE":
        handleFascore(items);
        break;
      default:
        break;
    }
  }

  private void handleFarule(String[] items) {
    if (items.length < 5) return;
    boardSize = Integer.parseInt(items[0]);
    String mainTime = items[1];
    String byoTime = items[2];
    String byoCount = items[3];
    komi = Integer.parseInt(items[4]) / 100.0;
    List<String> handicap = new ArrayList<>();
    for (int i = 5; i < items.length; i++) {
      try {
        String[] p = items[i].split("\\^");
        String v = xyToGtp(Integer.parseInt(p[1]), Integer.parseInt(p[2]));
        if (v != null) handicap.add(v);
      } catch (Exception ignored) {
      }
    }

    host.onRuleChanged(boardSize, komi, handicap);
    if (host.isEngineRunning()) {
      sendGtp("time_settings " + mainTime + " " + byoTime + " " + byoCount);
    }

    foxMoves.clear();
    lastMoveNumber = 0;
    waitingGenmoveResult = false;
    host.onInputLockChanged(true);
    host.log("规则: 棋盘" + boardSize + ", 贴目" + komi + ", 让子" + handicap);
  }

  private void handleFastatus(String[] items) {
    if (items.length == 0 || "0".equals(items[0])) {
      host.log("非对弈状态");
      return;
    }
    aiColor = items.length > 1 ? items[1] : "B";
    foxMoves.clear();
    lastMoveNumber = 0;
    for (int i = 2; i < items.length; i++) {
      try {
        String[] p = items[i].split("\\^");
        int n = Integer.parseInt(p[0]);
        String vertex = xyToGtp(Integer.parseInt(p[1]), Integer.parseInt(p[2]));
        String color = "B".equals(p[3]) ? "B" : "W";
        foxMoves.put(n, new String[] {color, vertex});
        lastMoveNumber = n;
      } catch (Exception e) {
        host.log("重放落子解析失败: " + items[i] + " " + e.getMessage());
      }
    }
    host.onReplayMoves(foxMoves);
    host.onBoardUpdated();
    host.log("重连重放完成，已同步到第 " + lastMoveNumber + " 手");
  }

  private void handleFamove(String[] items) {
    if (items.length == 0) return;
    boolean isAiTurn = "1".equals(items[0]);
    if (items.length > 1) aiColor = items[1];

    int parsedN = -1;
    String parsedVertex = null;
    String parsedColor = null;
    if (items.length > 2 && !"0^0^0^N".equals(items[2])) {
      try {
        String[] p = items[2].split("\\^");
        parsedN = Integer.parseInt(p[0]);
        parsedVertex = xyToGtp(Integer.parseInt(p[1]), Integer.parseInt(p[2]));
        parsedColor = "B".equals(p[3]) ? "B" : "W";
      } catch (Exception e) {
        host.log("落子解析失败: " + items[2] + " " + e.getMessage());
      }
    }

    if (parsedN > 0 && parsedVertex != null && parsedColor != null) {
      String[] existed = foxMoves.get(parsedN);
      boolean alreadyPlaced =
          existed != null && existed[0].equals(parsedColor) && existed[1].equals(parsedVertex);
      foxMoves.put(parsedN, new String[] {parsedColor, parsedVertex});
      lastMoveNumber = Math.max(lastMoveNumber, parsedN);

      if (alreadyPlaced) {
        host.log("野狐重复落子已跳过: " + parsedColor + " " + parsedVertex + "（第 " + parsedN + " 手）");
      } else {
        int[] xy = gtpToXy(parsedVertex);
        if (xy != null) host.onFoxMove(xy[0], xy[1], parsedColor);
        host.log("野狐确认落子: " + parsedColor + " " + parsedVertex + "（第 " + parsedN + " 手）");
      }
    }

    host.onBoardUpdated();
    if (isAiTurn) {
      host.onInputLockChanged(true);
      try {
        Thread.sleep(30);
      } catch (InterruptedException ignored) {
      }
      startThinking();
    }
  }

  private void handleFaskip(String[] items) {
    if (items.length == 0) return;
    boolean isAiTurn = "1".equals(items[0]);
    if (items.length > 1) aiColor = items[1];
    if (isAiTurn) startThinking();
  }

  private void handleFatimeleft(String[] items) {
    sendGtp("time_left " + aiColor + " 999999 0");
  }

  private void handleFaresult(String[] items) {
    host.log("对局结果: " + Arrays.toString(items));
    waitingGenmoveResult = false;
    foxMoves.clear();
    lastMoveNumber = 0;
    host.onInputLockChanged(false);
    host.onGameEnd();
  }

  private void handleFascore(String[] items) {
    pendingScoreOpponent = items.length > 0 ? items[0] : "0";
    sendGtp("final_score");
  }

  /** AI 回合：只走 genmove，等 "play <vertex>" 决定落子。 */
  private void startThinking() {
    waitingGenmoveResult = true;
    host.log("请求 genmove " + aiColor);
    host.onAiTurn(aiColor);
  }

  // ---------- GTP 输出处理 ----------
  public void onEngineOutput(String line) {
    if (line == null || host == null) return;
    String stripped = line.trim();
    if (stripped.isEmpty()) return;

    if (stripped.startsWith("?")) {
      host.log("引擎报错: " + stripped);
      return;
    }

    if (waitingGenmoveResult && stripped.startsWith("play")) {
      String[] params = stripped.split("\\s+");
      if (params.length >= 2) {
        waitingGenmoveResult = false;
        sendMoveToFox(params[1].trim());
        return;
      }
    }

    if (stripped.startsWith("=")) {
      String body = stripped.substring(1).trim();
      int sp = body.indexOf(' ');
      if (sp > 0 && body.substring(0, sp).chars().allMatch(Character::isDigit)) {
        body = body.substring(sp + 1).trim();
      }
      if (isFinalScoreResult(body)) sendScoreToFox(body);
    }
  }

  private boolean isFinalScoreResult(String body) {
    if (body.isEmpty()) return false;
    if (body.equals("0") || body.equalsIgnoreCase("draw")) return true;
    return body.length() >= 3 && "WBwb".indexOf(body.charAt(0)) >= 0 && body.charAt(1) == '+';
  }

  private void sendMoveToFox(String move) {
    if (host == null) return;
    move = move == null ? "" : move.trim();
    if (move.isEmpty()) {
      host.log("genmove 返回空，忽略");
      return;
    }
    if ("resign".equalsIgnoreCase(move)) {
      fox.send("AFGIVEUP", aiColor);
      host.log("发送认输: AFGIVEUP " + aiColor);
      return;
    }
    if ("pass".equalsIgnoreCase(move)) {
      fox.send("AFSKIP", aiColor);
      host.log("发送停一手: AFSKIP " + aiColor);
      lastMoveNumber++;
      foxMoves.put(lastMoveNumber, new String[] {aiColor, "pass"});
      return;
    }
    int[] xy = gtpToXy(move);
    if (xy == null) {
      host.log("无法解析落子: " + move);
      return;
    }
    lastMoveNumber++;
    String moveStr = lastMoveNumber + "^" + xy[0] + "^" + xy[1] + "^" + aiColor;
    fox.send("AFPLAY", aiColor, moveStr);
    host.log("发送落子: " + move + " -> AFPLAY " + aiColor + " " + moveStr);
    foxMoves.put(lastMoveNumber, new String[] {aiColor, xyToGtp(xy[0], xy[1])});
  }

  private void sendScoreToFox(String score) {
    score = score == null ? "" : score.trim();
    String result;
    if (score.equals("0") || "draw".equalsIgnoreCase(score)) {
      result = "0";
    } else if (score.length() >= 3
        && "WB".indexOf(Character.toUpperCase(score.charAt(0))) >= 0
        && score.charAt(1) == '+') {
      char color = Character.toUpperCase(score.charAt(0));
      String rest = score.substring(2).trim();
      if ("R".equalsIgnoreCase(rest) || "RESIGN".equalsIgnoreCase(rest)) {
        result = color + "+R";
      } else {
        try {
          result = color + "+" + (int) (Double.parseDouble(rest) * 100);
        } catch (NumberFormatException e) {
          result = score;
        }
      }
    } else {
      result = score;
    }
    fox.send("AFSCORE", pendingScoreOpponent, result);
    host.log("发送分数: AFSCORE " + pendingScoreOpponent + " " + result);
  }

  // ---------- 坐标 ----------
  public String xyToGtp(int x, int y) {
    if (x >= 0 && x < LETTERS.length() && y >= 0 && y < 19) {
      return "" + LETTERS.charAt(x) + (19 - y);
    }
    return "pass";
  }

  public int[] gtpToXy(String vertex) {
    if (vertex == null) return null;
    vertex = vertex.toUpperCase();
    if ("PASS".equals(vertex)) return null;
    Matcher m = GTP_VERTEX.matcher(vertex);
    if (!m.matches()) return null;
    int x = LETTERS.indexOf(m.group(1).charAt(0));
    int y = 19 - Integer.parseInt(m.group(2));
    if (x < 0 || y < 0 || y >= 19) return null;
    return new int[] {x, y};
  }

  private void sendGtp(String cmd) {
    if (host == null) return;
    if (!host.isEngineRunning()) {
      host.log("引擎未运行，跳过 GTP: " + cmd);
      return;
    }
    host.sendGtp(cmd);
    host.log("GTP发送: " + cmd);
  }
}
