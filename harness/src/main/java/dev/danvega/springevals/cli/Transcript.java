package dev.danvega.springevals.cli;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What a transcript shows about an attempt, as counts only: nothing verbatim
 * from the session is copied into results. A CLI whose output carries no
 * structure reports zeros, never a failure.
 */
public record Transcript(int commands, int filesWritten, int urlsFetched, List<String> hosts) {

    private static final Pattern URL = Pattern.compile("https?://[^\\s\"'<>`)\\]]+");

    public static Transcript empty() {
        return new Transcript(0, 0, 0, List.of());
    }

    public static List<String> urlsIn(String text) {
        List<String> urls = new ArrayList<>();
        if (text == null) {
            return urls;
        }
        Matcher matcher = URL.matcher(text);
        while (matcher.find()) {
            urls.add(matcher.group());
        }
        return urls;
    }

    public static String hostOf(String url) {
        try {
            String host = URI.create(url.replaceAll("[.,;:]+$", "")).getHost();
            return host == null ? null : host.toLowerCase();
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Accumulates counts while a CLI's event stream is walked. */
    public static final class Builder {
        private int commands;
        private int filesWritten;
        private int urlsFetched;
        private final Set<String> hosts = new LinkedHashSet<>();

        public Builder command(String commandLine) {
            commands++;
            for (String url : urlsIn(commandLine)) {
                fetched(url);
            }
            return this;
        }

        public Builder fileWritten() {
            filesWritten++;
            return this;
        }

        public Builder fetched(String url) {
            urlsFetched++;
            String host = hostOf(url);
            if (host != null) {
                hosts.add(host);
            }
            return this;
        }

        public Transcript build() {
            return new Transcript(commands, filesWritten, urlsFetched, List.copyOf(hosts));
        }
    }
}
