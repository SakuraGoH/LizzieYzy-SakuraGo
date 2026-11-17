package featurecat.lizzie.analysis;

import featurecat.lizzie.gui.FoxKifuDownload;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.SwingUtilities;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * 野狐棋谱查询。
 *
 * <p>抓谱接口与 FoxSgfsDownload 保持一致：QueryUserInfoPanel 查 UID， YHWQFetchChessList 拉列表，
 * TXWQFetchChess/YHWQFetchChess 取 SGF。所有网络请求在后台线程执行，结果回到 EDT 更新界面。
 */
public class GetFoxRequest {
  private static final String BASE_URL = "https://h5.foxwq.com/yehuDiamond/chessbook_local";
  private static final String QUERY_USER_URL = "https://newframe.foxwq.com/cgi/QueryUserInfoPanel";
  private static final String MOBILE_UA =
      "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 "
          + "(KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1";
  private static final String CGI_UA = "okhttp/3.12.12";
  private static final String DESKTOP_UA =
      "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
          + "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";
  private static final String QIPU_SEARCH_URL = "https://www.foxwq.com/qipu.html";
  private static final String QIPU_LIST_URL = "https://www.foxwq.com/newqipu/requestQp";
  private static final int MAX_QIPU_SEARCH_PAGES = 500;
  private static final int MAX_QIPU_META_PAGES = 500;

  private final FoxKifuDownload foxKifuDownload;
  private final ExecutorService executor =
      Executors.newSingleThreadExecutor(
          r -> {
            Thread t = new Thread(r, "fox-kifu-http");
            t.setDaemon(true);
            return t;
          });

  public GetFoxRequest(FoxKifuDownload foxKifuDownload) {
    this.foxKifuDownload = foxKifuDownload;
  }

  public void sendCommand(String command) {
    if (command == null || command.trim().isEmpty()) return;
    executor.execute(() -> handleCommand(command.trim()));
  }

  private void handleCommand(String command) {
    try {
      if (command.startsWith("user_name_all ")) {
        String name = command.substring("user_name_all ".length()).trim();
        String uid = queryUserUid(name);
        streamFullHistory(uid, name);
      } else if (command.startsWith("uid_all ")) {
        String uid = command.substring("uid_all ".length()).trim();
        streamFullHistory(uid, "");
      } else if (command.startsWith("user_name ")) {
        String name = command.substring("user_name ".length()).trim();
        String uid = queryUserUid(name);
        deliver(fetchChessList(uid, "0"));
      } else if (command.startsWith("uid ")) {
        String[] parts = command.split("\\s+");
        if (parts.length >= 3) deliver(fetchChessList(parts[1], parts[2]));
      } else if (command.startsWith("chessid ")) {
        String chessid = command.substring("chessid ".length()).trim();
        JSONObject result = new JSONObject();
        result.put("chess", fetchSgf(chessid));
        deliver(result.toString());
      }
    } catch (Exception e) {
      JSONObject error = new JSONObject();
      error.put("error", e.getMessage() == null ? e.toString() : e.getMessage());
      deliver(error.toString());
    }
  }

  private void progress(String message) {
    JSONObject value = new JSONObject();
    value.put("progress", message);
    deliver(value.toString());
  }

  private void deliver(String line) {
    if (foxKifuDownload == null) return;
    SwingUtilities.invokeLater(() -> foxKifuDownload.receiveResult(line));
  }

