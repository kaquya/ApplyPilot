package ch.applypilot;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
class RateLimit {

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    private record Window(long minute, int count) {}

    void check(String key, int maximum) {
        long minute = Instant.now().getEpochSecond() / 60;
        if (windows.size() > 10000) windows.entrySet().removeIf(e -> e.getValue().minute < minute);
        var next = windows.compute(key, (k, v) ->
            v == null || v.minute != minute
                ? new Window(minute, 1)
                : new Window(minute, v.count + 1)
        );
        if (next.count > maximum) throw new ResponseStatusException(
            HttpStatus.TOO_MANY_REQUESTS,
            "Too many requests. Please wait a minute."
        );
    }
}
