package featurecat.lizzie;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * Java 8/11 在未指定 -Xms 时会按物理内存预留较大的初始堆，空置的 Lizzie 也会占用数百 MB 提交内存。
 *
 * <p>这里在首次启动时用较小的初始堆和合理上限重新启动自身；用户显式设置过 -Xmx 或已带标记时不再重启。
 */
final class MemoryBootstrap {
  private static final String MARKER = "LIZZIE_MEMORY_TUNED";
  private static final long LARGE_HEAP = 4L * 1024 * 1024 * 1024;

  private MemoryBootstrap() {}

  static void tuneAndRestartIfNeeded(String[] args) {
    if ("1".equals(System.getenv(MARKER))) return;
    if ("1".equals(System.getProperty("lizzie.memory.keep"))) return;
    long max = Runtime.getRuntime().maxMemory();
    if (max <= LARGE_HEAP) return;
    if (!"64".equals(System.getProperty("sun.arch.data.model", "64"))) return;

    try {
      String javaHome = System.getProperty("java.home");
      String exe =
          javaHome
              + File.separator
              + "bin"
              + File.separator
              + (System.getProperty("os.name", "").toLowerCase().contains("win")
                  ? "java.exe"
                  : "java");
      File java = new File(exe);
      if (!java.exists()) return;
      URL location = MemoryBootstrap.class.getProtectionDomain().getCodeSource().getLocation();
      if (location == null) return;
      File code = new File(location.toURI());
      List<String> command = new ArrayList<>();
      command.add(java.getAbsolutePath());
      command.add("-Xms16m");
      command.add("-Xmx2g");
      if (code.isFile()) {
        command.add("-jar");
        command.add(code.getAbsolutePath());
      } else {
        command.add("-cp");
        command.add(System.getProperty("java.class.path"));
        command.add("featurecat.lizzie.Lizzie");
      }
      if (args != null) {
        for (String arg : args) command.add(arg);
      }
      ProcessBuilder builder = new ProcessBuilder(command);
      builder.directory(new File(System.getProperty("user.dir")));
      builder.environment().put(MARKER, "1");
      builder.redirectErrorStream(true);
      builder.start();
      System.exit(0);
    } catch (Throwable ignored) {
      // 重新启动失败时继续使用当前 JVM，不影响功能。
    }
  }
}
