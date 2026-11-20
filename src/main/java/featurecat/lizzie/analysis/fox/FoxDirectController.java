package featurecat.lizzie.analysis.fox;

import featurecat.lizzie.Lizzie;
import featurecat.lizzie.analysis.Leelaz;

/**
 * 野狐协议直连的生命周期管理器：负责监听端口、FoxBridge 和 UI 宿主之间的装配。
 *
 * <p>把原先散落在 LizzieFrame 里的连接/停止逻辑集中到一处，界面只负责选择引擎和窗口交互。
 */
public final class FoxDirectController {
  private static final FoxDirectController INSTANCE = new FoxDirectController();

  public static FoxDirectController getInstance() {
    return INSTANCE;
  }

  private FoxClientServer server;
  private FoxBridge bridge;
  private Leelaz engine;
  private int port;
  private volatile boolean inputLocked;

  private FoxDirectController() {}

  public boolean isRunning() {
    return server != null && server.isListening() && bridge != null;
  }

  public boolean isInputLocked() {
    return inputLocked;
  }

  public void setInputLocked(boolean locked) {
    inputLocked = locked;
  }

  public int getPort() {
    return port;
  }

  public FoxBridge getBridge() {
    return bridge;
  }

  public Leelaz getEngine() {
    return engine;
  }

  /**
   * 启动监听。
   *
   * @return true 表示启动成功；端口占用等原因失败时返回 false。
   */
  public boolean start(Leelaz engine, int port) {
    if (isRunning()) return false;
    this.engine = engine;
    this.port = port;
    inputLocked = false;

    FoxClientServer foxServer =
        new FoxClientServer(
            new FoxClientServer.Listener() {
              @Override
              public void onCommand(String command, String[] items) {
                FoxBridge b = bridge;
                if (b != null) b.handleFoxCommand(command, items);
              }

              @Override
              public void onLog(String msg) {
                log(msg);
              }

              @Override
              public void onReconnect() {
                if (Lizzie.board != null) Lizzie.board.clear(false);
                if (Lizzie.frame != null) Lizzie.frame.refresh();
              }
            });

    FoxBridge newBridge = new FoxBridge(null, foxServer);
    LizzieFoxHost newHost = new LizzieFoxHost(newBridge);
    newBridge.setHost(newHost);

    this.server = foxServer;
    this.bridge = newBridge;
    if (!foxServer.start(port)) {
      foxServer.stop();
      this.server = null;
      this.bridge = null;
      this.engine = null;
      return false;
    }

    Lizzie.config.foxDirectPort = port;
    Lizzie.config.uiConfig.put("fox-direct-port", port);
    log(
        "协议直连已启动，端口 "
            + port
            + "，对弈引擎: "
            + (engine != null ? engine.oriEnginename : "主引擎")
            + "，请在野狐 AI 设置中填 127.0.0.1:"
            + port);
    return true;
  }

  public void stop() {
    if (server == null && bridge == null) return;
    if (server != null) {
      try {
        server.stop();
      } catch (Exception ignored) {
      }
      server = null;
    }
    bridge = null;
    engine = null;
    inputLocked = false;
    log("协议直连已停止");
  }

  public void setPort(int port) {
    if (port <= 0 || port > 65535) return;
    this.port = port;
    Lizzie.config.foxDirectPort = port;
    Lizzie.config.uiConfig.put("fox-direct-port", port);
  }

  private void log(String msg) {
    if (Lizzie.gtpConsole != null) Lizzie.gtpConsole.addLine("[Fox] " + msg + "\n");
    else System.out.println("[Fox] " + msg);
  }
}
