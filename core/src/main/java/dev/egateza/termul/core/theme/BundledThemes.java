package dev.egateza.termul.core.theme;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Template tema yang ikut di dalam JAR ({@code templates/<id>.json}, didaftar di {@code templates/index.txt} karena isi
 * direktori classpath tidak bisa di-list dengan andal). Dipasang ke folder tema user oleh
 * {@link ThemeStore#installBundled(List)}.
 */
public final class BundledThemes {

    private static final Logger log = LoggerFactory.getLogger(BundledThemes.class);
    private static final String DIR = "templates/";

    private BundledThemes() {
    }

    /** @return semua template yang valid, urut sesuai {@code index.txt}; template rusak dilewati */
    public static List<CustomTheme> load() {
        ObjectMapper mapper = JsonMapper.builder()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
        var result = new ArrayList<CustomTheme>();
        for (String id : index()) {
            try (InputStream in = BundledThemes.class.getResourceAsStream(DIR + id + ".json")) {
                if (in == null) {
                    log.warn("Template tema {} terdaftar di index tapi tidak ada", id);
                    continue;
                }
                CustomTheme theme = mapper.readValue(in, CustomTheme.class);
                if (!theme.id().equals(id)) {
                    log.warn("Template tema {} dilewati: id di file berbeda ({})", id, theme.id());
                    continue;
                }
                result.add(theme);
            } catch (IOException | RuntimeException e) {
                log.warn("Template tema {} dilewati: {}", id, e.getMessage());
            }
        }
        return List.copyOf(result);
    }

    static List<String> index() {
        try (InputStream in = BundledThemes.class.getResourceAsStream(DIR + "index.txt")) {
            if (in == null) {
                return List.of();
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).lines()
                    .map(String::strip)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Index template tema tidak terbaca", e);
        }
    }
}
