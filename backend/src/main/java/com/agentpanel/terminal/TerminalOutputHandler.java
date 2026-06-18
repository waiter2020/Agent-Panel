package com.agentpanel.terminal;

@FunctionalInterface
public interface TerminalOutputHandler {

    void onOutput(byte[] data);

    default void onError(String message) {
    }

    default void onClosed() {
    }
}
