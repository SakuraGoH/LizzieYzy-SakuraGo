package featurecat.lizzie.gui;

import featurecat.lizzie.Config;
import featurecat.lizzie.Lizzie;
import featurecat.lizzie.analysis.GetFoxRequest;
import featurecat.lizzie.rules.SGFParser;
import featurecat.lizzie.util.Utils;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
import javax.swing.AbstractCellEditor;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class FoxKifuDownload extends JFrame {
  private DefaultTableModel model;
  private JTable table;
  private JScrollPane scrollPane;
  private JTextField txtUserName;
  public GetFoxRequest foxReq;
  private List<KifuInfo> foxKifuInfos;
  private int myUid;
  private String lastCode = "";
  private int tabNumber = 1;
  private int numbersPerTab = 25;
  private int curTabNumber = 1;
  private boolean isComplete = false;
  private boolean isSearching = false;
  JLabel lblTab;
  private ArrayList<String[]> rows;
  private boolean isSecondTimeReqEmpty = false;
  private boolean isRequestEmpty = false;
  private JFontCheckBox chkFullHistory;
  private JLabel lblStatus;
  private JTextField txtConcurrent;
  private JProgressBar progressBar;
  private boolean fullHistoryMode = false;
  private boolean downloading = false;

  public FoxKifuDownload() {
    Lizzie.setFrameSize(this, 950, 635);
    setTitle(Lizzie.resourceBundle.getString("FoxKifuDownload.title"));
    try {
      this.setIconImage(ImageIO.read(MoreEngines.class.getResourceAsStream("/assets/logo.png")));
    } catch (IOException e1) {
      // TODO Auto-generated catch block
      e1.printStackTrace();
    }
    setAlwaysOnTop(Lizzie.frame.isAlwaysOnTop());
    setLocationRelativeTo(Lizzie.frame);

    JPanel panel = new JPanel();
    getContentPane().add(panel, BorderLayout.NORTH);

    JLabel lblUserName =
        new JFontLabel(Lizzie.resourceBundle.getString("FoxKifuDownload.lblUserName"));
    panel.add(lblUserName);

    txtUserName = new JFontTextField();
    panel.add(txtUserName);
    txtUserName.setColumns(10);
    txtUserName.addKeyListener(
        new KeyAdapter() {
          public void keyPressed(KeyEvent e) {
            if (e.getKeyCode() == KeyEvent.VK_ENTER) {
              getFoxKifus();
            }
          }
        });
    txtUserName.setText(Lizzie.config.lastFoxName);

    JButton btnSearch =
        new JFontButton(Lizzie.resourceBundle.getString("FoxKifuDownload.btnSearch"));
    btnSearch.addActionListener(
        new ActionListener() {
          public void actionPerformed(ActionEvent e) {
            getFoxKifus();
          }
        });
    panel.add(btnSearch);

    chkFullHistory =
        new JFontCheckBox(Lizzie.resourceBundle.getString("FoxKifuDownload.chkFullHistory"));
    chkFullHistory.setSelected(true);
    panel.add(chkFullHistory);

    JLabel lblAfterGet = new JFontLabel();
    lblAfterGet.setText(Lizzie.resourceBundle.getString("FoxKifuDownload.lblAfterGet"));
    panel.add(lblAfterGet);

    JComboBox<String> cbxAfterGet = new JFontComboBox<String>();
    panel.add(cbxAfterGet);
    cbxAfterGet.addItem(Lizzie.resourceBundle.getString("FoxKifuDownload.cbxAfterGet.min"));
    cbxAfterGet.addItem(Lizzie.resourceBundle.getString("FoxKifuDownload.cbxAfterGet.close"));
    cbxAfterGet.addItem(Lizzie.resourceBundle.getString("FoxKifuDownload.cbxAfterGet.none"));
    cbxAfterGet.addItemListener(
        new ItemListener() {
          public void itemStateChanged(final ItemEvent e) {
            int index = cbxAfterGet.getSelectedIndex();
            Lizzie.config.foxAfterGet = index;
            Lizzie.config.uiConfig.put("fox-after-get", index);
          }
        });
    cbxAfterGet.setSelectedIndex(Lizzie.config.foxAfterGet);

    JPanel buttonPane = new JPanel();
    JPanel downloadPane = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 4, 2));
    JPanel southPane = new JPanel(new java.awt.GridLayout(2, 1));
    southPane.add(buttonPane);
    southPane.add(downloadPane);

    table =
        new JTable() {
          public boolean isCellEditable(int row, int column) {
            if (column == 8) return true;
            else return false;
          }
        };
    table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
    table.setFillsViewportHeight(true);
    ((DefaultTableCellRenderer) table.getTableHeader().getDefaultRenderer())
        .setHorizontalAlignment(JLabel.CENTER);
    table
        .getTableHeader()
        .setFont(new Font(Config.sysDefaultFontName, Font.PLAIN, Config.frameFontSize));
    table.setFont(new Font(Config.sysDefaultFontName, Font.PLAIN, Config.frameFontSize));
    table.setRowHeight(Config.menuHeight);
    table.addMouseListener(
        new MouseAdapter() {
          public void mouseClicked(MouseEvent e) {
            int row = table.rowAtPoint(e.getPoint());
            int col = table.columnAtPoint(e.getPoint());
            if (e.getClickCount() == 2) {
              if (row >= 0 && col >= 0) {
                foxReq.sendCommand("chessid " + table.getValueAt(row, 10).toString());
              }
            }
          }
        });
    TableCellRenderer tcr = new ColorTableCellRenderer();
    table.setDefaultRenderer(Object.class, tcr);
    scrollPane = new JScrollPane(table);
    getContentPane().add(scrollPane, BorderLayout.CENTER);
    getContentPane().add(southPane, BorderLayout.SOUTH);

    JButton btnFirst = new JFontButton("|<");
    btnFirst.addActionListener(
        new ActionListener() {
          public void actionPerformed(ActionEvent e) {
            if (rows == null) return;
            curTabNumber = 1;
            while (model.getRowCount() > 0) {
              model.removeRow(model.getRowCount() - 1);
            }
            for (int i = (curTabNumber - 1) * numbersPerTab;
                i < numbersPerTab && i < foxKifuInfos.size();
                i++) {
              model.addRow(rows.get(i));
            }
            setLblTab(1);
            isSecondTimeReqEmpty = false;
          }
        });
    buttonPane.add(btnFirst);

    JButton btnPrevious = new JFontButton("<");
    btnPrevious.addActionListener(
        new ActionListener() {
          public void actionPerformed(ActionEvent e) {
            if (rows == null) return;
            if (curTabNumber == 1) return;
            curTabNumber = curTabNumber - 1;
            while (model.getRowCount() > 0) {
              model.removeRow(model.getRowCount() - 1);
            }
            for (int i = (curTabNumber - 1) * numbersPerTab;
                i < curTabNumber * numbersPerTab && i < foxKifuInfos.size();
                i++) {
              model.addRow(rows.get(i));
            }
            setLblTab(curTabNumber);
            isSecondTimeReqEmpty = false;
          }
        });
    buttonPane.add(btnPrevious);

    JButton btnNext = new JFontButton(">");
    btnNext.addActionListener(
        new ActionListener() {
          public void actionPerformed(ActionEvent e) {
            if (rows == null) return;
            if (curTabNumber == tabNumber) {
              maybeGetNextPage();
              return;
            }
            curTabNumber = curTabNumber + 1;
            while (model.getRowCount() > 0) {
              model.removeRow(model.getRowCount() - 1);
            }
            for (int i = (curTabNumber - 1) * numbersPerTab;
                i < curTabNumber * numbersPerTab && i < foxKifuInfos.size();
                i++) {
              model.addRow(rows.get(i));
            }
            setLblTab(curTabNumber);
            maybeGetNextPage();
          }
        });
    buttonPane.add(btnNext);

    JButton btnLast = new JFontButton(">|");
    btnLast.addActionListener(
        new ActionListener() {
          public void actionPerformed(ActionEvent e) {
            if (rows == null) return;
            if (curTabNumber == tabNumber) {
              maybeGetNextPage();
              return;
            }
            curTabNumber = tabNumber;
            while (model.getRowCount() > 0) {
              model.removeRow(model.getRowCount() - 1);
            }
            for (int i = (curTabNumber - 1) * numbersPerTab;
                i < curTabNumber * numbersPerTab && i < foxKifuInfos.size();
                i++) {
              model.addRow(rows.get(i));
            }
            setLblTab(curTabNumber);
            maybeGetNextPage();
          }
        });
    buttonPane.add(btnLast);

    lblTab = new JFontLabel("1/1");
    lblTab.setPreferredSize(new Dimension(Config.menuHeight * 3, Config.menuHeight));
    buttonPane.add(lblTab);

    downloadPane.add(new JLabel(Lizzie.resourceBundle.getString("FoxKifuDownload.concurrent")));
    txtConcurrent = new JFontTextField();
    txtConcurrent.setColumns(3);
    txtConcurrent.setText("3");
    downloadPane.add(txtConcurrent);

    JButton btnSelectAll =
        new JFontButton(Lizzie.resourceBundle.getString("FoxKifuDownload.selectAll"));
    btnSelectAll.addActionListener(e -> table.selectAll());
    downloadPane.add(btnSelectAll);

    JButton btnDownloadSelected =
        new JFontButton(Lizzie.resourceBundle.getString("FoxKifuDownload.downloadSelected"));
    btnDownloadSelected.addActionListener(e -> downloadSelected());
    downloadPane.add(btnDownloadSelected);

    JButton btnDownloadAll =
        new JFontButton(Lizzie.resourceBundle.getString("FoxKifuDownload.downloadAll"));
    btnDownloadAll.addActionListener(e -> downloadAll());
    downloadPane.add(btnDownloadAll);

    progressBar = new JProgressBar();
    progressBar.setPreferredSize(new Dimension(160, 20));
    progressBar.setVisible(false);
    downloadPane.add(progressBar);
    lblStatus = new JFontLabel("");
    buttonPane.add(lblStatus);
  }

  private void maybeGetNextPage() {
    if (fullHistoryMode) return;
    // TODO Auto-generated method stub
    if (curTabNumber == tabNumber || tabNumber >= 4 && curTabNumber == tabNumber - 1) {
      String last = foxKifuInfos.get(foxKifuInfos.size() - 1).chessid;
      if (!lastCode.equals(last)) {
        lastCode = last;
        this.foxReq.sendCommand(
            "uid " + this.myUid + " " + foxKifuInfos.get(foxKifuInfos.size() - 1).chessid);
      } else {
        if (curTabNumber == tabNumber) {
          if (isSecondTimeReqEmpty)
            Utils.showMsg(Lizzie.resourceBundle.getString("FoxKifuDownload.noMoreKifu"), this);
          if (isRequestEmpty) {
            isSecondTimeReqEmpty = true;
          }
        }
      }
    }
  }

  private void getFoxKifus() {
    // TODO Auto-generated method stub
    if (txtUserName.getText().trim().isEmpty()) {
      Utils.showMsg(Lizzie.resourceBundle.getString("FoxKifuDownload.noUser"), this);
      return;
    }
    if (isSearching) {
      Utils.showMsg(Lizzie.resourceBundle.getString("FoxKifuDownload.waitLastSearch"), this);
      return;
    }
    myUid = 0;
    isSearching = true;
    isComplete = false;
    fullHistoryMode = false;
    isSecondTimeReqEmpty = false;
    isRequestEmpty = false;
    if (foxReq == null) foxReq = new GetFoxRequest(this);
    foxKifuInfos = new ArrayList<KifuInfo>();
    lastCode = "";
    lblStatus.setText("");
    Lizzie.config.lastFoxName = txtUserName.getText();
    boolean full = chkFullHistory != null && chkFullHistory.isSelected();
    foxReq.sendCommand((full ? "user_name_all " : "user_name ") + Lizzie.config.lastFoxName);
    Lizzie.config.uiConfig.put("last-fox-name", Lizzie.config.lastFoxName);
  }

  private void downloadSelected() {
    if (table == null || foxKifuInfos == null || foxKifuInfos.isEmpty()) return;
    List<KifuInfo> selected = new ArrayList<>();
    for (int row : table.getSelectedRows()) {
      String chessid = String.valueOf(table.getValueAt(row, 10));
      for (KifuInfo info : foxKifuInfos) {
        if (info.chessid.equals(chessid)) {
          selected.add(info);
          break;
        }
      }
    }
    downloadGames(selected);
  }

  private void downloadAll() {
    if (foxKifuInfos == null || foxKifuInfos.isEmpty()) return;
    downloadGames(new ArrayList<>(foxKifuInfos));
  }

  private void downloadGames(List<KifuInfo> games) {
    if (games == null || games.isEmpty()) {
      Utils.showMsgNoModal(Lizzie.resourceBundle.getString("FoxKifuDownload.noKifu"));
      return;
    }
    if (downloading) return;
    JFileChooser chooser = new JFileChooser();
    chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
    chooser.setDialogTitle(Lizzie.resourceBundle.getString("FoxKifuDownload.downloadDir"));
    if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
    File dir = chooser.getSelectedFile();
    if (dir == null) return;
    if (!dir.exists() && !dir.mkdirs()) return;
    int concurrent = 3;
    try {
      concurrent = Math.max(1, Math.min(10, Integer.parseInt(txtConcurrent.getText().trim())));
    } catch (Exception ignored) {
    }
    if (foxReq == null) foxReq = new GetFoxRequest(this);
    downloading = true;
    progressBar.setVisible(true);
    progressBar.setMaximum(games.size());
    progressBar.setValue(0);
    lblStatus.setText(
        Lizzie.resourceBundle.getString("FoxKifuDownload.downloading") + " 0/" + games.size());
    final File outDir = dir;
    final int poolSize = concurrent;
    new Thread(
            () -> {
              ExecutorService pool = Executors.newFixedThreadPool(poolSize);
              AtomicInteger done = new AtomicInteger();
              AtomicInteger failed = new AtomicInteger();
              AtomicInteger skipped = new AtomicInteger();
              for (KifuInfo info : games) {
                pool.execute(
                    () -> {
                      try {
                        File out = new File(outDir, safeFileName(info));
                        if (out.exists()) {
                          skipped.incrementAndGet();
                        } else {
                          String sgf = foxReq.fetchSgfNow(info.chessid);
                          Files.write(out.toPath(), sgf.getBytes(StandardCharsets.UTF_8));
                          done.incrementAndGet();
                        }
                      } catch (Exception e) {
                        failed.incrementAndGet();
                      }
                      int completed = done.get() + failed.get() + skipped.get();
                      SwingUtilities.invokeLater(
                          () -> {
                            progressBar.setValue(completed);
                            lblStatus.setText(
                                Lizzie.resourceBundle.getString("FoxKifuDownload.downloading")
                                    + " "
                                    + completed
                                    + "/"
                                    + games.size());
                          });
                    });
              }
              pool.shutdown();
              try {
                pool.awaitTermination(30, TimeUnit.MINUTES);
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              }
              SwingUtilities.invokeLater(
                  () -> {
                    downloading = false;
                    progressBar.setVisible(false);
                    lblStatus.setText(
                        Lizzie.resourceBundle.getString("FoxKifuDownload.downloadDone")
                            + " "
                            + done.get()
                            + "，"
                            + Lizzie.resourceBundle.getString("FoxKifuDownload.downloadSkipped")
                            + " "
                            + skipped.get()
                            + "，"
                            + Lizzie.resourceBundle.getString("FoxKifuDownload.downloadFailed")
                            + " "
                            + failed.get());
                    Utils.showMsgNoModal(lblStatus.getText());
                  });
            },
            "fox-kifu-download")
        .start();
  }

  private String safeFileName(KifuInfo info) {
    String name =
        (info.blackName == null ? "" : info.blackName)
            + "_vs_"
            + (info.whiteName == null ? "" : info.whiteName)
            + "_"
            + info.chessid;
    return name.replaceAll("[\\\\/:*?\"<>|\\s]+", "_") + ".sgf";
  }

  private String formatRank(int dan) {
    if (dan <= 0) return "";
    if (dan >= 100) {
      return Lizzie.resourceBundle.getString("FoxKifuDownload.rank.pro")
          + (dan - 99)
          + Lizzie.resourceBundle.getString("FoxKifuDownload.rank.dan");
    }
    int rank = dan - 17;
    if (rank > 0) return rank + Lizzie.resourceBundle.getString("FoxKifuDownload.rank.dan");
    return (Math.abs(rank) + 1) + Lizzie.resourceBundle.getString("FoxKifuDownload.rank.kyu");
  }

  /** 处理全量检索的流式事件：reset / append / updates / done。 */
  private void handleStreamEvent(JSONObject event) {
    if (event.optBoolean("reset", false)) {
      foxKifuInfos = new ArrayList<>();
      rows = null;
      model = null;
      curTabNumber = 1;
      tabNumber = 1;
      isComplete = false;
      fullHistoryMode = true;
      isSearching = true;
      table.setModel(new DefaultTableModel());
    }
    if (event.has("chesslist")) appendGames(event.getJSONArray("chesslist"));
    if (event.has("updates")) updateGames(event.getJSONArray("updates"));
    if (event.optBoolean("done", false)) {
      isSearching = false;
      isComplete = true;
      fullHistoryMode = true;
      rebuildTable();
    }
  }

  private void appendGames(JSONArray array) {
    if (foxKifuInfos == null) foxKifuInfos = new ArrayList<>();
    Set<String> existing = new HashSet<>();
    for (KifuInfo info : foxKifuInfos) existing.add(info.chessid);
    for (int i = 0; i < array.length(); i++) {
      JSONObject object = array.optJSONObject(i);
      if (object == null) continue;
      String chessid = object.optString("chessid", "");
      if (chessid.isEmpty() || existing.contains(chessid)) continue;
      KifuInfo info = parseKifu(object, foxKifuInfos.size() + 1);
      foxKifuInfos.add(info);
      existing.add(chessid);
    }
    rebuildTable();
  }

  private void updateGames(JSONArray array) {
    if (foxKifuInfos == null || foxKifuInfos.isEmpty()) return;
    java.util.Map<String, KifuInfo> byId = new java.util.HashMap<>();
    for (KifuInfo info : foxKifuInfos) byId.put(info.chessid, info);
    boolean changed = false;
    for (int i = 0; i < array.length(); i++) {
      JSONObject object = array.optJSONObject(i);
      if (object == null) continue;
      KifuInfo old = byId.get(object.optString("chessid", ""));
      if (old == null) continue;
      KifuInfo fresh = parseKifu(object, old.index);
      copyKifu(fresh, old);
      changed = true;
    }
    if (changed) rebuildTable();
  }

  private KifuInfo parseKifu(JSONObject object, int index) {
    KifuInfo info = new KifuInfo();
    info.index = index;
    info.playTime = object.optString("starttime", "");
    info.blackName = object.optString("blacknick", object.optString("blackenname", ""));
    info.blackRank = formatRank(object.optInt("blackdan", 0));
    info.whiteName = object.optString("whitenick", object.optString("whiteenname", ""));
    info.whiteRank = formatRank(object.optInt("whitedan", 0));
    info.chessid = object.optString("chessid", "");
    info.totalMoves = object.optInt("movenum", 0);
    int winner = object.optInt("winner", 0);
    int point = object.optInt("point", 0);
    int rule = object.optInt("rule", 0);
    String result;
    if (winner == 1 || winner == 2) {
      result =
          Lizzie.resourceBundle.getString(
              winner == 1 ? "FoxKifuDownload.black" : "FoxKifuDownload.white");
      int my = winner == 1 ? object.optInt("blackuid", 0) : object.optInt("whiteuid", 0);
      info.isWin = my == myUid;
      if (point < 0) {
        if (point == -1) result += Lizzie.resourceBundle.getString("FoxKifuDownload.winByRes");
        else if (point == -2)
          result += Lizzie.resourceBundle.getString("FoxKifuDownload.winByTime");
        else result += Lizzie.resourceBundle.getString("FoxKifuDownload.win");
      } else {
        String unit = "";
        if (rule == 1) unit = Lizzie.resourceBundle.getString("FoxKifuDownload.stones");
        if (rule == 0) unit = Lizzie.resourceBundle.getString("FoxKifuDownload.points");
        result +=
            (Lizzie.config.isChinese ? Lizzie.resourceBundle.getString("FoxKifuDownload.win") : "+")
                + point / 100f
                + unit;
      }
    } else {
      result = Lizzie.resourceBundle.getString("FoxKifuDownload.other");
    }
    info.result = result;
    return info;
  }

  private void copyKifu(KifuInfo from, KifuInfo to) {
    to.playTime = from.playTime;
    to.blackName = from.blackName;
    to.blackRank = from.blackRank;
    to.whiteName = from.whiteName;
    to.whiteRank = from.whiteRank;
    to.result = from.result;
    to.totalMoves = from.totalMoves;
    to.isWin = from.isWin;
  }

  private void rebuildTable() {
    if (table == null) return;
    Set<String> selected = new HashSet<>();
    for (int row : table.getSelectedRows()) {
      if (row >= 0 && row < table.getRowCount())
        selected.add(String.valueOf(table.getValueAt(row, 10)));
    }
    rows = new ArrayList<>();
    for (KifuInfo info : foxKifuInfos) {
      rows.add(
          new String[] {
            String.valueOf(info.index),
            info.playTime,
            info.blackName,
            info.blackRank,
            info.whiteName,
            info.whiteRank,
            info.result,
            String.valueOf(info.totalMoves),
            "",
            String.valueOf(info.isWin),
            info.chessid
          });
    }
    tabNumber = Math.max(1, (int) Math.ceil(rows.size() / (double) numbersPerTab));
    curTabNumber = Math.max(1, Math.min(curTabNumber, tabNumber));
    DefaultTableModel newModel = new DefaultTableModel();
    newModel.addColumn(Lizzie.resourceBundle.getString("FoxKifuDownload.column.index"));
    newModel.addColumn(Lizzie.resourceBundle.getString("FoxKifuDownload.column.time"));
    newModel.addColumn(Lizzie.resourceBundle.getString("FoxKifuDownload.column.black"));
    newModel.addColumn(Lizzie.resourceBundle.getString("FoxKifuDownload.column.rank"));
    newModel.addColumn(Lizzie.resourceBundle.getString("FoxKifuDownload.column.white"));
    newModel.addColumn(Lizzie.resourceBundle.getString("FoxKifuDownload.column.rank"));
    newModel.addColumn(Lizzie.resourceBundle.getString("FoxKifuDownload.column.result"));
    newModel.addColumn(Lizzie.resourceBundle.getString("FoxKifuDownload.column.moves"));
    newModel.addColumn(Lizzie.resourceBundle.getString("FoxKifuDownload.column.open"));
    newModel.addColumn("");
    newModel.addColumn("");
    int start = (curTabNumber - 1) * numbersPerTab;
    for (int i = start; i < start + numbersPerTab && i < rows.size(); i++) {
      newModel.addRow(rows.get(i));
    }
    model = newModel;
    table.setModel(model);
    table.getColumnModel().getColumn(0).setPreferredWidth(25);
    table.getColumnModel().getColumn(1).setPreferredWidth(190);
    table.getColumnModel().getColumn(2).setPreferredWidth(100);
    table.getColumnModel().getColumn(3).setPreferredWidth(40);
    table.getColumnModel().getColumn(4).setPreferredWidth(100);
    table.getColumnModel().getColumn(5).setPreferredWidth(35);
    table.getColumnModel().getColumn(6).setPreferredWidth(100);
    table.getColumnModel().getColumn(7).setPreferredWidth(50);
    table.getColumnModel().getColumn(8).setPreferredWidth(35);
    table.getColumnModel().getColumn(8).setCellEditor(new MyButtonOpenFoxKifuEditor());
    table.getColumnModel().getColumn(8).setCellRenderer(new MyButtonOpenFoxKifu());
    table.getColumnModel().getColumn(9).setPreferredWidth(0);
    table.getColumnModel().getColumn(10).setPreferredWidth(0);
    hideColumn(9);
    hideColumn(10);
    for (int row = 0; row < table.getRowCount(); row++) {
      if (selected.contains(String.valueOf(table.getValueAt(row, 10)))) {
        table.addRowSelectionInterval(row, row);
      }
    }
    table.revalidate();
    setLblTab(curTabNumber);
    if (lblStatus != null) {
      lblStatus.setText(
          Lizzie.resourceBundle.getString("FoxKifuDownload.total")
              + foxKifuInfos.size()
              + (fullHistoryMode
                  ? Lizzie.resourceBundle.getString("FoxKifuDownload.fullHistorySuffix")
                  : ""));
    }
  }

  class UserInfo {
    int blackUid;
    String blackNickName;
    String blackUserName;
    int whiteUid;
    String whiteNickName;
    String whiteUserName;
  }

  public void receiveResult(String string) {
    // TODO Auto-generated method stub
    try {
      JSONObject jsonOjbect = new JSONObject(string);
      if (jsonOjbect.has("progress")) {
        if (lblStatus != null) lblStatus.setText(jsonOjbect.optString("progress"));
        return;
      }
      if (jsonOjbect.has("error")) {
        isSearching = false;
        if (lblStatus != null) lblStatus.setText(jsonOjbect.optString("error"));
        Utils.showMsg(
            Lizzie.resourceBundle.getString("FoxKifuDownload.getKifuFailed")
                + jsonOjbect.optString("error"),
            this);
        return;
      }
      if (jsonOjbect.has("full")) fullHistoryMode = jsonOjbect.optBoolean("full", false);
      if (jsonOjbect.has("myuid")) myUid = jsonOjbect.optInt("myuid", myUid);
      if (myUid == 0) {
        if (jsonOjbect.has("chesslist")) {
          JSONArray jsonArray = jsonOjbect.getJSONArray("chesslist");
          for (int i = 0; i < jsonArray.length(); i++) {
            JSONObject jsonObject = jsonArray.getJSONObject(i);
            UserInfo userInfo = new UserInfo();
            userInfo.blackNickName = jsonObject.optString("blacknick", "");
            userInfo.blackUserName = jsonObject.optString("blackenname", "");
            userInfo.blackUid = jsonObject.optInt("blackuid", 0);

            userInfo.whiteNickName = jsonObject.optString("whitenick", "");
            userInfo.whiteUserName = jsonObject.optString("whiteenname", "");
            userInfo.whiteUid = jsonObject.optInt("whiteuid", 0);
            if (userInfo.blackNickName.trim().equalsIgnoreCase(Lizzie.config.lastFoxName)
                || userInfo.blackUserName.trim().equalsIgnoreCase(Lizzie.config.lastFoxName)) {
              myUid = userInfo.blackUid;
              break;
            } else if (userInfo.whiteNickName.trim().equalsIgnoreCase(Lizzie.config.lastFoxName)
                || userInfo.whiteUserName.trim().equalsIgnoreCase(Lizzie.config.lastFoxName)) {
              myUid = userInfo.whiteUid;
              break;
            }
          }
        }
      }
      if (jsonOjbect.has("reset")
          || jsonOjbect.has("append")
          || jsonOjbect.has("updates")
          || jsonOjbect.has("done")) {
        handleStreamEvent(jsonOjbect);
        return;
      }
      if (jsonOjbect.has("chesslist")) {
        isSearching = false;
        JSONArray jsonArray = jsonOjbect.getJSONArray("chesslist");
        int oldRows = foxKifuInfos.size();
        if (jsonArray.length() == 0) {
          if (oldRows > 0) {
            isComplete = true;
            isSecondTimeReqEmpty = true;
            isRequestEmpty = true;
          } else Utils.showMsg(Lizzie.resourceBundle.getString("FoxKifuDownload.noKifu"), this);
        }
        isComplete = fullHistoryMode || jsonArray.length() < 100;
        for (int i = 0; i < jsonArray.length(); i++) {
          JSONObject jsonObject = jsonArray.getJSONObject(i);
          KifuInfo kifuInfo = new KifuInfo();
          kifuInfo.index = i + 1;
          kifuInfo.playTime = jsonObject.optString("starttime", "");
          kifuInfo.blackName =
              jsonObject.optString("blacknick", jsonObject.optString("blackenname", ""));
          kifuInfo.blackRank = formatRank(jsonObject.optInt("blackdan", 0));
          kifuInfo.whiteName =
              jsonObject.optString("whitenick", jsonObject.optString("whiteenname", ""));
          kifuInfo.whiteRank = formatRank(jsonObject.optInt("whitedan", 0));
          kifuInfo.chessid = jsonObject.optString("chessid", "");
          kifuInfo.totalMoves = jsonObject.optInt("movenum", 0);
          String result = "";
          int winner = jsonObject.optInt("winner", 0);
          int point = jsonObject.optInt("point", 0);
          int rule = jsonObject.optInt("rule", 0);
          if (winner == 1 || winner == 2) {
            if (winner == 1) {
              result = Lizzie.resourceBundle.getString("FoxKifuDownload.black");
              if (jsonObject.optInt("blackuid", 0) == myUid) kifuInfo.isWin = true;
              else kifuInfo.isWin = false;
            } else {
              result = Lizzie.resourceBundle.getString("FoxKifuDownload.white");
              if (jsonObject.optInt("whiteuid", 0) == myUid) kifuInfo.isWin = true;
              else kifuInfo.isWin = false;
            }
            if (point < 0) {
              if (point == -1)
                result += Lizzie.resourceBundle.getString("FoxKifuDownload.winByRes");
              else if (point == -2)
                result += Lizzie.resourceBundle.getString("FoxKifuDownload.winByTime");
              else result += Lizzie.resourceBundle.getString("FoxKifuDownload.win");
            } else {
              String unit = "";
              if (rule == 1) unit = Lizzie.resourceBundle.getString("FoxKifuDownload.stones");
              if (rule == 0) unit = Lizzie.resourceBundle.getString("FoxKifuDownload.points");
              result +=
                  (Lizzie.config.isChinese
                          ? Lizzie.resourceBundle.getString("FoxKifuDownload.win")
                          : "+")
                      + point / 100f
                      + unit;
            }
          } else result = Lizzie.resourceBundle.getString("FoxKifuDownload.other");
          kifuInfo.result = result;
          foxKifuInfos.add(kifuInfo);
        }
        rows = new ArrayList<String[]>();
        if (foxKifuInfos.size() >= 1) {
          for (int i = 0; i < foxKifuInfos.size(); i++) {
            KifuInfo info = foxKifuInfos.get(i);
            String[] rowParams = {
              String.valueOf(info.index),
              info.playTime,
              info.blackName,
              info.blackRank,
              info.whiteName,
              info.whiteRank,
              info.result,
              String.valueOf(info.totalMoves),
              "",
              String.valueOf(info.isWin),
              info.chessid
            };
            rows.add(rowParams);
          }
          tabNumber = (int) Math.ceil(rows.size() / (double) numbersPerTab);
          if (oldRows <= 0) {
            model = new DefaultTableModel();
            model.addColumn(Lizzie.resourceBundle.getString("FoxKifuDownload.column.index"));
            model.addColumn(Lizzie.resourceBundle.getString("FoxKifuDownload.column.time"));
            model.addColumn(Lizzie.resourceBundle.getString("FoxKifuDownload.column.black"));
            model.addColumn(Lizzie.resourceBundle.getString("FoxKifuDownload.column.rank"));
            model.addColumn(Lizzie.resourceBundle.getString("FoxKifuDownload.column.white"));
            model.addColumn(Lizzie.resourceBundle.getString("FoxKifuDownload.column.rank"));
            model.addColumn(Lizzie.resourceBundle.getString("FoxKifuDownload.column.result"));
            model.addColumn(Lizzie.resourceBundle.getString("FoxKifuDownload.column.moves"));
            model.addColumn(Lizzie.resourceBundle.getString("FoxKifuDownload.column.open"));
            model.addColumn("");
            model.addColumn("");

            for (int i = 0; i < numbersPerTab && i < rows.size(); i++) {
              model.addRow(rows.get(i));
            }

            if (model.getRowCount() > 0) {
              table.setModel(model);
              table.getColumnModel().getColumn(0).setPreferredWidth(25);
              table.getColumnModel().getColumn(1).setPreferredWidth(190);
              table.getColumnModel().getColumn(2).setPreferredWidth(100);
              table.getColumnModel().getColumn(3).setPreferredWidth(40);
              table.getColumnModel().getColumn(4).setPreferredWidth(100);
              table.getColumnModel().getColumn(5).setPreferredWidth(35);
              table.getColumnModel().getColumn(6).setPreferredWidth(100);
              table.getColumnModel().getColumn(7).setPreferredWidth(50);
              table.getColumnModel().getColumn(8).setPreferredWidth(35);
              table.getColumnModel().getColumn(8).setCellEditor(new MyButtonOpenFoxKifuEditor());
              table.getColumnModel().getColumn(8).setCellRenderer(new MyButtonOpenFoxKifu());
              table.getColumnModel().getColumn(9).setPreferredWidth(0);
              table.getColumnModel().getColumn(10).setPreferredWidth(0);
              hideColumn(9);
              hideColumn(10);
              table.revalidate();
              if (Lizzie.config.isFrameFontSmall() && rows.size() >= 25) {
                scrollPane.setPreferredSize(
                    new Dimension(
                        scrollPane.getWidth(),
                        table.getTableHeader().getHeight() + Config.menuHeight * 25 + 2));
                pack();
              }
            }
            curTabNumber = 1;
          }
          setLblTab(curTabNumber);
          if (lblStatus != null)
            lblStatus.setText(
                Lizzie.resourceBundle.getString("FoxKifuDownload.total")
                    + foxKifuInfos.size()
                    + (fullHistoryMode
                        ? Lizzie.resourceBundle.getString("FoxKifuDownload.fullHistorySuffix")
                        : ""));
        }
      }
      if (jsonOjbect.has("chess")) {
        {
          String kifu = jsonOjbect.getString("chess");
          boolean oriReadKomi = Lizzie.config.readKomi;
          Lizzie.config.readKomi = false;
          SGFParser.loadFromString(kifu);
          Lizzie.board.setMovelistAll();
          if (Lizzie.leelaz.isPondering()) Lizzie.leelaz.ponder();
          Lizzie.frame.refresh();
          Lizzie.config.readKomi = oriReadKomi;
          if (Lizzie.config.foxAfterGet == 0) setExtendedState(JFrame.ICONIFIED);
          else if (Lizzie.config.foxAfterGet == 1) setVisible(false);
        }
      }
    } catch (JSONException e1) {
      // TODO Auto-generated catch block
      e1.printStackTrace();
      Utils.showMsg(
          Lizzie.resourceBundle.getString("FoxKifuDownload.getKifuFailed") + string, this);
      isSearching = false;
    }
  }

  private void setLblTab(int i) {
    // TODO Auto-generated method stub
    lblTab.setText(i + "/" + tabNumber + (this.isComplete ? "" : "..."));
  }

  private void hideColumn(int i) {
    // TODO Auto-generated method stub
    table.getColumnModel().getColumn(i).setWidth(0);
    table.getColumnModel().getColumn(i).setMaxWidth(0);
    table.getColumnModel().getColumn(i).setMinWidth(0);
    table.getTableHeader().getColumnModel().getColumn(i).setMaxWidth(0);
    table.getTableHeader().getColumnModel().getColumn(i).setMinWidth(0);
  }
}

