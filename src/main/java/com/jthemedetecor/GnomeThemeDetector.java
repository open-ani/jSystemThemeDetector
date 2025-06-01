/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *          http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package com.jthemedetecor;

import com.jthemedetecor.util.ConcurrentHashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * Used for detecting the dark theme on a Linux (GNOME/GTK) system.
 * Tested on Ubuntu.
 *
 * @author Daniel Gyorffy
 */
class GnomeThemeDetector extends OsThemeDetector {

    private static final Logger logger = LoggerFactory.getLogger(GnomeThemeDetector.class);

    private static final String MONITORING_CMD = "gsettings monitor org.gnome.desktop.interface";
    private static final String[] GET_CMD = new String[]{
            "gsettings get org.gnome.desktop.interface gtk-theme",
            "gsettings get org.gnome.desktop.interface color-scheme"
    };

    private final Set<Consumer<Boolean>> listeners = new ConcurrentHashSet<>();
    private final Pattern darkThemeNamePattern = Pattern.compile(".*dark.*", Pattern.CASE_INSENSITIVE);

    private volatile DetectorThread detectorThread;

    GnomeThemeDetector() {
        // 添加JVM关闭钩子确保进程被清理
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (detectorThread != null) {
                detectorThread.destroyProcess();
                detectorThread.interrupt();
            }
        }));
    }

    @Override
    public boolean isDark() {
        try {
            Runtime runtime = Runtime.getRuntime();
            for (String cmd : GET_CMD) {
                Process process = runtime.exec(cmd);
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String readLine = reader.readLine();
                    if (readLine != null && isDarkTheme(readLine)) {
                        return true;
                    }
                }
            }
        } catch (IOException e) {
            logger.error("Couldn't detect Linux OS theme", e);
        }
        return false;
    }

    private boolean isDarkTheme(String gtkTheme) {
        return darkThemeNamePattern.matcher(gtkTheme).matches();
    }

    @SuppressWarnings("DuplicatedCode")
    @Override
    public synchronized void registerListener(@NotNull Consumer<Boolean> darkThemeListener) {
        Objects.requireNonNull(darkThemeListener);
        final boolean listenerAdded = listeners.add(darkThemeListener);
        final boolean singleListener = listenerAdded && listeners.size() == 1;
        final DetectorThread currentDetectorThread = detectorThread;
        final boolean threadInterrupted = currentDetectorThread != null && currentDetectorThread.isInterrupted();

        if (singleListener || threadInterrupted) {
            final DetectorThread newDetectorThread = new DetectorThread(this);
            this.detectorThread = newDetectorThread;
            newDetectorThread.start();
        }
    }

    @Override
    public synchronized void removeListener(@Nullable Consumer<Boolean> darkThemeListener) {
        listeners.remove(darkThemeListener);
        if (listeners.isEmpty() && detectorThread != null) {
            // 先销毁进程，这会使I/O操作失败
            detectorThread.destroyProcess();
            // 然后再中断线程
            detectorThread.interrupt();
            System.out.println("1111");
            this.detectorThread = null;
        }
    }

    /**
     * Thread implementation for detecting the actually changed theme
     */
    private static final class DetectorThread extends Thread {

        private final GnomeThemeDetector detector;
        private final Pattern outputPattern = Pattern.compile("(gtk-theme|color-scheme).*", Pattern.CASE_INSENSITIVE);
        private boolean lastValue;
        // 保存对进程的引用
        private volatile Process monitoringProcess;

        DetectorThread(@NotNull GnomeThemeDetector detector) {
            this.detector = detector;
            this.lastValue = detector.isDark();
            this.setName("GTK Theme Detector Thread");
            this.setDaemon(true);
            this.setPriority(Thread.NORM_PRIORITY - 1);
        }

        /**
         * 销毁监控进程
         */
        public void destroyProcess() {
            Process process = monitoringProcess;
            if (process != null && process.isAlive()) {
                process.destroy();
                logger.debug("Monitoring process destroyed by external call");
            }
        }

        @Override
        public void run() {
            try {
                Runtime runtime = Runtime.getRuntime();
                monitoringProcess = runtime.exec(MONITORING_CMD);
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(monitoringProcess.getInputStream()))) {
                    while (!this.isInterrupted()) {
                        try {
                            //Expected input = gtk-theme: '$GtkThemeName'
                            String readLine = reader.readLine();

                            // reader.readLine sometimes returns null on application shutdown.
                            if (readLine == null) {
                                // 如果读取返回null，可能是进程已经结束
                                if (monitoringProcess.isAlive()) {
                                    continue;
                                } else {
                                    // 进程已结束，退出循环
                                    break;
                                }
                            }

                            if (!outputPattern.matcher(readLine).matches()) {
                                continue;
                            }
                            String[] keyValue = readLine.split("\\s");
                            String value = keyValue[1];
                            boolean currentDetection = detector.isDarkTheme(value);
                            logger.debug("Theme changed detection, dark: {}", currentDetection);
                            if (currentDetection != lastValue) {
                                lastValue = currentDetection;
                                for (Consumer<Boolean> listener : detector.listeners) {
                                    try {
                                        listener.accept(currentDetection);
                                    } catch (RuntimeException e) {
                                        logger.error("Caught exception during listener notifying ", e);
                                    }
                                }
                            }
                        } catch (IOException e) {
                            // 读取过程中出现IO异常，检查是否是因为中断
                            if (this.isInterrupted() || monitoringProcess == null || !monitoringProcess.isAlive()) {
                                break;
                            }
                            logger.error("Error reading from monitoring process: ", e);
                        }
                    }
                    logger.debug("ThemeDetectorThread has been interrupted!");
                    if (monitoringProcess != null && monitoringProcess.isAlive()) {
                        System.out.println("2222");
                        monitoringProcess.destroy();
                        logger.debug("Monitoring process has been destroyed!");
                    }
                }
            } catch (IOException e) {
                logger.error("Couldn't start monitoring process ", e);
            } catch (ArrayIndexOutOfBoundsException e) {
                logger.error("Couldn't parse command line output", e);
            }
        }
    }
}
