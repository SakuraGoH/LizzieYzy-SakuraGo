# LizzieYzy-SakuraGo

A fork of [LizzieYzy](https://github.com/yzyray/lizzieyzy) focused on:

- direct Fox Weiqi (野狐围棋) AI-protocol support,
- independent multi-game engine matches,
- Fox kifu search/download integration,
- practical GUI, analysis and startup improvements.

The project remains a desktop Swing application and keeps the original LizzieYzy board, SGF, variation tree and engine-analysis features.

---

## Features

### Fox Direct (野狐协议直连)

- Listen on a configurable local TCP port; no hard-coded port.
- Separate **Fox Direct** window containing the port, start/stop and status.
- The Fox menu item is a check box item and reflects the running state.
- Parses `FARULE`, `FASTATUS`, `FAMOVE`, `FASKIP`, `FATIMELEFT`, `FARESULT` and `FASCORE`.
- Keeps the board in sync, forwards engine moves as `AFPLAY` / `AFSKIP` / `AFGIVEUP`.
- Manual board placement is locked during a Fox game; the prompt is localized.
- Reconnect replay de-duplicates repeated `FAMOVE` messages.
- Choose which loaded engine plays in double-engine mode.

### Engine matches

- Start several games at the same time; the count is user-configurable and cannot exceed the batch count.
- Every lane/game owns its own board, engine processes, command queue and GTP response loop, so commands never cross between games.
- Page through running games with the bottom **<局 / 局>** controls.
- **结束本局** stops only the game currently being viewed.
- Winrate curves are recorded independently for every lane, including while a lane is in the background.
- Batch/match results are written to the usual `EngineGames/<batch>/结果<timestamp>.txt` statistics file (wins, score, win rate, black/white wins, double-pass, max-move, referee, draw and aborted counts).
- Multi-game no longer writes per-lane startup/move GTP logs; only SGF and the final result file are produced.
- Referee engine: when a game ends with two passes, the configured referee engine replays the game and uses GTP `final_score` to decide black/white/draw.
- If an engine fails to start during a match, the game state is left automatically and the toolbar/menu/input are restored.
- Saving engine settings while a game or Fox Direct is running does **not** restart engines or interrupt games. Restart engines manually to apply changes.

### Command history

- Command lines are saved as timestamped history nodes in `commandHistory.json`.
- History is separated by channel:
  - main engine slot (`engine:0`, `engine:1`, ...)
  - analysis engine
  - estimate engine
  - referee engine
- Each engine's **历史** button only shows and edits its own channel.

### Kifu search and download

- **Sync → Fox kifu (野狐棋谱)** launches the bundled `FoxSgfsDownload-Pro.exe` as a separate desktop tool, in the same way `readboard.exe` is used.
- The exe is copied from the jar to `foxSgfsDownload/` on first use.
- Full Fox kifu history search and batch SGF download are handled by that tool.
- Existing Fox kifu query code also supports H5 latest games and official qipu search, with streaming display and metadata completion.

### Analysis UI

- **Display → KataGo related settings → Kata evaluation display → Show ownership on hover**:
  - hover any intersection (not only engine candidate points) to show the current KataGo ownership estimate;
  - displays the dominant side as e.g. `黑 60%` / `白 80%`;
  - uses the same ownership array, coordinate mapping and side-to-move convention as the transparent ownership squares.
- **Display → Suggestion info → Precision**: 0–3 decimal places for win rate and score lead. Precision is applied after the raw engine value, not after pre-rounding.
- **Help → About** includes links to KataGo releases and KataGo distributed training.
- **Help → Device information** detects CPU, cores, physical memory, GPU, VRAM and driver version (using `nvidia-smi`, then WMI/PowerShell fallback).
- Main window position and maximized/normal state are restored from the last exit.
- The main board window is brought to the foreground on startup.
- Startup defers non-critical GUI work (GTP console, clock helper, comments) and reuses the off-screen paint buffer for a faster first screen.

### Misc

- Capture-tsumego steps window can be skipped next time via a check box in the 死活题 window; the default is to show the steps every time.
- Dead code and large obsolete comment blocks were removed across the project.
- Background/foreground colors for Fox menu check items, input locks and controller state are kept in sync.

---

## Requirements

| Component | Version |
|-----------|---------|
| Java | 8 or newer |
| Maven | 3.6+ for building |
| KataGo | 1.10+ recommended |
| OS | Windows 10/11 (tested) |

The shaded jar already includes JNA for Windows native window handling.

---

## Fox Direct

1. Open **Sync → Fox Direct (协议直连)**.
2. Set the listen port in the Fox Direct window (default `6000`) and click **Start**.
3. In the Fox client, set the AI address to `127.0.0.1:<port>`.
4. Start a game on Fox. The board, engine and result reporting stay in sync.
5. Stop the listener from the same window, or close LizzieYzy.

The Fox menu item is checked while the listener is running.

---

## Engine matches

Open the engine-game settings from the engine-match panel:

- **Black/White engine**: select the two engines.
- **Simultaneous games**: number of lanes; must not exceed the batch game count.
- **Referee engine command line**: full command line including weights; used for `final_score` on double pass.
- **Batch games**: total games.

While a match runs:

- use the bottom paging buttons to switch lanes;
- **结束本局** aborts only the current lane;
- the global stop command still ends the whole match;
- pending old genmove responses are discarded by a per-lane generation counter.

---

## Fox kifu

Click **Sync → Fox kifu (野狐棋谱)**. The bundled Pro executable opens as its own window. It supports full history search and batch download, with concurrency, progress and skip-existing behavior.

If the exe is missing, LizzieYzy reports the expected path under `foxSgfsDownload/`.

---

## Building

```bash
mvn clean package -DskipTests
```

Output:

```text
target/lizzie-yzy2.5.3-shaded.jar
```

Copy the jar into the LizzieYzy distribution directory.

---

## Important source locations

| Area | Files |
|------|-------|
| Fox direct | `analysis/fox/FoxDirectController.java`, `FoxBridge.java`, `FoxClientServer.java`, `LizzieFoxHost.java`, `gui/FoxDirectWindow.java` |
| Engine matches | `analysis/EngineGameArena.java`, `EngineGameSession.java`, `EngineGameInfo.java`, `gui/EnginePkConfig.java` |
| Referee | `analysis/RefereeEngine.java` |
| Command history | `analysis/CommandHistory.java`, `gui/CommandHistoryDialog.java` |
| Fox kifu | `analysis/GetFoxRequest.java`, `gui/FoxKifuDownload.java`, `gui/FoxSgfsLauncher.java` |
| Window focus/state | `util/WindowFocusHelper.java`, `gui/LizzieFrame.java` |
| Device information | `gui/DeviceInfoDialog.java` |

---

## Configuration keys

| Key | Meaning |
|-----|---------|
| `fox-direct-port` | Fox Direct TCP port |
| `engine-pk-parallel-games` | Number of simultaneous engine games |
| `refereeEngineCommand` | Referee engine command line |
| `suggestion-info-precision` | Win rate/score precision (0–3) |
| `show-katago-ownership-on-hover` | Ownership tooltip on hover |
| `capture-tsume-go-skip-hint` | Skip the capture-tsumego steps window next time |

---

## Known limitations

- Fox protocol variants are not exhaustive; new or changed Fox commands may be ignored.
- Undo / clear-board menu items are not locked during Fox games; only direct board clicks are locked.
- Windows is the tested platform. JNA window foreground handling is Windows-specific.
- The bundled `FoxSgfsDownload-Pro.exe` is an external tool; its own network behavior and rate limits apply.
- Full qipu search can take a long time for players with a very large history.

---

## Safety notes

- The manual placement lock is intentional: an accidental click would desynchronize the local board and the Fox server.
- Use a dedicated Fox AI account; do not use a personal account for automated play.
- The bridge only uses the standard local Fox AI socket, not memory reading or packet injection.
- No Fox credentials are stored by the bridge.
- `FASCORE` comes from the engine's `final_score`; rules disagreements can produce different scores.

---

## Credits

- **LizzieYzy** — yzyray: <https://github.com/yzyray/lizzieyzy>
- **KataGo** — David J. Wu and contributors: <https://github.com/lightvector/KataGo>
- **KataGo releases**: <https://github.com/lightvector/KataGo/releases>
- **KataGo distributed training**: <https://katagotraining.org/>
- **Leela Zero**: <https://github.com/leela-zero/leela-zero>
- **SakuraGTP**: <https://github.com/SakuraGoH/SakuraGTP>
- **Original Lizzie**: <https://github.com/featurecat/lizzie>
- **Fox Weiqi**: <https://www.foxwq.com/>

---

## License

GNU General Public License v3.0, same as upstream LizzieYzy. See `LICENSE`.

The software is provided “as is”, without warranty. Use at your own risk.
