package featurecat.lizzie.gui;

import featurecat.lizzie.Lizzie;
import featurecat.lizzie.util.Utils;
import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.swing.JDialog;
import javax.swing.JScrollPane;
import javax.swing.SwingWorker;

/** 帮助菜单中的设备信息检测：CPU、内存、GPU/显存和 Java 环境。 */
public class DeviceInfoDialog extends JDialog {
  private final JFontTextArea textArea = new JFontTextArea();

  public DeviceInfoDialog(Window owner) {
    super(owner, Lizzie.resourceBundle.getString("DeviceInfoDialog.title"), ModalityType.MODELESS);
    setLayout(new BorderLayout(0, 4));
    textArea.setEditable(false);
    textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
    textArea.setMargin(new Insets(8, 8, 8, 8));
    textArea.setText(Lizzie.resourceBundle.getString("DeviceInfoDialog.loading"));
    add(new JScrollPane(textArea), BorderLayout.CENTER);

    JFontButton copy = new JFontButton(Lizzie.resourceBundle.getString("DeviceInfoDialog.copy"));
    JFontButton close = new JFontButton(Lizzie.resourceBundle.getString("DeviceInfoDialog.close"));
    copy.setMargin(new Insets(0, 8, 0, 8));
    close.setMargin(new Insets(0, 8, 0, 8));
    copy.addActionListener(
        (ActionEvent e) -> {
          Toolkit.getDefaultToolkit()
              .getSystemClipboard()
              .setContents(new StringSelection(textArea.getText()), null);
          Utils.showMsgNoModal(Lizzie.resourceBundle.getString("DeviceInfoDialog.copied"));
        });
    close.addActionListener(e -> dispose());
    JPanelWithButtons buttons = new JPanelWithButtons(copy, close);
    add(buttons, BorderLayout.SOUTH);
    setSize(760, 560);
    setLocationRelativeTo(owner);
    new SwingWorker<String, Void>() {
      @Override
      protected String doInBackground() {
        return collectDeviceInfo();
      }

      @Override
      protected void done() {
        try {
          textArea.setText(get());
          textArea.setCaretPosition(0);
        } catch (Exception e) {
          textArea.setText(e.toString());
        }
      }
    }.execute();
  }

