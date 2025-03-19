package com.graphhopper.util;

import me.tongfei.progressbar.DelegatingProgressBarConsumer;
import me.tongfei.progressbar.ProgressBar;
import me.tongfei.progressbar.ProgressBarBuilder;
import me.tongfei.progressbar.ProgressBarStyle;
import org.apache.log4j.Logger;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.ConsoleAppender;
import org.apache.logging.log4j.core.config.AppenderRef;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.layout.PatternLayout;


public class ProgressBarLogger {
    private static String LOGGER_NAME = "ProgressBarLogger";
    private static final Logger CLASS_LOGGER = Logger.getLogger(ProgressBarLogger.class);


    private static Logger initializeLogger() {
        LoggerContext context = LoggerContext.getContext(false);
        Configuration config = context.getConfiguration();
        if (Logger.getRootLogger().getAppender(LOGGER_NAME) == null) {
            String originalConsolePattern;
            try {
                originalConsolePattern = config.getAppender("Console").getLayout().getContentFormat().get("format");
            } catch (Exception e) {
                originalConsolePattern = "%d{yyyy-MM-dd HH:mm:ss} %highlight{%-7p} %style{%50t}{Cyan} %style{[ %-40.40c{1.} ]}{Bright Cyan}   %m";
            }

            // Remove new line characters from the end of the pattern
            if (originalConsolePattern.endsWith("%n")) {
                originalConsolePattern = originalConsolePattern.substring(0, originalConsolePattern.length() - 2);
            }

            // Remove new line characters from the beginning of the pattern
            if (originalConsolePattern.startsWith("%n")) {
                originalConsolePattern = originalConsolePattern.substring(2);
            }

            final PatternLayout layout = PatternLayout.newBuilder()
                    .withPattern(originalConsolePattern + "\r")
                    .withConfiguration(config)
                    .build();

            Appender consoleAppender = ConsoleAppender.newBuilder()
                    .setConfiguration(config)
                    .setName(LOGGER_NAME)
                    .setLayout(layout)
                    .setFilter(null)
                    .setTarget(ConsoleAppender.Target.SYSTEM_OUT)
                    .setName(LOGGER_NAME)
                    .setFollow(false)
                    .build();
            consoleAppender.start();

            // Create the new appender reference
            AppenderRef ref = AppenderRef.createAppenderRef(LOGGER_NAME, null, null);
            AppenderRef[] refs = new AppenderRef[]{ref};
            LoggerConfig loggerConfig = LoggerConfig.newBuilder()
                    .withAdditivity(false)
                    .withLevel(Level.INFO)
                    .withLoggerName(LOGGER_NAME)
                    .withIncludeLocation("true")
                    .withRefs(refs)
                    .withProperties(null)
                    .withConfig(config)
                    .withtFilter(null)
                    .build();

            config.addAppender(consoleAppender);
            loggerConfig.addAppender(consoleAppender, null, null);
            config.addLogger(LOGGER_NAME, loggerConfig);
            context.updateLoggers();
        }
        return Logger.getLogger(LOGGER_NAME);
    }

    public static void setLoggerName(String loggerName) {
        LOGGER_NAME = loggerName;
    }

    public static String getLoggerName() {
        return LOGGER_NAME;
    }

    public static Logger getLogger() {
        return initializeLogger();
    }

    /**
     * Creates and returns a ProgressListener that receives percentages and updates a progress bar with the given task name.
     *
     * @param taskName the name of the task to be displayed in the progress bar
     * @return a ProgressListener that updates the progress bar
     */
    public static ProgressListener getProgressListener(String taskName) {
        Logger logger = initializeLogger();
        return new ProgressListener() {
            final ProgressBarBuilder progressBarBuilder = new ProgressBarBuilder()
                    .setStyle(ProgressBarStyle.COLORFUL_UNICODE_BAR)
                    .setUpdateIntervalMillis(1000) // slow update for better visualization and less IO. Avoids % calculation for each element.
                    .setInitialMax(100)
                    .setTaskName(taskName)
                    .setConsumer(new DelegatingProgressBarConsumer(logger::info));

            ProgressBar progressBar;

            int lastPercentage = 0;
            @Override
            public void update(long val) {
                if (progressBar == null) {
                    progressBar = progressBarBuilder.build();
                }

                if (val > lastPercentage) {
                    progressBar.stepTo(val);
                    lastPercentage = (int) val;
                }
            }
        };
    }
}
