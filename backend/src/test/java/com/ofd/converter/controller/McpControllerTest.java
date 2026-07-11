package com.ofd.converter.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class McpControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;

    static Path tempDir;
    @org.junit.jupiter.api.BeforeAll
    static void setup() throws Exception { tempDir = Files.createTempDirectory("mcp-test"); }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("file.data-dir", () -> tempDir.toString());
        r.add("spring.datasource.url", () -> "jdbc:sqlite:" + tempDir.resolve("mcp.db"));
    }

    private MvcResult rpc(String body) throws Exception {
        return mvc.perform(post("/mcp").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk()).andReturn();
    }

    @Test
    void initializeReturnsSessionIdAndProtocolVersion() throws Exception {
        MvcResult r = rpc("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}");
        JsonNode j = om.readTree(r.getResponse().getContentAsString());
        assertEquals("2.0", j.get("jsonrpc").asText());
        assertEquals("2025-06-18", j.get("result").get("protocolVersion").asText());
        assertNotNull(r.getResponse().getHeader("Mcp-Session-Id"));
    }

    @Test
    void toolsCallRejectedBeforeInit() throws Exception {
        MvcResult init = rpc("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}");
        String sid = init.getResponse().getHeader("Mcp-Session-Id");
        MvcResult r = rpcWithSid(sid, "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/call\",\"params\":{\"name\":\"list_formats\",\"arguments\":{}}}", false);
        JsonNode j = om.readTree(r.getResponse().getContentAsString());
        assertEquals(-32001, j.get("error").get("code").asInt()); // NOT_INITIALIZED
    }

    @Test
    void toolsCallRejectedWithoutSession() throws Exception {
        MvcResult r = rpc("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":{\"name\":\"list_formats\",\"arguments\":{}}}");
        JsonNode j = om.readTree(r.getResponse().getContentAsString());
        assertEquals(-32000, j.get("error").get("code").asInt()); // NO_SESSION
    }

    @Test
    void fullHandshakeThenListFormats() throws Exception {
        // initialize
        MvcResult init = rpc("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}");
        String sid = init.getResponse().getHeader("Mcp-Session-Id");
        // notifications/initialized (no id -> 202)
        mvc.perform(post("/mcp").contentType(MediaType.APPLICATION_JSON)
                .header("Mcp-Session-Id", sid)
                .content("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}"))
            .andExpect(status().isAccepted());
        // tools/list
        MvcResult list = rpcWithSid(sid, "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\",\"params\":{}}", false);
        JsonNode lj = om.readTree(list.getResponse().getContentAsString());
        assertTrue(lj.get("result").get("tools").size() >= 6);
        // tools/call list_formats
        MvcResult call = mvc.perform(post("/mcp").contentType(MediaType.APPLICATION_JSON)
                .header("Mcp-Session-Id", sid)
                .content("{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\",\"params\":{\"name\":\"list_formats\",\"arguments\":{}}}"))
            .andExpect(status().isOk()).andReturn();
        JsonNode cj = om.readTree(call.getResponse().getContentAsString());
        assertNotNull(cj.get("result"));
    }

    @Test
    void extractMarkdownFullFlow() throws Exception {
        // handshake
        MvcResult init = rpc("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}");
        String sid = init.getResponse().getHeader("Mcp-Session-Id");
        rpcWithSid(sid, "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}", true);

        // Need a real OFD uploaded via the tool. Build one with Fixtures and base64 it.
        java.nio.file.Path ofd = com.ofd.converter.Fixtures.ofd(tempDir);
        String b64 = Base64.getEncoder().encodeToString(Files.readAllBytes(ofd));
        String uploadBody = om.writeValueAsString(Map.of("jsonrpc", "2.0", "id", 2, "method", "tools/call",
            "params", Map.of("name", "upload_file", "arguments", Map.of("filename", "s.ofd", "content", b64))));
        MvcResult up = rpcWithSid(sid, uploadBody, false);
        JsonNode uj = om.readTree(up.getResponse().getContentAsString());
        // Tool result is wrapped: result.content[0].text is a JSON string with file_id
        JsonNode content0 = uj.get("result").get("content").get(0);
        assertNotNull(content0);
        assertNotNull(content0.get("text"));
        String innerJson = content0.get("text").asText();
        JsonNode inner = om.readTree(innerJson);
        String fileId = inner.get("file_id").asText();
        assertNotNull(fileId);

        String extractBody = om.writeValueAsString(Map.of("jsonrpc", "2.0", "id", 3, "method", "tools/call",
            "params", Map.of("name", "extract_ofd_markdown", "arguments", Map.of("file_id", fileId))));
        MvcResult ex = rpcWithSid(sid, extractBody, false);
        JsonNode ej = om.readTree(ex.getResponse().getContentAsString());
        assertNotNull(ej.get("result"));
    }

    private MvcResult rpcWithSid(String sid, String body, boolean accepted) throws Exception {
        return mvc.perform(post("/mcp").contentType(MediaType.APPLICATION_JSON).header("Mcp-Session-Id", sid).content(body))
            .andExpect(accepted ? status().isAccepted() : status().isOk()).andReturn();
    }

    private static void assertEquals(Object e, Object a) { org.junit.jupiter.api.Assertions.assertEquals(e, a); }
    private static void assertNotNull(Object a) { org.junit.jupiter.api.Assertions.assertNotNull(a); }
    private static void assertTrue(boolean b) { org.junit.jupiter.api.Assertions.assertTrue(b); }
}