package br.com.wallet.copilot.internal.mcp.config;

import org.springframework.context.annotation.Configuration;

/**
 * Configuration class for Spring AI Model Context Protocol (MCP) Streamable HTTP Server (REQ-MCP-001).
 * Enforces session-oriented STREAMABLE protocol mode for server-initiated pushes (Form/URL Elicitation).
 */
@Configuration
public class McpServerConfiguration {

    public static final String PROTOCOL_STREAMABLE = "STREAMABLE";
    public static final String MCP_ENDPOINT = "/mcp";

}
