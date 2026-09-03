package dev.danvega.springevals.cli;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Newline-delimited JSON as the Claude Code, Qwen Code, and Gemini CLIs emit
 * it. Lines that are not JSON objects (npm notices, warnings) are skipped, so
 * a chatty CLI never breaks parsing.
 */
final class StreamJson {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** How a tool name counts in a transcript summary. */
    enum Kind { COMMAND, WRITE, FETCH }

    private StreamJson() {
    }

    static List<JsonNode> events(String output) {
        List<JsonNode> events = new ArrayList<>();
        if (output == null) {
            return events;
        }
        for (String line : output.split("\\r?\\n")) {
            String trimmed = line.strip();
            if (!trimmed.startsWith("{")) {
                continue;
            }
            try {
                JsonNode node = JSON.readTree(trimmed);
                if (node.isObject()) {
                    events.add(node);
                }
            } catch (RuntimeException ignored) {
                // partial or non-JSON line
            }
        }
        return events;
    }

    /** Whole-output fallback for the single-object `json` format, with leading noise tolerated. */
    static JsonNode object(String output) {
        int start = output == null ? -1 : output.indexOf('{');
        if (start < 0) {
            return null;
        }
        try {
            JsonNode node = JSON.readTree(output.substring(start));
            return node.isObject() ? node : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    static String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || !value.isString() ? null : value.asString();
    }

    static Long integer(JsonNode node, String field) {
        return node != null && node.hasNonNull(field) && node.get(field).isNumber() ? node.get(field).asLong() : null;
    }

    /**
     * Counts tool_use blocks of Claude-shaped assistant events, plus flat
     * Gemini-shaped tool_use events, using the given tool name table.
     */
    static Transcript summarize(List<JsonNode> events, Map<String, Kind> tools) {
        Transcript.Builder summary = new Transcript.Builder();
        for (JsonNode event : events) {
            String type = text(event, "type");
            if ("assistant".equals(type)) {
                JsonNode content = event.path("message").path("content");
                if (content.isArray()) {
                    for (JsonNode block : content) {
                        if ("tool_use".equals(text(block, "type"))) {
                            count(summary, tools, text(block, "name"), block.path("input"));
                        }
                    }
                }
            } else if ("tool_use".equals(type)) {
                count(summary, tools, text(event, "tool_name"), event.path("parameters"));
            }
        }
        return summary.build();
    }

    private static void count(Transcript.Builder summary, Map<String, Kind> tools, String name, JsonNode input) {
        Kind kind = name == null ? null : tools.get(name);
        if (kind == null) {
            return;
        }
        switch (kind) {
            case COMMAND -> summary.command(text(input, "command"));
            case WRITE -> summary.fileWritten();
            case FETCH -> {
                String url = text(input, "url");
                if (url == null) {
                    List<String> urls = Transcript.urlsIn(text(input, "prompt"));
                    url = urls.isEmpty() ? null : urls.getFirst();
                }
                if (url != null) {
                    summary.fetched(url);
                }
            }
        }
    }
}
