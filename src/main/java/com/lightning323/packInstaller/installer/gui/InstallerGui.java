package com.lightning323.packInstaller.installer.gui;

import javax.swing.*;
import javax.swing.text.DefaultCaret;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Small cross-platform installer window (pure Swing, no extra dependencies so it
 * works on Windows, macOS and Linux).
 *
 * <p>Shows a progress bar, a status line and a scrolling box mirroring everything
 * that would normally go to the console, plus a Cancel button.</p>
 *
 * <p>All methods are thread-safe and never throw. If the GUI cannot be shown
 * (headless JVM, no display, missing AWT, or any other OS issue) {@link #tryInit}
 * prints a warning to the console and returns {@code false}, after which every
 * other method is a harmless no-op and the installer simply continues with
 * console output only.</p>
 */
public final class InstallerGui {

    private InstallerGui() {
    }

    private static final int MAX_LOG_CHARS = 300_000;
    private static final int TRIM_TO_CHARS = 200_000;

    private static volatile boolean enabled = false;
    private static volatile boolean initAttempted = false;

    private static final AtomicBoolean cancelled = new AtomicBoolean(false);
    private static final AtomicBoolean finished = new AtomicBoolean(false);

    private static JFrame frame;
    private static JProgressBar progressBar;
    private static JLabel statusLabel;
    private static JTextArea logArea;
    private static JButton cancelButton;

    private static PrintStream originalOut;
    private static PrintStream originalErr;

    /**
     * Tries to create and show the installer window and mirror console output into it.
     *
     * @param title window title
     * @return {@code true} if the GUI is now visible, {@code false} if it could not
     * be shown (a warning is printed and the caller should carry on with console only)
     */
    public static synchronized boolean tryInit(String title) {
        if (initAttempted) {
            return enabled;
        }
        initAttempted = true;

        if (GraphicsEnvironment.isHeadless()) {
            warnNoGui("headless environment (no display available)");
            return false;
        }

        try {
            // Remember the real console streams before we wrap them.
            originalOut = System.out;
            originalErr = System.err;

            final String windowTitle = (title == null || title.isEmpty()) ? "Pack-Installer" : title;
            try {
                if (SwingUtilities.isEventDispatchThread()) {
                    createWindow(windowTitle);
                } else {
                    SwingUtilities.invokeAndWait(() -> createWindow(windowTitle));
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                warnNoGui("interrupted while creating the window");
                return false;
            } catch (InvocationTargetException e) {
                warnNoGuiCause(e.getCause());
                return false;
            }

            // Mirror everything printed to the console into the scrolling log box.
            System.setOut(new PrintStream(new GuiTeeStream(originalOut), true));
            System.setErr(new PrintStream(new GuiTeeStream(originalErr), true));

            enabled = true;
            return true;
        } catch (Throwable t) {
            warnNoGui(t.toString());
            return false;
        }
    }

    private static void warnNoGui(String reason) {
        warnNoGuiDetail(reason);
    }

    private static void warnNoGuiCause(Throwable cause) {
        warnNoGuiDetail(cause == null ? "unknown error" : cause.toString());
    }

    private static void warnNoGuiDetail(String reason) {
        try {
            PrintStream err = (originalErr != null) ? originalErr : System.err;
            err.println("WARNING: Could not show the installer GUI (" + reason + "). Continuing with console output only.");
        } catch (Throwable ignored) {
            // Last resort: absolutely no fuss.
        }
        enabled = false;
    }

    private static void createWindow(String title) {
        frame = new JFrame(title);
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.setLayout(new BorderLayout(8, 8));
        frame.setSize(640, 460);
        frame.setMinimumSize(new Dimension(420, 300));
        frame.setLocationRelativeTo(null); // center on screen

        // Top: status line + progress bar.
        JPanel topPanel = new JPanel(new BorderLayout(4, 4));
        topPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 0, 8));
        statusLabel = new JLabel("Starting...");
        progressBar = new JProgressBar(0, 1000);
        progressBar.setStringPainted(true);
        progressBar.setString("0%");
        progressBar.setIndeterminate(true);
        topPanel.add(statusLabel, BorderLayout.NORTH);
        topPanel.add(progressBar, BorderLayout.CENTER);
        frame.add(topPanel, BorderLayout.NORTH);

        // Center: scrolling log box.
        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        logArea.setLineWrap(false);
        DefaultCaret caret = (DefaultCaret) logArea.getCaret();
        caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE); // follow the tail
        JScrollPane scrollPane = new JScrollPane(logArea);
        scrollPane.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
        frame.add(scrollPane, BorderLayout.CENTER);

        // Bottom: cancel button.
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(0, 8, 8, 8));
        cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(e -> requestCancel());
        bottomPanel.add(cancelButton);
        frame.add(bottomPanel, BorderLayout.SOUTH);

        // Closing the window behaves like Cancel while running, like Close when done.
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (finished.get()) {
                    dispose();
                } else {
                    requestCancel();
                }
            }
        });

        frame.setVisible(true);
    }

    /** @return {@code true} once the GUI is up and usable. */
    public static boolean isEnabled() {
        return enabled;
    }

    /** @return {@code true} after the user pressed Cancel / closed the window. */
    public static boolean isCancelled() {
        return cancelled.get();
    }

    /**
     * Cooperative cancellation checkpoint for worker threads.
     *
     * @throws CancellationException if the user cancelled the installation
     */
    public static void checkCancelled() {
        if (cancelled.get()) {
            throw new CancellationException("Installation cancelled by user");
        }
    }

    /** Updates the status line. No-op when the GUI is unavailable. */
    public static void setStatus(String status) {
        if (!enabled) {
            return;
        }
        final String text = (status == null) ? "" : status;
        try {
            SwingUtilities.invokeLater(() -> {
                try {
                    if (statusLabel != null) {
                        statusLabel.setText(text);
                    }
                } catch (Throwable ignored) {
                }
            });
        } catch (Throwable ignored) {
        }
    }

    /** Updates the window title (e.g. once the modpack name is known). No-op when the GUI is unavailable. */
    public static void setTitle(String title) {
        if (!enabled) {
            return;
        }
        final String text = (title == null || title.isEmpty()) ? "Pack-Installer" : title;
        try {
            SwingUtilities.invokeLater(() -> {
                try {
                    if (frame != null) {
                        frame.setTitle(text);
                    }
                } catch (Throwable ignored) {
                }
            });
        } catch (Throwable ignored) {
        }
    }

    /** Switches the progress bar between determinate and indeterminate mode. */
    public static void setIndeterminate(boolean indeterminate) {
        if (!enabled) {
            return;
        }
        try {
            SwingUtilities.invokeLater(() -> {
                try {
                    if (progressBar != null) {
                        progressBar.setIndeterminate(indeterminate);
                    }
                } catch (Throwable ignored) {
                }
            });
        } catch (Throwable ignored) {
        }
    }

    /**
     * Sets overall progress.
     *
     * @param fraction 0.0 (just started) to 1.0 (complete); values are clamped
     */
    public static void setProgress(double fraction) {
        if (!enabled) {
            return;
        }
        final double clamped = Math.min(1.0, Math.max(0.0, fraction));
        try {
            SwingUtilities.invokeLater(() -> {
                try {
                    if (progressBar != null) {
                        progressBar.setIndeterminate(false);
                        int value = (int) Math.round(clamped * 1000);
                        progressBar.setValue(value);
                        progressBar.setString(((int) Math.round(clamped * 100)) + "%");
                    }
                } catch (Throwable ignored) {
                }
            });
        } catch (Throwable ignored) {
        }
    }

    /** Convenience helper for counted tasks (e.g. "Downloaded 3/42 files"). */
    public static void setTaskProgress(int done, int total, String status) {
        if (total <= 0) {
            setIndeterminate(true);
            setStatus(status);
            return;
        }
        setProgress((double) done / (double) total);
        if (status != null) {
            setStatus(status + " (" + done + "/" + total + ")");
        }
    }

    /** Appends a line to the scrolling log box (in addition to normal console output). */
    public static void log(String message) {
        if (!enabled) {
            return;
        }
        appendText(String.valueOf(message) + System.lineSeparator());
    }

    private static void appendText(String text) {
        if (!enabled || text == null || text.isEmpty()) {
            return;
        }
        try {
            SwingUtilities.invokeLater(() -> {
                try {
                    if (logArea == null) {
                        return;
                    }
                    logArea.append(text);
                    // Keep memory bounded on very long installs.
                    if (logArea.getDocument().getLength() > MAX_LOG_CHARS) {
                        logArea.replaceRange("", 0, logArea.getDocument().getLength() - TRIM_TO_CHARS);
                    }
                } catch (Throwable ignored) {
                }
            });
        } catch (Throwable ignored) {
        }
    }

    /**
     * Called when the installation completed successfully: shows 100%, swaps the
     * Cancel button for a Close button and auto-closes shortly after so scripted
     * / launcher runs don't hang. The caller is expected to exit normally
     * afterwards; the auto-close is just a safety net.
     */
    public static void finish(String message) {
        if (!enabled) {
            return;
        }
        try {
            finished.set(true);
            if (message != null) {
                appendText(message + System.lineSeparator());
                setStatus(message);
            }
            setProgress(1.0);
            SwingUtilities.invokeLater(() -> {
                try {
                    if (cancelButton != null) {
                        cancelButton.setText("Close");
                        for (java.awt.event.ActionListener al : cancelButton.getActionListeners()) {
                            cancelButton.removeActionListener(al);
                        }
                        cancelButton.addActionListener(e -> dispose());
                    }
                } catch (Throwable ignored) {
                }
            });
            // Brief grace period so the completed state is visible, then close.
            // Daemon timer: never blocks JVM shutdown.
            java.util.Timer timer = new java.util.Timer(true);
            timer.schedule(new java.util.TimerTask() {
                @Override
                public void run() {
                    dispose();
                }
            }, 2000);
        } catch (Throwable ignored) {
        }
    }

    /**
     * Shows a failure in the window. Non-blocking; the caller decides when to exit.
     */
    public static void showError(String message) {
        if (!enabled) {
            return;
        }
        try {
            finished.set(true);
            if (message != null) {
                appendText("ERROR: " + message + System.lineSeparator());
                setStatus("Failed: " + message);
            } else {
                setStatus("Failed");
            }
            setIndeterminate(false);
            SwingUtilities.invokeLater(() -> {
                try {
                    if (cancelButton != null) {
                        cancelButton.setText("Close");
                        for (java.awt.event.ActionListener al : cancelButton.getActionListeners()) {
                            cancelButton.removeActionListener(al);
                        }
                        cancelButton.addActionListener(e -> dispose());
                    }
                } catch (Throwable ignored) {
                }
            });
        } catch (Throwable ignored) {
        }
    }

    /**
     * Invoked by the Cancel button / window close. Flags cooperative cancellation
     * and guarantees the JVM exits shortly after so a stuck download can't hang
     * the installer forever.
     */
    public static void requestCancel() {
        if (!enabled) {
            return;
        }
        try {
            if (finished.get()) {
                dispose();
                return;
            }
            if (!cancelled.compareAndSet(false, true)) {
                return;
            }
            appendText("Cancellation requested, stopping..." + System.lineSeparator());
            setStatus("Cancelling...");
            SwingUtilities.invokeLater(() -> {
                try {
                    if (cancelButton != null) {
                        cancelButton.setEnabled(false);
                        cancelButton.setText("Cancelling...");
                    }
                } catch (Throwable ignored) {
                }
            });
            Thread killer = new Thread(() -> {
                try {
                    Thread.sleep(1500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    try {
                        dispose();
                    } finally {
                        System.exit(1);
                    }
                }
            }, "installer-gui-cancel");
            killer.setDaemon(true);
            killer.start();
        } catch (Throwable t) {
            try {
                System.exit(1);
            } catch (Throwable ignored) {
            }
        }
    }

    /** Hides and releases the window. Safe to call multiple times or when disabled. */
    public static void dispose() {
        if (!enabled && frame == null) {
            return;
        }
        enabled = false;
        try {
            // Restore the real console streams for any post-GUI output.
            try {
                if (originalOut != null) {
                    System.setOut(originalOut);
                }
                if (originalErr != null) {
                    System.setErr(originalErr);
                }
            } catch (Throwable ignored) {
            }
            try {
                if (SwingUtilities.isEventDispatchThread()) {
                    disposeOnEdt();
                } else {
                    SwingUtilities.invokeLater(InstallerGui::disposeOnEdt);
                }
            } catch (Throwable ignored) {
            }
        } catch (Throwable ignored) {
        }
    }

    private static void disposeOnEdt() {
        try {
            if (frame != null) {
                frame.setVisible(false);
                frame.dispose();
            }
        } catch (Throwable ignored) {
        } finally {
            frame = null;
            progressBar = null;
            statusLabel = null;
            logArea = null;
            cancelButton = null;
        }
    }

    /**
     * OutputStream that writes through to the original console stream and mirrors
     * the text into the GUI log box.
     */
    private static class GuiTeeStream extends OutputStream {
        private final PrintStream original;

        GuiTeeStream(PrintStream original) {
            this.original = original;
        }

        @Override
        public synchronized void write(int b) {
            try {
                if (original != null) {
                    original.write(b);
                }
            } catch (Throwable ignored) {
            }
            appendText(String.valueOf((char) (b & 0xFF)));
        }

        @Override
        public synchronized void write(byte[] b, int off, int len) {
            String text = null;
            try {
                if (original != null) {
                    original.write(b, off, len);
                }
            } catch (Throwable ignored) {
            }
            try {
                text = new String(b, off, len, java.nio.charset.Charset.defaultCharset());
            } catch (Throwable ignored) {
            }
            if (text != null && !text.isEmpty()) {
                appendText(text);
            }
        }

        @Override
        public synchronized void flush() {
            try {
                if (original != null) {
                    original.flush();
                }
            } catch (Throwable ignored) {
            }
        }
    }
}
