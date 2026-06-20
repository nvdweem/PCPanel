package com.getpcpanel.mcp;

/**
 * Shared gating constant for the dev MCP tools.
 *
 * <p>The whole {@code com.getpcpanel.mcp} package, plus the MCP extension itself, is only compiled
 * under the {@code mcp} Maven profile ({@code -Dpcpanel.mcp=true}), so the default/native build never
 * contains any of this. On top of that build-level gate, every dev tool bean carries
 * {@code @ApplicationScoped} together with
 * {@code @IfBuildProperty(name = McpDevTool.FLAG, stringValue = "true")}, so its {@code @Tool}
 * methods are wired into the server only when {@value #FLAG} is {@code true} — on in the {@code %dev}
 * profile, off otherwise (see {@code application.properties}).
 *
 * <p>Future <em>non</em>-dev MCP tools live in the same package but simply omit the
 * {@code @IfBuildProperty} gate (a plain {@code @ApplicationScoped} bean), so they are available
 * whenever the server itself is built in.
 */
public final class McpDevTool {
    /** Build-time config flag gating the dev tools. */
    public static final String FLAG = "pcpanel.mcp.dev";

    private McpDevTool() {
    }
}
