import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BotLauncher {
    private JFrame frame;
    private CardLayout cardLayout;
    private JPanel mainPanel;

    private JTextField folderField;
    private JTextArea terminalArea;
    private JTextField inputField;
    private JButton restartBtn;
    private Process botProcess;
    private File currentBotDir;

    private final String NODE_VERSION = "24.14.0";

    private File currentConfigDir;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new BotLauncher().createAndShowGUI());
    }

    private void createAndShowGUI() {
        frame = new JFrame("Mineflayer Bot Launcher");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(880, 500);
        frame.setLocationRelativeTo(null);

        cardLayout = new CardLayout();
        mainPanel = new JPanel(cardLayout);

        mainPanel.add(createSetupPanel(), "setup");
        mainPanel.add(createTerminalPanel(), "terminal");

        frame.add(mainPanel);
        cardLayout.show(mainPanel, "setup");
        frame.setVisible(true);
    }

    private JPanel createSetupPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(12, 12, 12, 12);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JLabel title = new JLabel("Mineflayer Bot Launcher - 没手不行", SwingConstants.CENTER);
        title.setFont(new Font("微软雅黑", Font.BOLD, 22));
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 3;
        panel.add(title, gbc);

        gbc.gridy = 1;
        panel.add(new JLabel("支持 Windows / macOS（x64 & arm64）"), gbc);

        gbc.gridy = 2; gbc.gridwidth = 1;
        panel.add(new JLabel("安装目录："), gbc);

        folderField = new JTextField(System.getProperty("user.home") + File.separator + "MinecraftBot", 50);
        gbc.gridx = 1;
        panel.add(folderField, gbc);

        JButton browse = new JButton("浏览");
        gbc.gridx = 2;
        panel.add(browse, gbc);

        // 按钮们
        JButton oneClickBtn = new JButton("自动安装 Nodejs 等依赖");
        oneClickBtn.setFont(new Font("微软雅黑", Font.PLAIN, 13));
        oneClickBtn.setBackground(new Color(0, 180, 0));
        oneClickBtn.setForeground(Color.BLACK);
        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 3;
        panel.add(oneClickBtn, gbc);

        JButton configBtn = new JButton("配置 Bot IP 账号 密码等");
        configBtn.setFont(new Font("微软雅黑", Font.PLAIN, 13));
        configBtn.setBackground(new Color(0, 120, 255));
        configBtn.setForeground(Color.BLACK);
        gbc.gridy = 4;
        panel.add(configBtn, gbc);

        JButton directBtn = new JButton("安装完毕？点击进入终端");
        gbc.gridy = 5;
        panel.add(directBtn, gbc);

        browse.addActionListener(e -> browseFolder());
        oneClickBtn.addActionListener(e -> startWizard());
        configBtn.addActionListener(e -> showConfigDialog());   // 新增配置功能
        directBtn.addActionListener(e -> directLaunchTerminal());

        return panel;
    }

    private JPanel createTerminalPanel() {
        JPanel panel = new JPanel(new BorderLayout());

        terminalArea = new JTextArea();
        terminalArea.setBackground(new Color(0, 0, 0));
        terminalArea.setForeground(new Color(0, 255, 120));
        terminalArea.setFont(new Font("Monospaced", Font.PLAIN, 14));
        terminalArea.setEditable(false);
        terminalArea.setMargin(new Insets(10, 10, 10, 10));

        JScrollPane scroll = new JScrollPane(terminalArea);

        inputField = new JTextField();
        inputField.setFont(new Font("Monospaced", Font.PLAIN, 14));
        inputField.setBackground(new Color(25, 25, 25));
        inputField.setForeground(Color.WHITE);
        inputField.addActionListener(e -> sendCommand());

        //终端输入
        JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(new JLabel(" 输入命令后按回车（具体指令看文档）"), BorderLayout.WEST);
        bottom.add(inputField, BorderLayout.CENTER);

        //终端按钮
        JPanel btnPanel = new JPanel();
        restartBtn = new JButton("重启 Bot");
        JButton stopBtn = new JButton("停止 Bot");
        JButton clearBtn = new JButton("清屏");
        btnPanel.add(restartBtn);
        btnPanel.add(stopBtn);
        btnPanel.add(clearBtn);

        panel.add(scroll, BorderLayout.CENTER);
        panel.add(bottom, BorderLayout.SOUTH);
        panel.add(btnPanel, BorderLayout.NORTH);

        restartBtn.addActionListener(e -> restartBot());
        stopBtn.addActionListener(e -> stopBot());
        clearBtn.addActionListener(e -> terminalArea.setText(""));

        return panel;
    }

    private void browseFolder() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
            folderField.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    private void showConfigDialog() {
        String path = folderField.getText().trim();
        if (path.isEmpty()) {
            JOptionPane.showMessageDialog(frame, "请先选择安装目录！");
            return;
        }
        File dir = new File(path);
        currentConfigDir = dir;

        //读botjs
        BotConfig config = readConfig(new File(dir, "bot.js"));

        JDialog dialog = new JDialog(frame, "Bot 配置 - 编辑后点击保存", true);
        dialog.setSize(700, 420);
        dialog.setLocationRelativeTo(frame);
        dialog.setLayout(new GridBagLayout());

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0;
        dialog.add(new JLabel("服务器 IP："), gbc);

        JTextField hostField = new JTextField(config.host);

        //ai写的别介意
        gbc.gridx = 1;
        gbc.weightx = 1.0;   // ⭐ 关键！让这一列占剩余宽度
        gbc.fill = GridBagConstraints.HORIZONTAL;  // ⭐ 横向填充
        dialog.add(hostField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0;
        dialog.add(new JLabel("服务器端口："), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        JTextField portField = new JTextField(String.valueOf(config.port));
        dialog.add(portField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.weightx = 0;
        dialog.add(new JLabel("玩家名字："), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        JTextField userField = new JTextField(config.username);
        dialog.add(userField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.weightx = 0;
        dialog.add(new JLabel("登录密码："), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        JPasswordField passField = new JPasswordField(config.password);
        dialog.add(passField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.weightx = 0;
        dialog.add(new JLabel("登录延迟 (毫秒)："), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        JTextField delayField = new JTextField(String.valueOf(config.loginDelay));
        dialog.add(delayField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 5;
        gbc.weightx = 0;
        dialog.add(new JLabel("Minecraft 版本（1.21/auto自动）："), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        JTextField versionField = new JTextField(config.version);
        dialog.add(versionField, gbc);

        JButton saveBtn = new JButton("保存配置并写入");
        saveBtn.setBackground(new Color(0, 150, 0));
        saveBtn.setForeground(Color.BLACK);
        gbc.gridx = 0; gbc.gridy = 6; gbc.gridwidth = 2;
        dialog.add(saveBtn, gbc);

        // 取消按钮
        JButton cancelBtn = new JButton("取消");
        gbc.gridy = 7;
        dialog.add(cancelBtn, gbc);

        saveBtn.addActionListener(e -> {
            try {
                BotConfig newConfig = new BotConfig();
                newConfig.host = hostField.getText().trim();
                newConfig.port = Integer.parseInt(portField.getText().trim());
                newConfig.username = userField.getText().trim();
                newConfig.password = new String(passField.getPassword());
                newConfig.loginDelay = Integer.parseInt(delayField.getText().trim());
                newConfig.version = versionField.getText().trim();

                writeConfig(new File(currentConfigDir, "bot.js"), newConfig);
                JOptionPane.showMessageDialog(dialog, "配置已成功保存到 bot.js！");
                dialog.dispose();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(dialog, "保存失败：" + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
            }
        });

        cancelBtn.addActionListener(e -> dialog.dispose());

        dialog.setVisible(true);
    }

    //配置结构
    private static class BotConfig {
        String host = "127.0.0.1";
        int port = 25565;
        String username = "7891";
        String password = "277879113";
        int loginDelay = 3000;
        String version = "auto";
    }

    private BotConfig readConfig(File botJs) {
        BotConfig cfg = new BotConfig();
        if (!botJs.exists()) return cfg;

        try {
            String content = new String(Files.readAllBytes(botJs.toPath()), StandardCharsets.UTF_8);

            cfg.host = extractValue(content, "host:\\s*'([^']*)'");
            String portStr = extractValue(content, "port:\\s*(\\d+)");
            if (!portStr.isEmpty()) cfg.port = Integer.parseInt(portStr);

            cfg.username = extractValue(content, "username:\\s*'([^']*)'");
            cfg.password = extractValue(content, "password:\\s*'([^']*)'");

            String delayStr = extractValue(content, "loginDelay:\\s*(\\d+)");
            if (!delayStr.isEmpty()) cfg.loginDelay = Integer.parseInt(delayStr);

            cfg.version = extractValue(content, "version:\\s*'([^']*)'");

        } catch (Exception ignored) {
        }
        return cfg;
    }

    private String extractValue(String content, String regex) {
        Pattern p = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(content);
        return m.find() ? m.group(1) : "";
    }

    private void writeConfig(File botJs, BotConfig cfg) throws IOException {
        if (!botJs.exists()) {
            //如果botjs不存在 创建一个最小的带配置的版本
            //我也不知道为啥，ai让我这么干的
            String minimal = "const mineflayer = require('mineflayer')\n" +
                    "const { pathfinder, Movements, goals } = require('mineflayer-pathfinder')\n" +
                    "const mcDataLoader = require('minecraft-data')\n" +
                    "const readline = require('readline')\n" +
                    "const { Vec3 } = require('vec3')\n\n" +
                    "const CONFIG = {\n" +
                    "  host: '" + cfg.host + "', //IP放这里\n" +
                    "  port: " + cfg.port + ", //端口放这里\n" +
                    "  username: '" + cfg.username + "', //玩家名字\n" +
                    "  password: '" + cfg.password + "', //密码\n" +
                    "  version: '" + cfg.version + "', // 指定 Minecraft 客户端版本，例如 '1.21.1'、'1.8.9' 或 'auto'（自动检测）\n" +
                    "  loginDelay: " + cfg.loginDelay + ", //输入延迟\n\n" +
                    "  stuckCheckInterval: 500,\n" +
                    "  stuckDistance: 0.03,\n" +
                    "  jumpAttemptsBeforeReroute: 3,\n" +
                    "  jumpHoldTicks: 40,\n" +
                    "  maxStuckLevel: 3,\n" +
                    "  maxRerouteRadius: 256\n" +
                    "}\n\n" +
                    "//警告⚠️这不是完整Bot脚本，需要重新安装环境，配置只是防止主程序报错炸掉\n";
            Files.write(botJs.toPath(), minimal.getBytes(StandardCharsets.UTF_8));
            return;
        }

        String content = new String(Files.readAllBytes(botJs.toPath()), StandardCharsets.UTF_8);

        String newConfigBlock = "const CONFIG = {\n" +
                "  host: '" + cfg.host + "', //IP放这里\n" +
                "  port: " + cfg.port + ", //端口放这里\n" +
                "  username: '" + cfg.username + "', //玩家名字\n" +
                "  password: '" + cfg.password + "', //密码\n" +
                "  version: '" + cfg.version + "', // 指定 Minecraft 客户端版本，例如 '1.21.1'、'1.8.9' 或 'auto'（自动检测）\n" +
                "  loginDelay: " + cfg.loginDelay + ", //输入延迟\n\n" +
                "  stuckCheckInterval: 500, //卡多少秒判定卡住\n" +
                "  stuckDistance: 0.03, //不需要知道\n\n" +
                "  jumpAttemptsBeforeReroute: 3, //跳跃尝试多少次\n" +
                "  jumpHoldTicks: 40, //没必要知道，是这样就对了\n\n" +
                "  maxStuckLevel: 3, //最高卡死等级\n" +
                "  maxRerouteRadius: 256 //最大寻路范围\n" +
                "}";

        content = content.replaceFirst("(?s)const CONFIG\\s*=\\s*\\{.*?\\}", newConfigBlock);

        Files.write(botJs.toPath(), content.getBytes(StandardCharsets.UTF_8));
    }

    private void sendCommand() {
        String cmd = inputField.getText().trim();
        if (cmd.isEmpty() || botProcess == null || !botProcess.isAlive()) return;
        try {
            OutputStream os = botProcess.getOutputStream();
            os.write((cmd + "\n").getBytes(StandardCharsets.UTF_8));
            os.flush();
            terminalArea.append("> " + cmd + "\n");
            terminalArea.setCaretPosition(terminalArea.getDocument().getLength());
        } catch (Exception ex) {
            terminalArea.append("发送失败: " + ex.getMessage() + "\n");
        }
        inputField.setText("");
    }

    private void restartBot() {
        if (currentBotDir == null) return;
        terminalArea.append("\n=== 重启 Bot ===\n");
        startBotProcess(currentBotDir);
    }

    private void stopBot() {
        if (botProcess != null && botProcess.isAlive()) {
            botProcess.destroyForcibly();
            terminalArea.append("\n=== Bot 已停止 ===\n");
        } else {
            terminalArea.append("\nBot 未运行。\n");
        }
    }

    private void startWizard() {
        String path = folderField.getText().trim();
        if (path.isEmpty()) {
            JOptionPane.showMessageDialog(frame, "请选择安装目录！");
            return;
        }
        File dir = new File(path);
        dir.mkdirs();

        try {
            copyBotJs(dir);
            showInstallProgressDialog(dir);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(frame, "准备失败: " + ex.getMessage());
        }
    }

    private void directLaunchTerminal() {
        String path = folderField.getText().trim();
        File dir = new File(path);
        File nodeExec = new File(dir, isWindows() ? "node/node.exe" : "node/bin/node");
        if (!nodeExec.exists()) {
            JOptionPane.showMessageDialog(frame, "Node.js 未安装，请先点击“自动安装”！");
            return;
        }
        currentBotDir = dir;
        cardLayout.show(mainPanel, "terminal");
        startBotProcess(dir);
    }

    private void showInstallProgressDialog(File dir) {
        JDialog dialog = new JDialog(frame, "正在自动安装 请稍等", true);
        dialog.setSize(800, 550);
        dialog.setLocationRelativeTo(frame);

        JTextArea logArea = new JTextArea();
        logArea.setBackground(Color.BLACK);
        logArea.setForeground(Color.CYAN);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 13));
        logArea.setEditable(false);
        dialog.add(new JScrollPane(logArea));

        new Thread(() -> {
            try {
                File setupFile = createSetupScript(dir);
                String[] cmd = isWindows()
                        ? new String[]{"cmd.exe", "/c", setupFile.getName()}
                        : new String[]{"/bin/sh", setupFile.getName()};

                ProcessBuilder pb = new ProcessBuilder(cmd);
                pb.directory(dir);
                pb.redirectErrorStream(true);

                Process p = pb.start();
                BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8));

                String line;
                while ((line = br.readLine()) != null) {
                    String finalLine = line;
                    SwingUtilities.invokeLater(() -> {
                        logArea.append(finalLine + "\n");
                        logArea.setCaretPosition(logArea.getDocument().getLength());
                    });
                }

                int exitCode = p.waitFor();
                SwingUtilities.invokeLater(() -> {
                    if (exitCode == 0) {
                        logArea.append("\n\n安装完成！正在启动 Bot...\n");
                        dialog.dispose();
                        currentBotDir = dir;
                        cardLayout.show(mainPanel, "terminal");
                        startBotProcess(dir);
                    } else {
                        logArea.append("\n安装失败 (错误 " + exitCode + ")，请把上方日志截图发给三文猫.");
                    }
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> logArea.append("\n错误: " + ex.getMessage()));
            }
        }).start();

        dialog.setVisible(true);
    }

    private void startBotProcess(File dir) {
        if (botProcess != null && botProcess.isAlive()) botProcess.destroyForcibly();

        String nodePath = isWindows()
                ? new File(dir, "node/node.exe").getAbsolutePath()
                : new File(dir, "node/bin/node").getAbsolutePath();

        ProcessBuilder pb = new ProcessBuilder(nodePath, "bot.js");
        pb.directory(dir);
        pb.redirectErrorStream(true);

        try {
            botProcess = pb.start();

            new Thread(() -> {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(botProcess.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        String finalLine = line;
                        SwingUtilities.invokeLater(() -> {
                            terminalArea.append(finalLine + "\n");
                            terminalArea.setCaretPosition(terminalArea.getDocument().getLength());
                        });
                    }
                } catch (Exception ignored) {}
            }).start();

            new Thread(() -> {
                try {
                    int code = botProcess.waitFor();
                    SwingUtilities.invokeLater(() -> terminalArea.append("\n\nBot 已退出 (代码 " + code + ")，点击 重启 Bot 重新启动\n"));
                } catch (Exception ignored) {}
            }).start();

            terminalArea.append("Bot 已启动，等待 Minecraft 服务器连接...\n");
        } catch (Exception ex) {
            terminalArea.append("启动失败: " + ex.getMessage() + "\n");
        }
    }

    // ====================== 工具方法 ======================
    private boolean isWindows() {
        return System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");
    }

    private void copyBotJs(File dir) throws IOException {
        InputStream is = getClass().getResourceAsStream("/bot.js");
        if (is == null) is = new FileInputStream("bot.js");
        File target = new File(dir, "bot.js");
        try (FileOutputStream fos = new FileOutputStream(target);
             InputStream in = is) {
            byte[] buf = new byte[4096];
            int len;
            while ((len = in.read(buf)) > 0) fos.write(buf, 0, len);
        }
    }

    private File createSetupScript(File dir) throws IOException {
        String url = getNodeDownloadUrl();
        File f = new File(dir, isWindows() ? "setup.bat" : "setup.sh");
        String content;

        if (isWindows()) {
            String arch = getArch();
            content = "@echo off\r\n" +
                    "chcp 65001 >nul\r\n" +
                    "title Mineflayer Bot 一键安装\r\n" +
                    "cd /d \"%~dp0\"\r\n" +
                    "if exist node\\node.exe (\r\n" +
                    "  echo Node.js 已存在，跳过下载。\r\n" +
                    ") else (\r\n" +
                    "  echo 正在下载 Node.js...\r\n" +
                    "  powershell -NoProfile -ExecutionPolicy Bypass -Command \"Invoke-WebRequest -Uri '" + url + "' -OutFile 'node.zip'\"\r\n" +
                    "  echo 正在解压...\r\n" +
                    "  powershell -NoProfile -ExecutionPolicy Bypass -Command \"Expand-Archive -Path 'node.zip' -DestinationPath '.' -Force\"\r\n" +
                    "  ren \"node-v" + NODE_VERSION + "-win-" + arch + "\" node\r\n" +
                    "  del node.zip\r\n" +
                    ")\r\n" +
                    "echo 正在安装依赖...\r\n" +
                    "node\\npm.cmd install mineflayer mineflayer-pathfinder minecraft-data vec3 --no-audit --no-fund\r\n" +
                    "echo ========================================\r\n" +
                    "echo 一键安装完成！\r\n" +
                    "echo ========================================\r\n" +
                    "pause\r\n";
        } else {
            String arch = getArch();
            content = "#!/bin/bash\n" +
                    "cd \"$(dirname \"$0\")\"\n" +
                    "if [ -f \"node/bin/node\" ]; then\n" +
                    "  echo \"Node.js 已存在，跳过下载。\"\n" +
                    "else\n" +
                    "  echo \"正在下载 Node.js...\"\n" +
                    "  curl -L -# -o node.tar.gz \"" + url + "\"\n" +
                    "  echo \"正在解压...\"\n" +
                    "  tar -xzf node.tar.gz\n" +
                    "  DOWNLOADED=$(ls -d node-v* 2>/dev/null | head -n 1)\n" +
                    "  if [ -n \"$DOWNLOADED\" ]; then\n" +
                    "    mv \"$DOWNLOADED\" node\n" +
                    "  fi\n" +
                    "  rm -f node.tar.gz\n" +
                    "fi\n" +
                    "echo \"正在修复权限...\"\n" +
                    "chmod +x node/bin/* 2>/dev/null || true\n" +
                    "echo \"正在安装依赖...\"\n" +
                    "\"./node/bin/node\" \"./node/bin/npm\" install mineflayer mineflayer-pathfinder minecraft-data vec3 --no-audit --no-fund\n" +
                    "echo \"========================================\"\n" +
                    "echo \"一键安装完成！\"\n" +
                    "echo \"========================================\"\n" +
                    "read -p \"按回车继续...\"\n";
            f.setExecutable(true, false);
        }

        try (FileWriter w = new FileWriter(f)) { w.write(content); }
        return f;
    }

    private String getNodeDownloadUrl() {
        String base = "https://nodejs.org/dist/v" + NODE_VERSION + "/node-v" + NODE_VERSION + "-";
        String os = isWindows() ? "win" : "darwin";
        String arch = getArch();
        String ext = isWindows() ? ".zip" : ".tar.gz";
        return base + os + "-" + arch + ext;
    }

    private String getArch() {
        String a = System.getProperty("os.arch").toLowerCase(Locale.ROOT);
        return (a.contains("arm") || a.contains("aarch")) ? "arm64" : "x64";
    }
}
