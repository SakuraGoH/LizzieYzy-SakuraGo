package featurecat.lizzie.analysis;

import featurecat.lizzie.Lizzie;
import featurecat.lizzie.rules.Board;
import featurecat.lizzie.rules.Movelist;
import featurecat.lizzie.util.Utils;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * 裁判引擎：双 pass 结束时用 GTP 的 final_score 命令判定胜负。
 *
 * <p>裁判引擎独立于对局双方，可以单独指定命令行(含权重)。它按需启动并复用； 对局结束时把整盘棋重放到裁判引擎，再读取 final_score。
 */
public final class RefereeEngine {
  private static final int READ_TIMEOUT_SECONDS = 20;

  private static Process process;
  private static BufferedWriter writer;
  private static BlockingQueue<String> lines;

  private RefereeEngine() {}

  /** 返回 GTP final_score 的原始结果(如 "B+3.5")，失败返回 null。 */
  public static synchronized String finalScore(Board board, double komi) {
    String command = Lizzie.config.refereeEngineCommand;
    if (command == null || command.trim().isEmpty()) return null;
    if (board == null) return null;
    try {
      ensureStarted(command.trim());
      prepareGame(board, komi);
      return query("final_score");
    } catch (Exception e) {
      e.printStackTrace();
      stop();
      return null;
    }
  }

  public static synchronized void shutdown() {
    stop();
  }

  private static void ensureStarted(String command) throws IOException {
    if (process != null && process.isAlive()) return;
    stop();
    List<String> args = Utils.splitCommand(command);
    if (args.isEmpty()) throw new IOException("empty referee engine command");
    ProcessBuilder builder = new ProcessBuilder(args);
    builder.redirectErrorStream(true);
    process = builder.start();
    writer =
        new BufferedWriter(
            new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
    lines = new ArrayBlockingQueue<>(1024);
    BlockingQueue<String> queue = lines;
    Thread reader =
        new Thread(
            () -> {
              try (BufferedReader in =
                  new BufferedReader(
                      new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = in.readLine()) != null) {
                  if (!queue.offer(line)) {
                    queue.poll();
                    queue.offer(line);
                  }
                }
              } catch (IOException ignored) {
              }
            },
            "referee-engine-reader");
    reader.setDaemon(true);
    reader.start();
  }

  private static void prepareGame(Board board, double komi) throws IOException {
    query("boardsize " + Board.boardWidth + " " + Board.boardHeight);
    query("komi " + komi);
    String rules = Lizzie.config.kataRules;
    if (rules != null && !rules.trim().isEmpty()) {
      // 只对支持该命令的 KataGo 生效；失败时忽略返回值继续判定。
      write("kata-set-rules " + rules);
    }
    query("clear_board");
    ArrayList<Movelist> moves = board.getMoveList();
    for (int i = moves.size() - 1; i >= 0; i--) {
      Movelist move = moves.get(i);
      String color = move.isblack ? "B" : "W";
      String coord = move.ispass ? "pass" : Board.convertCoordinatesToName(move.x, move.y);
      query("play " + color + " " + coord);
    }
  }

  private static String query(String command) throws IOException {
    drain();
    write(command);
    long deadline = System.currentTimeMillis() + READ_TIMEOUT_SECONDS * 1000L;
    while (System.currentTimeMillis() < deadline) {
      String line;
      try {
        line = lines.poll(200, TimeUnit.MILLISECONDS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return null;
      }
      if (line == null) continue;
      line = line.trim();
      if (line.startsWith("=")) return line.substring(1).trim();
      if (line.startsWith("?")) return null;
    }
    return null;
  }

  private static void write(String command) throws IOException {
    writer.write(command);
    writer.write("\n");
    writer.flush();
  }

  private static void drain() {
    if (lines != null) lines.clear();
  }

  private static void stop() {
    try {
      if (process != null) {
        if (process.isAlive()) {
          try {
            writer.write("quit\n");
            writer.flush();
          } catch (Exception ignored) {
          }
          if (!process.waitFor(2, TimeUnit.SECONDS)) process.destroyForcibly();
        }
      }
    } catch (Exception ignored) {
    } finally {
      process = null;
      writer = null;
      lines = null;
    }
  }

  /**
   * 解析 final_score 的胜负：1=黑胜，2=白胜，0=和棋/未知。
   *
   * <p>支持 "B+3.5"、"W+R"、"0"、"Draw" 等常见格式。
   */
  public static int parseWinner(String finalScore) {
    if (finalScore == null) return 0;
    String s = finalScore.trim();
    if (s.isEmpty()) return 0;
    char c = Character.toUpperCase(s.charAt(0));
    if (c == 'B') return 1;
    if (c == 'W') return 2;
    if (s.startsWith("0") || s.toLowerCase().contains("draw") || s.toLowerCase().contains("jigo"))
      return 0;
    return 0;
  }
}