class ColorTableCellRenderer extends DefaultTableCellRenderer {

  public Component getTableCellRendererComponent(
      JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
    setHorizontalAlignment(CENTER);
    if (table.getValueAt(row, 9).toString().equals("true")) {
      setForeground(Color.RED);
    } else setForeground(Color.GRAY);
    return super.getTableCellRendererComponent(table, value, isSelected, false, row, column);
  }
}

class MyButtonOpenFoxKifu implements TableCellRenderer {
  private JPanel panel;
  private JButton button;

  public MyButtonOpenFoxKifu() {
    initButton();
    initPanel();
    panel.add(button, BorderLayout.CENTER);
  }

  private void initButton() {
    button = new JFontButton();
    button.setMargin(new Insets(0, 0, 0, 0));
  }

  private void initPanel() {
    panel = new JPanel();
    panel.setLayout(new BorderLayout());
  }

  public Component getTableCellRendererComponent(
      JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
    button.setText(Lizzie.resourceBundle.getString("FoxKifuDownload.column.open"));
    return panel;
  }
}

class MyButtonOpenFoxKifuEditor extends AbstractCellEditor implements TableCellEditor {
  private JPanel panel;
  private JButton button;
  private String chessid = "";

  public MyButtonOpenFoxKifuEditor() {
    initButton();
    initPanel();
    panel.add(this.button, BorderLayout.CENTER);
  }

  private void initButton() {
    button = new JFontButton();
    button.setMargin(new Insets(0, 0, 0, 0));
    button.addActionListener(
        new ActionListener() {
          public void actionPerformed(ActionEvent e) {
            Lizzie.frame.foxKifuDownload.foxReq.sendCommand("chessid " + chessid);
          }
        });
  }

  private void initPanel() {
    panel = new JPanel();
    panel.setLayout(new BorderLayout());
  }

  @Override
  public Component getTableCellEditorComponent(
      JTable table, Object value, boolean isSelected, int row, int column) {
    button.setText(Lizzie.resourceBundle.getString("PrivateKifuSearch.open"));
    chessid = table.getValueAt(row, 10).toString();
    return panel;
  }

  @Override
  public Object getCellEditorValue() {
    // TODO Auto-generated method stub
    return null;
  }
}

class KifuInfo {
  int index;
  String playTime;
  String blackName;
  String blackRank;
  String whiteName;
  String whiteRank;
  String result;
  int totalMoves;
  String chessid;
  boolean isWin;
}
