package featurecat.lizzie.gui;

import featurecat.lizzie.Lizzie;
import featurecat.lizzie.analysis.fox.FoxDirectController;
import featurecat.lizzie.analysis.fox.FoxDirectOverlay;
import featurecat.lizzie.util.Utils;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.JDialog;

/** 野狐协议直连的独立设置窗口：监听端口 + 启动/停止 + 原棋盘选点。 */
public class FoxDirectWindow extends JDialog {
  private final JFontTextField txtPort = new JFontTextField();
  private final JFontLabel lblStatus = new JFontLabel();
  private final JFontButton btnToggle = new JFontButton();
  private final JFontButton btnClose =
      new JFontButton(Lizzie.resourceBundle.getString("FoxDirect.close"));
  private final JFontCheckBox chkShowOnBoard =
      new JFontCheckBox(Lizzie.resourceBundle.getString("FoxDirect.showOnBoard"));
  private final JFontButton btnCalibrate =
      new JFontButton(Lizzie.resourceBundle.getString("FoxDirect.calibrate"));

  public FoxDirectWindow(Window owner) {
    super(owner, Lizzie.resourceBundle.getString("FoxDirect.title"), ModalityType.MODELESS);
    setResizable(false);
    setAlwaysOnTop(Lizzie.frame != null && Lizzie.frame.isAlwaysOnTop());
    setLayout(null);
    setSize(420, 195);
    Lizzie.setFrameSize(this, 420, 195);
    setLocationRelativeTo(owner);

    JFontLabel lblPort = new JFontLabel(Lizzie.resourceBundle.getString("FoxDirect.port"));
    lblPort.setBounds(18, 18, 110, 24);
    add(lblPort);
    txtPort.setDocument(new IntDocument());
    txtPort.setBounds(132, 19, 80, 24);
    add(txtPort);
    JFontLabel lblPortHint = new JFontLabel("127.0.0.1:port");
    lblPortHint.setBounds(220, 18, 180, 24);
    add(lblPortHint);

    lblStatus.setBounds(18, 52, 380, 22);
    add(lblStatus);

    chkShowOnBoard.setBounds(18, 82, 250, 24);
    chkShowOnBoard.setSelected(Lizzie.config.foxDirectShowOnBoard);
    add(chkShowOnBoard);
    btnCalibrate.setMargin(new Insets(0, 4, 0, 4));
    btnCalibrate.setBounds(280, 80, 100, 26);
    btnCalibrate.setFocusable(false);
    add(btnCalibrate);

    btnToggle.setMargin(new Insets(0, 4, 0, 4));
    btnToggle.setBounds(150, 130, 120, 28);
    btnToggle.setFocusable(false);
    add(btnToggle);
    btnClose.setMargin(new Insets(0, 4, 0, 4));
    btnClose.setBounds(280, 130, 100, 28);
    btnClose.setFocusable(false);
    add(btnClose);

    txtPort.setText(String.valueOf(Lizzie.config.foxDirectPort));

    btnToggle.addActionListener(
        new ActionListener() {
          public void actionPerformed(ActionEvent e) {
            if (FoxDirectController.getInstance().isRunning()) {
              Lizzie.frame.stopFoxDirect();
              refreshState();
              return;
            }
            int port;
            try {
              port = Integer.parseInt(txtPort.getText().trim());
            } catch (NumberFormatException ex) {
              Utils.showMsg(Lizzie.resourceBundle.getString("FoxDirect.invalidPort"));
              return;
            }
            if (port <= 0 || port > 65535) {
              Utils.showMsg(Lizzie.resourceBundle.getString("FoxDirect.invalidPort"));
              return;
            }
            FoxDirectController.getInstance().setPort(port);
            Lizzie.frame.startFoxDirect();
            refreshState();
          }
        });

    btnClose.addActionListener(e -> dispose());

    chkShowOnBoard.addActionListener(
        e -> {
          Lizzie.config.foxDirectShowOnBoard = chkShowOnBoard.isSelected();
          Lizzie.config.uiConfig.put(
              "fox-direct-show-on-board", Lizzie.config.foxDirectShowOnBoard);
          FoxDirectOverlay.sync();
        });
    btnCalibrate.addActionListener(e -> BoardAreaSelector.open());
    addWindowListener(
        new WindowAdapter() {
          public void windowClosing(WindowEvent e) {
            dispose();
          }
        });
    refreshState();
  }

  public void refreshState() {
    FoxDirectController controller = FoxDirectController.getInstance();
    boolean running = controller.isRunning();
    txtPort.setEnabled(!running);
    if (running) {
      lblStatus.setText(
          Lizzie.resourceBundle.getString("FoxDirect.running") + " " + controller.getPort());
      btnToggle.setText(Lizzie.resourceBundle.getString("FoxDirect.stop"));
    } else {
      lblStatus.setText(Lizzie.resourceBundle.getString("FoxDirect.stopped"));
      btnToggle.setText(Lizzie.resourceBundle.getString("FoxDirect.start"));
    }
    FoxDirectOverlay.sync();
  }
}
