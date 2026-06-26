package dev.niels.discord;

import dev.niels.discord.impl.DiscordRpcClientImpl;
import lombok.extern.log4j.Log4j2;

/**
 * Public entry point for the Discord local IPC (RPC) client. Set the client id, {@link #connect}, drive
 * the {@code authorize}/{@code authenticate} handshake, then call the voice-control methods. Standalone
 * (no Quarkus/CDI) so it can be reused as a library — see {@code com.getpcpanel.discord.DiscordService}
 * for how the app wires it in.
 */
@Log4j2
public class DiscordRpcClient extends DiscordRpcClientImpl {
    public DiscordRpcClient() {
        super();
    }
}
