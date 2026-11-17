package featurecat.lizzie.analysis;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * 命令行使用历史。
 *
 * <p>按通道(引擎槽位、分析引擎、形势判断引擎、裁判引擎)分别记录，互不干扰。 每次保存命令时在对应通道追加一个历史节点，持久化到工作目录的 commandHistory.json。
 */
public final class CommandHistory {
  private static final String FILE_NAME = "commandHistory.json";
  private static final int MAX_NODES_PER_CHANNEL = 200;

  private static final Map<String, List<Node>> CHANNELS = new LinkedHashMap<>();
  private static boolean loaded = false;

  private CommandHistory() {}

  public static final class Node {
    public final String command;
    public final long time;

    Node(String command, long time) {
      this.command = command;
      this.time = time;
    }

    public String formattedTime() {
      return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(time));
    }

    @Override
    public String toString() {
      String oneLine = command.replace('\n', ' ').replace('\r', ' ');
      if (oneLine.length() > 120) oneLine = oneLine.substring(0, 117) + "...";
      return "[" + formattedTime() + "] " + oneLine;
    }
  }

  /** 记录一个保存节点到指定通道。连续保存同一条命令不会重复记录。 */
  public static synchronized void add(String channel, String command) {
    if (command == null) return;
    String trimmed = command.trim();
    if (trimmed.isEmpty()) return;
    load();
    List<Node> nodes = CHANNELS.computeIfAbsent(normalize(channel), k -> new ArrayList<>());
    if (!nodes.isEmpty() && nodes.get(0).command.equals(trimmed)) return;
    nodes.removeIf(n -> n.command.equals(trimmed));
    nodes.add(0, new Node(trimmed, System.currentTimeMillis()));
    while (nodes.size() > MAX_NODES_PER_CHANNEL) nodes.remove(nodes.size() - 1);
    save();
  }

  public static synchronized List<Node> getAll(String channel) {
    load();
    List<Node> nodes = CHANNELS.get(normalize(channel));
    return nodes == null ? new ArrayList<>() : new ArrayList<>(nodes);
  }

  public static synchronized void remove(String channel, int index) {
    load();
    List<Node> nodes = CHANNELS.get(normalize(channel));
    if (nodes != null && index >= 0 && index < nodes.size()) {
      nodes.remove(index);
      save();
    }
  }

  public static synchronized void clear(String channel) {
    load();
    List<Node> nodes = CHANNELS.get(normalize(channel));
    if (nodes != null) {
      nodes.clear();
      save();
    }
  }

  private static String normalize(String channel) {
    return channel == null || channel.trim().isEmpty() ? "default" : channel.trim();
  }

  private static void load() {
    if (loaded) return;
    loaded = true;
    File file = new File(FILE_NAME);
    if (!file.exists()) return;
    try {
      String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
      JSONObject channels;
      if (content.trim().startsWith("[")) {
        // 兼容旧版单一列表格式
        channels = new JSONObject();
        channels.put("default", new JSONArray(content));
      } else {
        JSONObject root = new JSONObject(content);
        channels = root.optJSONObject("channels");
        if (channels == null) channels = new JSONObject();
      }
      for (String channel : channels.keySet()) {
        JSONArray array = channels.optJSONArray(channel);
        if (array == null) continue;
        List<Node> nodes = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
          JSONObject object = array.optJSONObject(i);
          if (object == null) continue;
          String command = object.optString("command", "");
          if (!command.trim().isEmpty()) nodes.add(new Node(command, object.optLong("time", 0)));
        }
        CHANNELS.put(channel, nodes);
      }
    } catch (Exception ignored) {
    }
  }

  private static void save() {
    JSONObject channels = new JSONObject();
    for (Map.Entry<String, List<Node>> entry : CHANNELS.entrySet()) {
      JSONArray array = new JSONArray();
      for (Node node : entry.getValue()) {
        JSONObject object = new JSONObject();
        object.put("command", node.command);
        object.put("time", node.time);
        array.put(object);
      }
      channels.put(entry.getKey(), array);
    }
    JSONObject root = new JSONObject();
    root.put("version", 2);
    root.put("channels", channels);
    try {
      Files.write(new File(FILE_NAME).toPath(), root.toString(2).getBytes(StandardCharsets.UTF_8));
    } catch (IOException ignored) {
    }
  }
}
