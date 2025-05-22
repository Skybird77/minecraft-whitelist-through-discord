package com.github.skybird77.server;

import net.minecraft.server.command.CommandOutput;
import net.minecraft.text.Text;

public class CapturingCommandOutput implements CommandOutput {
    private final StringBuilder output = new StringBuilder();

    @Override
    public void sendMessage(Text message) {
        output.append(message.getString()).append("\n");
    }

    @Override
    public boolean shouldReceiveFeedback() { return true; }

    @Override
    public boolean shouldTrackOutput() { return true; }

    @Override
    public boolean shouldBroadcastConsoleToOps() { return false; }

    public String getCapturedOutput() {
        String result = output.toString();
        if (result.endsWith("\n")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }
}
