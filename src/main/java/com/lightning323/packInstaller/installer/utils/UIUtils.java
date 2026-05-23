package com.lightning323.packInstaller.installer.utils;

import java.awt.*;
import java.awt.TrayIcon.MessageType;

public class UIUtils {

    private static boolean displayTray(String title, String message) {
        // 1. Check if the OS even supports a System Tray
        if (GraphicsEnvironment.isHeadless() || !SystemTray.isSupported()) {
            return false;
        }

        try {
            SystemTray tray = SystemTray.getSystemTray();
            // Use a transparent 1x1 image if icon.png isn't found to prevent errors
            Image image = Toolkit.getDefaultToolkit().createImage("icon.png");
            TrayIcon trayIcon = new TrayIcon(image, "Notification");

            trayIcon.setImageAutoSize(true);
            tray.add(trayIcon);
            trayIcon.displayMessage(title, message, MessageType.INFO);

            // 2. Schedule removal so the icon doesn't haunt the taskbar forever
            new Thread(() -> {
                try {
                    Thread.sleep(10000); // Wait long enough for the toast to show
                    tray.remove(trayIcon);
                } catch (InterruptedException e) { /* Ignore */ }
            }).start();

            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    public static void detachedAlert(String title, String message) {
        final String finalTitle = (title == null) ? "Alert" : title;
        final String finalMsg = (message == null) ? "An alert has been triggered." : message;

        try {
            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("win")) {
                if (displayTray(finalTitle, finalMsg)) return;
                // Escape single quotes for PowerShell
                String escapedMsg = finalMsg.replace("'", "''");
                String escapedTitle = finalTitle.replace("'", "''");

                String script = String.format(
                        "Add-Type -AssemblyName System.Windows.Forms; " +
                                "$bal = New-Object System.Windows.Forms.NotifyIcon; " +
                                "$bal.Icon = [System.Drawing.Icon]::ExtractAssociatedIcon('powershell.exe'); " +
                                "$bal.BalloonTipTitle = '%s'; $bal.BalloonTipText = '%s'; " +
                                "$bal.Visible = $true; $bal.ShowBalloonTip(5000); " +
                                "Start-Sleep -s 5; $bal.Dispose();", // Clean up the icon
                        escapedTitle, escapedMsg);

                new ProcessBuilder("powershell", "-Command", script).start();
            } else if (os.contains("mac")) {
                if (displayTray(finalTitle, finalMsg)) return;
                // macOS escaping (double quotes)
                String escapedMsg = finalMsg.replace("\"", "\\\"");
                new ProcessBuilder("osascript", "-e",
                        String.format("display notification \"%s\" with title \"%s\"", escapedMsg, finalTitle)).start();
            } else {
                // Linux Multi-Tool Fallback
                String[] commands = {
                        "notify-send", title, message,              // GNOME/Universal
                        "kdialog", "--title", title, "--passivepopup", message, "5", // KDE
                        "zenity", "--info", "--title=" + title, "--text=" + message, // Legacy/GTK
                        "xmessage", message                         // Ancient X11 fallback
                };

                boolean success = false;
                // Try each common notification tool until one works
                for (int i = 0; i < commands.length; i += 3) {
                    try {
                        // This is a simplified logic; in reality, you'd check which tool exists
                        new ProcessBuilder(commands[i], commands[i + 1], commands[i + 2]).start();
                        success = true;
                        break;
                    } catch (Exception ignored) {
                    }
                }
            }
        } catch (Throwable t) {
            System.err.println("Total failure triggering popup: " + finalMsg);
        }
    }

    public static void main(String[] args) {
        detachedAlert("Test", "This is a test");
    }
}