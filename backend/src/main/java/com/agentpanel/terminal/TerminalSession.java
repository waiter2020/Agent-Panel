package com.agentpanel.terminal;

public interface TerminalSession extends AutoCloseable {

    void write(byte[] data);

    void resize(int cols, int rows);

    @Override
    void close();
}
