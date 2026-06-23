package com.agentpanel.runtime.docker;

import com.agentpanel.terminal.TerminalOutputHandler;
import com.agentpanel.terminal.TerminalSession;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.model.Frame;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.concurrent.TimeUnit;

@Slf4j
class DockerTerminalSession implements TerminalSession {

    private final DockerClient dockerClient;
    private final String execId;
    private final PipedOutputStream stdinWriter;
    private final TerminalOutputHandler handler;
    private ResultCallback.Adapter<Frame> callback;

    DockerTerminalSession(DockerClient dockerClient, String containerId, TerminalOutputHandler handler)
            throws IOException {
        this.handler = handler;
        PipedInputStream stdinReader = new PipedInputStream();
        this.stdinWriter = new PipedOutputStream(stdinReader);
        ExecCreateCmdResponse create = dockerClient.execCreateCmd(containerId)
                .withAttachStdin(true)
                .withAttachStdout(true)
                .withAttachStderr(true)
                .withTty(true)
                .withCmd("/bin/sh")
                .exec();
        this.execId = create.getId();
        this.dockerClient = dockerClient;
        this.callback = dockerClient.execStartCmd(execId)
                .withStdIn(stdinReader)
                .withDetach(false)
                .withTty(true)
                .exec(new ResultCallback.Adapter<>() {
                    @Override
                    public void onNext(Frame frame) {
                        if (frame != null && frame.getPayload() != null) {
                            handler.onOutput(frame.getPayload());
                        }
                    }

                    @Override
                    public void onComplete() {
                        handler.onClosed();
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        handler.onError(throwable.getMessage());
                    }
                });
    }

    @Override
    public void write(byte[] data) {
        try {
            stdinWriter.write(data);
            stdinWriter.flush();
        } catch (IOException e) {
            handler.onError("写入终端失败: " + e.getMessage());
        }
    }

    @Override
    public void resize(int cols, int rows) {
        try {
            dockerClient.resizeExecCmd(execId).withSize(rows, cols).exec();
        } catch (Exception e) {
            log.debug("Docker 终端 resize 失败: cols={} rows={} execId={}: {}", cols, rows, execId, e.getMessage());
        }
    }

    @Override
    public void close() {
        try {
            stdinWriter.close();
        } catch (IOException ignored) {
        }
        if (callback != null) {
            try {
                callback.close();
            } catch (IOException ignored) {
            }
            try {
                callback.awaitCompletion(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            callback = null;
        }
    }
}
