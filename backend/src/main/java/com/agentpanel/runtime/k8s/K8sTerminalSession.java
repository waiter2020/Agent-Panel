package com.agentpanel.runtime.k8s;

import com.agentpanel.terminal.TerminalOutputHandler;
import com.agentpanel.terminal.TerminalSession;
import io.fabric8.kubernetes.client.dsl.ExecWatch;
import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@Slf4j
class K8sTerminalSession implements TerminalSession {

    private final ExecWatch execWatch;
    private final OutputStream stdin;
    private final ExecutorService readerExecutor;
    private final Future<?> readerFuture;

    K8sTerminalSession(ExecWatch execWatch, TerminalOutputHandler handler) {
        this.execWatch = execWatch;
        this.stdin = execWatch.getInput();
        this.readerExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "k8s-terminal-reader");
            t.setDaemon(true);
            return t;
        });
        this.readerFuture = readerExecutor.submit(() -> readOutput(execWatch.getOutput(), handler));
    }

    private void readOutput(InputStream output, TerminalOutputHandler handler) {
        try (output) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = output.read(buffer)) >= 0) {
                if (read == 0) {
                    continue;
                }
                byte[] chunk = new byte[read];
                System.arraycopy(buffer, 0, chunk, 0, read);
                handler.onOutput(chunk);
            }
            handler.onClosed();
        } catch (Exception e) {
            if (!Thread.currentThread().isInterrupted()) {
                handler.onError(e.getMessage());
            }
        }
    }

    @Override
    public void write(byte[] data) {
        if (stdin == null) {
            return;
        }
        try {
            stdin.write(data);
            stdin.flush();
        } catch (Exception e) {
            handlerSafeError(e.getMessage());
        }
    }

    private void handlerSafeError(String message) {
        log.debug("K8s terminal write failed: {}", message);
    }

    @Override
    public void resize(int cols, int rows) {
        // fabric8 resize requires pod exec websocket; skip for MVP
    }

    @Override
    public void close() {
        readerFuture.cancel(true);
        readerExecutor.shutdownNow();
        try {
            if (stdin != null) {
                stdin.close();
            }
        } catch (Exception ignored) {
        }
        try {
            execWatch.close();
        } catch (Exception ignored) {
        }
    }
}
