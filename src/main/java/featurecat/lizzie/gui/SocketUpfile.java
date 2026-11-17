package featurecat.lizzie.gui;

import featurecat.lizzie.Lizzie;
import featurecat.lizzie.rules.SGFParser;
import featurecat.lizzie.util.Utils;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;

public class SocketUpfile {
  SMessage msg;
  ShareMessage smsg;
  String link = "";
  boolean recievedServer = false;
  Socket socket = null;

  public void SocketUpfile(
      String black,
      String white,
      String uploader,
      String label,
      String otherInfo,
      boolean isPublic,
      String bScore,
      String wScore,
      String allMove,
      String analyzedMove,
      boolean isBatch) {
    // TODO Auto-generated method stub

    BufferedReader br = null;
    PrintWriter pw = null;
    try {
      // 客户端socket指定服务器的地址和端口号121.36.229.204
      socket = new Socket("lizzieyzy.cn", 3085);
      // System.out.println("Socket=" + socket);
      // 同服务器原理一样
      br = new BufferedReader(new InputStreamReader(socket.getInputStream(), "utf-8"));
      pw =
          new PrintWriter(
              new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), "utf-8")));
      sendContent("SktINFOStart", pw);
      String playerB = black;
      if (playerB.equals("")) playerB = "black";
      String playerW = white;
      if (playerW.equals("")) playerW = "white";
      String info = "";
      if (isPublic)
        info =
            playerB
                + ">"
                + playerW
                + ">"
                + uploader
                + ">"
                + label
                + ">"
                + "true"
                + ">"
                + otherInfo
                + ">"
                + bScore
                + ">"
                + wScore
                + ">"
                + allMove
                + ">"
                + analyzedMove;
      else
        info =
            playerB
                + ">"
                + playerW
                + ">"
                + uploader
                + ">"
                + label
                + ">"
                + "false"
                + ">"
                + otherInfo
                + ">"
                + bScore
                + ">"
                + wScore
                + ">"
                + allMove
                + ">"
                + analyzedMove;
      Lizzie.board.getHistory().getGameInfo().setPlayerBlack(playerB);
      Lizzie.board.getHistory().getGameInfo().setPlayerWhite(playerW);
      sendContent(info, pw);
      // sendContent("SktINFOEnd",pw);
      pw.println("SktINFOEnd");
      pw.flush();
      LizzieFrame.isShareing = true;
      String sgfContent = SGFParser.saveToString(true);
      LizzieFrame.isShareing = false;
      sendContent("SktSGFStart", pw);

      sendContent(sgfContent, pw);

      // sendContent("SktSGFEnd",pw);
      pw.println("SktSGFEnd");
      pw.flush();
      pw.println("SktEND");
      pw.flush();
      //  sendContent("SktEND",pw);

      String str;
      boolean success = false;
      boolean errMsg = false;
      String err = "";

      Runnable runnable =
          new Runnable() {
            public void run() {
              try {
                Thread.sleep(15000);
                if (socket != null && !recievedServer)
                  try {
                    socket.close();
                    if (!isBatch) {
                      //  msg = new SMessage();
                      //  msg.setMessage(
                      //
                      // "连接超时...请重试或下载最新版Lizzie,链接:https://pan.baidu.com/s/1q615GHD62F92mNZbTYfcxA");
                      Utils.showMsg(Lizzie.resourceBundle.getString("Socket.connectFailed"));
                    }
                    //     msg.setVisible(true);
                  } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                  }
              } catch (InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
              }
            }
          };
      Thread th = new Thread(runnable);
      th.start();
      while ((str = br.readLine()) != null) {
        recievedServer = true;
        // System.out.println(str);
        // Lizzie.gtpConsole.addLine(str);
        if (str.startsWith("http")) {
          link = str;
          success = true;
          // SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm");
          // saveTxt(link, uploader, label, df.format(new Date()), otherInfo, playerB, playerW);
        }
        if (str.startsWith("error")) {
          errMsg = true;
          err = str;
        }
      }
      if (!isBatch) {
        if (success) {
          smsg = new ShareMessage();
          smsg.setMessage(link);
          smsg.setVisible(true);
        }
        if (errMsg) {
          msg = new SMessage();
          msg.setMessage(err);
          //   msg.setVisible(true);
        }
      }
    } catch (Exception e) {
      // e.printStackTrace();
    } finally {
      if (socket != null)
        try {
          // System.out.println("close......");
          br.close();
          pw.close();
          socket.close();
        } catch (IOException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        }
      else {
        if (!isBatch) Utils.showMsg(Lizzie.resourceBundle.getString("Socket.connectFailed"));
        //        System.out.println("连接失败...");
        //        Lizzie.gtpConsole.addLine("连接失败..." + "\n");
        //        if (!isBatch) {
        //          msg = new SMessage();
        //          msg.setMessage(
        //
        // "连接失败...请重试或下载最新版Lizzie,链接:https://pan.baidu.com/s/1q615GHD62F92mNZbTYfcxA");
        //   }
        //  msg.setVisible(true);
      }
    }
  }

  public void sendContent(String str, PrintWriter pw) {
    pw.println(Utils.doEncrypt(str));
    pw.flush();
  }
}
