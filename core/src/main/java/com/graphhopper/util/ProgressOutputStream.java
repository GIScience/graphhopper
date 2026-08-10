package com.graphhopper.util;

import org.apache.commons.lang3.time.DurationFormatUtils;
import org.apache.commons.lang3.time.StopWatch;

import java.io.IOException;
import java.io.OutputStream;
import java.util.function.Consumer;

class ProgressOutputStream extends OutputStream {
    private final OutputStream out;
    private final long max;
    private final Consumer<String> log;
    private final String progressRenderString;

    private long current = 0;
    private long speed = 0;

    private final StopWatch time;
    private final StopWatch totalTime;


    public ProgressOutputStream(OutputStream out, String task, long max, Consumer<String> log) {
        this.out = out;
        this.max = max;
        this.log = log;
        if (max > 0) {
            this.progressRenderString = "%s %%%dd/%d (%%3d%%%%) [%%.2f MB/s / %%.2f MB/s] %%s"
                    .formatted(task, Long.toString(max).length(), max);
        } else {
            this.progressRenderString = "%s %%d/? [%%.2f Mbps / %%.2f Mbps] %%s".formatted(task);
        }

        this.time = StopWatch.createStarted();
        this.totalTime = StopWatch.createStarted();

    }

    private void progress(long n) {
        current += n;
        speed += n;
        if (time.getTime() >= 1000) {
            triggerLog();
            time.reset();
            time.start();
            speed = 0;
        }
    }

    private void triggerLog() {
        log.accept(render((speed) / (Math.max(time.getTime(), 1) * 1000.0), (current) / (Math.max(totalTime.getTime(), 1) * 1000.0)));
    }

    private String render(double mbits, double mbitsTotal) {
        if (max > 0) {
            return progressRenderString
                    .formatted(current, (int) ((current / (double) max) * 100),
                            mbits, mbitsTotal,
                            DurationFormatUtils.formatDuration(totalTime.getTime(), "mm:ss.SSS"));
        }
        return progressRenderString
                .formatted(current,
                        mbits, mbitsTotal,
                        DurationFormatUtils.formatDuration(totalTime.getTime(), "mm:ss.SSS"));

    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        out.write(b, off, len);
        progress(len);
    }

    @Override
    public void write(int b) throws IOException {
        out.write(b);
        progress(1);
    }

    @Override
    public void close() throws IOException {
        out.close();
        triggerLog();
    }
}