  /** 简单的按钮栏，避免额外引入布局工具类。 */
  private static final class JPanelWithButtons extends javax.swing.JPanel {
    JPanelWithButtons(javax.swing.JComponent... buttons) {
      setLayout(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 6, 4));
      for (javax.swing.JComponent button : buttons) add(button);
    }
  }

  private static String collectDeviceInfo() {
    StringBuilder out = new StringBuilder();
    out.append("OS        : ")
        .append(System.getProperty("os.name"))
        .append(' ')
        .append(System.getProperty("os.version"))
        .append(" (")
        .append(System.getProperty("os.arch"))
        .append(")\n");
    out.append("Java      : ")
        .append(System.getProperty("java.version"))
        .append(" / ")
        .append(System.getProperty("java.vendor"))
        .append('\n');
    out.append("CPU       : ").append(cpuName()).append('\n');
    out.append("CPU cores : ").append(Runtime.getRuntime().availableProcessors()).append('\n');
    out.append("Memory    : ").append(formatBytes(physicalMemory())).append(" physical\n");
    out.append("JVM max   : ").append(formatBytes(Runtime.getRuntime().maxMemory())).append('\n');
    out.append('\n');
    List<String> gpus = nvidiaGpus();
    if (gpus.isEmpty()) gpus = windowsGpus();
    if (gpus.isEmpty()) {
      out.append("GPU       : ")
          .append(Lizzie.resourceBundle.getString("DeviceInfoDialog.gpuNone"))
          .append('\n');
    } else {
      for (int i = 0; i < gpus.size(); i++) {
        out.append("GPU ").append(i + 1).append("     : ").append(gpus.get(i)).append('\n');
      }
    }
    return out.toString();
  }

  private static String cpuName() {
    String value = wmicValue("Processor", "Name");
    if (value != null && !value.trim().isEmpty()) return value.trim();
    String output =
        run("powershell", "-NoProfile", "-Command", "(Get-CimInstance Win32_Processor).Name");
    if (output != null && !output.trim().isEmpty()) return output.trim();
    value = System.getenv("PROCESSOR_IDENTIFIER");
    return value == null || value.trim().isEmpty() ? "unknown" : value.trim();
  }

  private static long physicalMemory() {
    try {
      Object bean = ManagementFactory.getOperatingSystemMXBean();
      Method method = bean.getClass().getMethod("getTotalPhysicalMemorySize");
      Object value = method.invoke(bean);
      if (value instanceof Number) return ((Number) value).longValue();
    } catch (Exception ignored) {
    }
    return Runtime.getRuntime().maxMemory();
  }

  private static List<String> nvidiaGpus() {
    List<String> result = new ArrayList<>();
    String output =
        run(
            "nvidia-smi",
            "--query-gpu=name,memory.total,driver_version",
            "--format=csv,noheader,nounits");
    if (output == null) return result;
    for (String line : output.split("\\r?\\n")) {
      line = line.trim();
      if (line.isEmpty()) continue;
      String[] parts = line.split("\\s*,\\s*");
      if (parts.length >= 2) {
        String name = parts[0];
        String vram = parts[1];
        String driver = parts.length >= 3 ? parts[2] : "";
        result.add(
            name + " | VRAM: " + vram + " MiB" + (driver.isEmpty() ? "" : " | Driver: " + driver));
      } else {
        result.add(line);
      }
    }
    return result;
  }

  private static List<String> windowsGpus() {
    List<String> result = new ArrayList<>();
    String output =
        run(
            "wmic",
            "path",
            "win32_VideoController",
            "get",
            "Name,AdapterRAM,DriverVersion",
            "/format:list");
    if (output == null || output.trim().isEmpty()) {
      output =
          run(
              "powershell",
              "-NoProfile",
              "-Command",
              "Get-CimInstance Win32_VideoController | ForEach-Object {"
                  + " 'Name=' + $_.Name; 'AdapterRAM=' + $_.AdapterRAM;"
                  + " 'DriverVersion=' + $_.DriverVersion; '' }");
    }
    if (output == null) return result;
    String name = "";
    String ram = "";
    String driver = "";
    for (String raw : output.split("\\r?\\n")) {
      String line = raw.trim();
      if (line.isEmpty()) {
        if (!name.isEmpty()) result.add(formatGpu(name, ram, driver));
        name = "";
        ram = "";
        driver = "";
        continue;
      }
      int eq = line.indexOf('=');
      if (eq < 0) continue;
      String key = line.substring(0, eq).trim();
      String value = line.substring(eq + 1).trim();
      if ("Name".equalsIgnoreCase(key)) name = value;
      else if ("AdapterRAM".equalsIgnoreCase(key)) ram = value;
      else if ("DriverVersion".equalsIgnoreCase(key)) driver = value;
    }
    if (!name.isEmpty()) result.add(formatGpu(name, ram, driver));
    return result;
  }

  private static String formatGpu(String name, String ram, String driver) {
    StringBuilder out = new StringBuilder(name);
    try {
      long bytes = Long.parseLong(ram);
      if (bytes > 0) out.append(" | VRAM: ").append(formatBytes(bytes));
    } catch (Exception ignored) {
      if (!ram.isEmpty()) out.append(" | VRAM: ").append(ram).append(" bytes");
    }
    if (!driver.isEmpty()) out.append(" | Driver: ").append(driver);
    return out.toString();
  }

  private static String wmicValue(String table, String field) {
    String output = run("wmic", "path", "win32_" + table, "get", field, "/value");
    if (output == null) return null;
    for (String line : output.split("\\r?\\n")) {
      if (line.trim().startsWith(field + "=")) return line.substring(line.indexOf('=') + 1);
    }
    return null;
  }

  private static String run(String... command) {
    try {
      ProcessBuilder builder = new ProcessBuilder(command);
      builder.redirectErrorStream(true);
      Process process = builder.start();
      StringBuilder output = new StringBuilder();
      try (java.io.BufferedReader reader =
          new java.io.BufferedReader(
              new java.io.InputStreamReader(process.getInputStream(), "UTF-8"))) {
        String line;
        while ((line = reader.readLine()) != null) output.append(line).append('\n');
      }
      if (!process.waitFor(5, TimeUnit.SECONDS)) {
        process.destroyForcibly();
        return null;
      }
      return output.toString();
    } catch (Exception ignored) {
      return null;
    }
  }

  private static String formatBytes(long bytes) {
    if (bytes <= 0) return "unknown";
    double gib = bytes / 1024.0 / 1024.0 / 1024.0;
    return String.format(java.util.Locale.ENGLISH, "%.2f GiB", gib);
  }
}