  /** 全量历史：H5 最新对局 + 官方棋谱库按棋手名翻页，棋谱详情用于补全双方信息。 */
  /** 边查边显示：先显示 H5 最新对局，再逐页追加棋谱库历史，最后补全详情。 */
  private void streamFullHistory(String uid, String nickname) throws Exception {
    JSONArray h5Items = new JSONArray();
    Map<String, JSONObject> seen = new LinkedHashMap<>();
    progress("正在获取最新对局...");
    try {
      JSONObject latest = new JSONObject(fetchChessList(uid, "0"));
      JSONArray list = latest.optJSONArray("chesslist");
      if (list != null) {
        h5Items = list;
        for (int i = 0; i < list.length(); i++) {
          JSONObject item = list.getJSONObject(i);
          String chessid = item.optString("chessid", "");
          if (!chessid.isEmpty()) seen.put(chessid, item);
        }
      }
    } catch (Exception ignored) {
    }
    JSONObject reset = new JSONObject();
    reset.put("chesslist", h5Items);
    reset.put("reset", true);
    reset.put("full", true);
    reset.put("myuid", Long.parseLong(uid));
    deliver(reset.toString());

    String keyword = resolveHistoryKeyword(nickname, h5Items, uid);
    List<JSONObject> search = new ArrayList<>();
    if (!keyword.isEmpty()) {
      search = streamQipuSearch(keyword, seen, uid);
    }
    if (search.isEmpty()) {
      for (String alias : aliasesFromH5(h5Items, uid)) {
        if (alias.equals(keyword)) continue;
        search = streamQipuSearch(alias, seen, uid);
        if (!search.isEmpty()) break;
      }
    }
    if (!search.isEmpty()) {
      progress("正在补全历史棋谱信息...");
      List<JSONObject> meta = fetchQipuMeta(uid);
      Map<String, List<JSONObject>> metaByTitle = new LinkedHashMap<>();
      for (JSONObject item : meta) {
        String key = titleKey(item.optString("title", ""));
        if (!key.isEmpty()) metaByTitle.computeIfAbsent(key, k -> new ArrayList<>()).add(item);
      }
      JSONArray updates = new JSONArray();
      for (JSONObject item : search) {
        JSONObject merged = mergeQipuItem(item, meta, metaByTitle);
        if (!merged.optString("chessid", "").isEmpty()) updates.put(merged);
      }
      JSONObject update = new JSONObject();
      update.put("updates", updates);
      deliver(update.toString());
    }

    JSONObject done = new JSONObject();
    done.put("done", true);
    done.put("full", true);
    deliver(done.toString());
  }

  private List<JSONObject> streamQipuSearch(
      String keyword, Map<String, JSONObject> seen, String uid) throws Exception {
    List<JSONObject> result = new ArrayList<>();
    for (int page = 1; page <= MAX_QIPU_SEARCH_PAGES; page++) {
      progress("正在检索历史棋谱 第 " + page + " 页...");
      String url = QIPU_SEARCH_URL + "?keyword=" + encode(keyword) + "&p=" + page;
      JSONArray items;
      try {
        items = parseQipuSearchHtml(httpGet(url, DESKTOP_UA));
      } catch (Exception e) {
        break;
      }
      if (items.length() == 0) break;
      JSONArray pageItems = new JSONArray();
      for (int i = 0; i < items.length(); i++) {
        JSONObject item = items.getJSONObject(i);
        String chessid = item.optString("chessid", "");
        if (chessid.isEmpty() || seen.containsKey(chessid)) continue;
        // 棋谱库列表只有标题，先放入占位信息，详情返回后会原地更新。
        String title = item.optString("title", "");
        item.put("blacknick", title);
        item.put("blackenname", title);
        item.put("whitenick", "");
        item.put("whiteenname", "");
        item.put("movenum", 0);
        item.put("winner", 0);
        item.put("point", 0);
        item.put("rule", 0);
        seen.put(chessid, item);
        result.add(item);
        pageItems.put(item);
      }
      if (pageItems.length() == 0) break;
      JSONObject pageResult = new JSONObject();
      pageResult.put("chesslist", pageItems);
      pageResult.put("append", true);
      pageResult.put("full", true);
      pageResult.put("myuid", Long.parseLong(uid));
      deliver(pageResult.toString());
      Thread.sleep(80);
    }
    return result;
  }

  private List<JSONObject> fetchQipuMeta(String uid) throws Exception {
    List<JSONObject> result = new ArrayList<>();
    for (int type : new int[] {1, 6}) {
      String lastCode = "0";
      for (int page = 1; page <= MAX_QIPU_META_PAGES; page++) {
        String url =
            QIPU_LIST_URL
                + "?type="
                + type
                + "&lastCode="
                + encode(lastCode)
                + "&GradeLow=0&GradeHigh=0&FindUserName=&uid="
                + encode(uid);
        List<JSONObject> items;
        try {
          items = parseQipuText(httpGet(url, DESKTOP_UA));
        } catch (Exception e) {
          break;
        }
        if (items.isEmpty()) break;
        result.addAll(items);
        if (page % 5 == 0) progress("正在补全历史棋谱信息 第 " + page + " 页...");
        String next = items.get(items.size() - 1).optString("chessid", "");
        if (next.isEmpty() || next.equals(lastCode)) break;
        lastCode = next;
        Thread.sleep(80);
      }
    }
    return result;
  }

