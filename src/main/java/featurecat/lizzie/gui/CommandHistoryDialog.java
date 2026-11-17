package featurecat.lizzie.gui;

import featurecat.lizzie.Lizzie;
import featurecat.lizzie.analysis.CommandHistory;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;
import javax.swing.DefaultListModel;
import javax.swing.JDialog;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;

/** 指定通道(某个引擎/分析引擎/裁判引擎)的命令行历史节点。 */
public class CommandHistoryDialog extends JDialog {
  private final DefaultListModel<CommandHistory.Node> model = new DefaultListModel<>();
  private final JList<CommandHistory.Node> list = new JList<>(model);
  private final Consumer<String> onUse;
  private final String channel;

  public CommandHistoryDialog(Window owner, String channel, String title, Consumer<String> onUse) {
    super(
        owner,
        (title == null || title.isEmpty()
            ? Lizzie.resourceBundle.getString("CommandHistory.title")
            : title),
        ModalityType.APPLICATION_MODAL);
    this.onUse = onUse;
    this.channel = channel;
    setDefaultCloseOperation(DISPOSE_ON_CLOSE);
    setLayout(new BorderLayout(0, 4));
    list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    list.setFont(new java.awt.Font(Lizzie.config.sysDefaultFontName, java.awt.Font.PLAIN, 12));
    list.addMouseListener(
        new MouseAdapter() {
          @Override
          public void mouseClicked(MouseEvent e) {
            if (e.getClickCount() == 2) useSelected();
          }
        });
    add(new JScrollPane(list), BorderLayout.CENTER);

    JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
    JFontButton use = new JFontButton(Lizzie.resourceBundle.getString("CommandHistory.use"));
    JFontButton delete = new JFontButton(Lizzie.resourceBundle.getString("CommandHistory.delete"));
    JFontButton clear = new JFontButton(Lizzie.resourceBundle.getString("CommandHistory.clear"));
    JFontButton close = new JFontButton(Lizzie.resourceBundle.getString("CommandHistory.close"));
    use.setMargin(new Insets(0, 6, 0, 6));
    delete.setMargin(new Insets(0, 6, 0, 6));
    clear.setMargin(new Insets(0, 6, 0, 6));
    close.setMargin(new Insets(0, 6, 0, 6));
    use.addActionListener(e -> useSelected());
    delete.addActionListener(
        e -> {
          int index = list.getSelectedIndex();
          if (index < 0) return;
          CommandHistory.remove(channel, index);
          reload();
        });
    clear.addActionListener(
        e -> {
          if (JOptionPane.showConfirmDialog(
                  this,
                  Lizzie.resourceBundle.getString("CommandHistory.clearConfirm"),
                  Lizzie.resourceBundle.getString("CommandHistory.title"),
                  JOptionPane.OK_CANCEL_OPTION)
              == JOptionPane.OK_OPTION) {
            CommandHistory.clear(channel);
            reload();
          }
        });
    close.addActionListener(e -> dispose());
    buttons.add(use);
    buttons.add(delete);
    buttons.add(clear);
    buttons.add(close);
    add(buttons, BorderLayout.SOUTH);
    reload();
    setSize(new Dimension(760, 420));
    setLocationRelativeTo(owner);
  }

  private void reload() {
    model.clear();
    for (CommandHistory.Node node : CommandHistory.getAll(channel)) model.addElement(node);
  }

  private void useSelected() {
    CommandHistory.Node node = list.getSelectedValue();
    if (node == null) return;
    if (onUse != null) onUse.accept(node.command);
    dispose();
  }
}