  private JSONObject mergeQipuItem(
      JSONObject search, List<JSONObject> metaAll, Map<String, List<JSONObject>> metaByTitle) {
    String key = titleKey(search.optString("title", ""));
    JSONObject match = null;
    for (JSONObject candidate : metaByTitle.getOrDefault(key, new ArrayList<>())) {
      if (dateDistance(search, candidate) <= 2) {
        match = candidate;
        break;
      }
    }
    if (match == null) {
      for (JSONObject candidate : metaAll) {
        if (dateDistance(search, candidate) <= 2
            && titleMentionsPlayers(search.optString("title", ""), candidate)) {
          match = candidate;
          break;
        }
      }
    }
    JSONObject merged = match == null ? new JSONObject() : new JSONObject(match.toString());
    merged.put("chessid", search.optString("chessid", ""));
    merged.put("source", "h5");
    merged.put("qipu_id", match == null ? "" : match.optString("chessid", ""));
    merged.put("title", search.optString("title", ""));
    merged.put(
        "starttime",
        search.optString("starttime", match == null ? "" : match.optString("starttime", "")));
    if (match == null) {
      // 棋谱库列表没有双方信息时，至少把标题作为可读名称，避免表格空行。
      merged.put("blacknick", search.optString("title", ""));
      merged.put("blackenname", search.optString("title", ""));
      merged.put("whitenick", "");
      merged.put("whiteenname", "");
      merged.put("movenum", 0);
      merged.put("winner", 0);
      merged.put("point", 0);
      merged.put("rule", 0);
    }
    return merged;
  }

  private String resolveHistoryKeyword(String nickname, JSONArray h5, String uid) {
    String keyword = nickname == null ? "" : nickname.trim();
    if (!keyword.isEmpty()) {
      String canonical = queryCanonicalName(keyword);
      return canonical.isEmpty() ? keyword : canonical;
    }
    List<String> aliases = aliasesFromH5(h5, uid);
    for (String alias : aliases) {
      String canonical = queryCanonicalName(alias);
      if (!canonical.isEmpty()) return canonical;
    }
    return aliases.isEmpty() ? "" : aliases.get(0);
  }

  private String queryCanonicalName(String name) {
    if (name == null || name.trim().isEmpty()) return "";
    try {
      JSONObject info = queryUserInfo(name.trim());
      String canonical = info.optString("username", "").trim();
      if (canonical.isEmpty()) canonical = info.optString("name", "").trim();
      if (canonical.isEmpty()) canonical = info.optString("englishname", "").trim();
      return canonical;
    } catch (Exception e) {
      return "";
    }
  }

  private List<String> aliasesFromH5(JSONArray h5, String uid) {
    List<String> aliases = new ArrayList<>();
    if (h5 == null) return aliases;
    for (int i = 0; i < h5.length(); i++) {
      JSONObject item = h5.optJSONObject(i);
      if (item == null) continue;
      if (uid.equals(item.optString("blackuid", ""))) {
        addAlias(aliases, item.optString("blacknick", ""));
        addAlias(aliases, item.optString("blackenname", ""));
      }
      if (uid.equals(item.optString("whiteuid", ""))) {
        addAlias(aliases, item.optString("whitenick", ""));
        addAlias(aliases, item.optString("whiteenname", ""));
      }
    }
    return aliases;
  }

  private void addAlias(List<String> aliases, String value) {
    value = value == null ? "" : value.trim();
    if (!value.isEmpty() && !aliases.contains(value)) aliases.add(value);
  }

  private JSONArray parseQipuSearchHtml(String html) {
    JSONArray result = new JSONArray();
    Matcher dates = Pattern.compile("qipu-time[^>]*>([^<]+)<").matcher(html);
    List<String> dateList = new ArrayList<>();
    while (dates.find()) dateList.add(dates.group(1).trim());
    Matcher rows =
        Pattern.compile(
                "<h4 class=\"qipu-title\">\\s*<a href=\"([^\"]+)\"[^>]*>(.*?)</a>", Pattern.DOTALL)
            .matcher(html);
    int index = 0;
    while (rows.find()) {
      Matcher idMatcher = Pattern.compile("chessid=(\\d+)").matcher(rows.group(1));
      if (!idMatcher.find()) {
        index++;
        continue;
      }
      String date = index < dateList.size() ? dateList.get(index) : "";
      if (date.length() == 16) date += ":00";
      String title = unescapeHtml(stripTags(rows.group(2))).trim();
      JSONObject item = new JSONObject();
      item.put("chessid", idMatcher.group(1));
      item.put("source", "h5");
      item.put("starttime", date);
      item.put("endtime", date);
      item.put("title", title);
      result.put(item);
      index++;
    }
    return result;
  }

  private List<JSONObject> parseQipuText(String text) {
    List<JSONObject> result = new ArrayList<>();
    if (text == null || !text.contains("[foxwq_qp]")) return result;
    String body = text.split("\\[foxwq_qp\\]", 2)[1].replace("[foxwq_end]", "");
    for (String chunk : body.split("\\[foxwq_game_detail_list\\]")) {
      String[] fields = chunk.split("\\[foxwq_game_detail\\]", -1);
      if (fields.length < 22 || fields[0].trim().isEmpty()) continue;
      String qipuId = fields[0].trim();
      String endtime = fields[20].trim();
      JSONObject item = new JSONObject();
      item.put("chessid", qipuId);
      item.put("qipu_id", qipuId);
      item.put("source", "qipu");
      item.put("blackuid", fields[1].trim());
      item.put("blackdan", safeInt(fields[2]));
      item.put("blacknick", fields[3].trim());
      item.put("blackenname", fields[4].trim());
      item.put("whiteuid", fields[6].trim());
      item.put("whitedan", safeInt(fields[7]));
      item.put("whitenick", fields[8].trim());
      item.put("whiteenname", fields[9].trim());
      item.put("winner", safeInt(fields[11]));
      item.put("point", safeInt(fields[12]));
      item.put("reason", safeInt(fields[13]));
      item.put("movenum", safeInt(fields[14]));
      item.put("komi", safeInt(fields[16]));
      item.put("endtime", endtime);
      item.put("starttime", qipuStartTime(qipuId, endtime));
      item.put("title", fields[21].trim());
      item.put("rule", 0);
      result.add(item);
    }
    return result;
  }

  private String qipuStartTime(String qipuId, String endtime) {
    String date = "";
    if (qipuId.length() >= 8 && qipuId.substring(0, 8).matches("\\d{8}")) {
      date = qipuId.substring(0, 4) + "-" + qipuId.substring(4, 6) + "-" + qipuId.substring(6, 8);
    }
    String timePart = endtime.length() >= 19 ? endtime.substring(11, 19) : "";
    if (!date.isEmpty() && !timePart.isEmpty()) return date + " " + timePart;
    return endtime.isEmpty() ? date : endtime;
  }

  private int dateDistance(JSONObject a, JSONObject b) {
    String da = dateOf(a);
    String db = dateOf(b);
    if (da.isEmpty() || db.isEmpty()) return 0;
    try {
      return Math.abs(
          Integer.parseInt(da.replace("-", "")) - Integer.parseInt(db.replace("-", "")));
    } catch (Exception e) {
      return 0;
    }
  }

  private String dateOf(JSONObject item) {
    String value = item.optString("starttime", item.optString("endtime", ""));
    return value.length() >= 10 ? value.substring(0, 10) : value;
  }

  private String titleKey(String title) {
    String key = title == null ? "" : title.replaceAll("\\s+", "");
    return key.length() > 16 ? key.substring(0, 16) : key;
  }

  private boolean titleMentionsPlayers(String title, JSONObject meta) {
    String text = title == null ? "" : title.toLowerCase();
    if (text.isEmpty()) return false;
    int hits = 0;
    for (String key : new String[] {"blacknick", "blackenname", "whitenick", "whiteenname"}) {
      String name = meta.optString(key, "").trim().toLowerCase();
      if (name.length() >= 2 && text.contains(name)) hits++;
    }
    return hits >= 2;
  }

  private int safeInt(String value) {
    try {
      return Integer.parseInt(value.trim());
    } catch (Exception e) {
      return 0;
    }
  }

  private String stripTags(String value) {
    return value == null ? "" : value.replaceAll("<[^>]+>", "");
  }

  private String unescapeHtml(String value) {
    if (value == null) return "";
    return value
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'");
  }

  private String queryUserUid(String nickname) throws Exception {
    return queryUserInfo(nickname).optString("uid", "").trim();
  }

  private JSONObject queryUserInfo(String nickname) throws Exception {
    String url =
        QUERY_USER_URL
            + "?srcuid=0&username="
            + URLEncoder.encode(nickname, StandardCharsets.UTF_8.name());
    JSONObject info = new JSONObject(httpGet(url, MOBILE_UA));
    String uid = info.optString("uid", "").trim();
    int result = info.optInt("result", info.optInt("errcode", -1));
    if (uid.isEmpty() || "0".equals(uid) || "None".equals(uid)) {
      if (result != 0) {
        throw new Exception(info.optString("resultstr", info.optString("errmsg", "未找到该野狐用户")));
      }
      throw new Exception("找到了账号，但 UID 为空");
    }
    return info;
  }

  private String fetchChessList(String uid, String lastCode) throws Exception {
    String url =
        BASE_URL
            + "/YHWQFetchChessList?srcuid=0&dstuid="
            + encode(uid)
            + "&type=1&lastcode="
            + encode(lastCode == null ? "0" : lastCode)
            + "&searchkey=&uin="
            + encode(uid);
    JSONObject data = new JSONObject(httpGet(url, MOBILE_UA));
    if (data.optInt("result", 0) != 0) {
      throw new Exception(data.optString("resultstr", "棋谱列表获取失败"));
    }
    try {
      data.put("myuid", Long.parseLong(uid));
    } catch (Exception ignored) {
    }
    return data.toString();
  }

  /** 批量下载时同步获取单个棋谱。 */
  public String fetchSgfNow(String chessid) throws Exception {
    return fetchSgf(chessid);
  }

  private String fetchSgf(String chessid) throws Exception {
    String form = "chessid=" + encode(chessid);
    String[] cgiUrls = {
      "http://happyapp.huanle.qq.com/cgi-bin/CommonMobileCGI/TXWQFetchChess",
      "http://cgi.foxwq.com/cgi-bin/CommonMobileCGI/TXWQFetchChess"
    };
    Exception last = null;
    for (String url : cgiUrls) {
      try {
        JSONObject data = new JSONObject(httpPost(url, form, CGI_UA));
        String chess = data.optString("chess", "");
        if (data.optInt("result", -1) == 0 && !chess.trim().isEmpty()) return chess;
      } catch (Exception e) {
        last = e;
      }
    }
    try {
      String url = BASE_URL + "/YHWQFetchChess?chessid=" + encode(chessid);
      JSONObject data = new JSONObject(httpGet(url, MOBILE_UA));
      String chess = data.optString("chess", "");
      if (data.optInt("result", 0) == 0 && !chess.trim().isEmpty()) return chess;
      throw new Exception(data.optString("resultstr", "棋谱获取失败"));
    } catch (Exception e) {
      if (last != null) e.addSuppressed(last);
      throw e;
    }
  }

  private static String encode(String value) throws Exception {
    return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8.name());
  }

  private static String httpGet(String url, String userAgent) throws Exception {
    HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
    connection.setConnectTimeout(20000);
    connection.setReadTimeout(25000);
    connection.setRequestMethod("GET");
    connection.setRequestProperty("User-Agent", userAgent);
    connection.setRequestProperty("Accept", "application/json,text/plain,*/*");
    connection.setRequestProperty("Connection", "close");
    return read(connection);
  }

  private static String httpPost(String url, String form, String userAgent) throws Exception {
    HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
    connection.setConnectTimeout(20000);
    connection.setReadTimeout(25000);
    connection.setRequestMethod("POST");
    connection.setDoOutput(true);
    connection.setRequestProperty("User-Agent", userAgent);
    connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
    connection.setRequestProperty("Connection", "close");
    byte[] bytes = form.getBytes(StandardCharsets.UTF_8);
    connection.setFixedLengthStreamingMode(bytes.length);
    try (OutputStream out = connection.getOutputStream()) {
      out.write(bytes);
    }
    return read(connection);
  }

  private static String read(HttpURLConnection connection) throws Exception {
    int code = connection.getResponseCode();
    if (code < 200 || code >= 300) throw new Exception("HTTP " + code);
    StringBuilder text = new StringBuilder();
    try (BufferedReader reader =
        new BufferedReader(
            new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
      String line;
      while ((line = reader.readLine()) != null) text.append(line).append('\n');
    } finally {
      connection.disconnect();
    }
    return text.toString();
  }
}
