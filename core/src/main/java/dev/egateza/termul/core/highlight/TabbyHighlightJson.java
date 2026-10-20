package dev.egateza.termul.core.highlight;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Import/export profil highlight dalam format JSON plugin Tabby (tabby-highlight). Import menerima satu profil
 * ({@code {id, name, keywords}}), array profil, atau seluruh config plugin ({@code highlightProfiles}, boleh di dalam
 * {@code highlightPlugin}). Export menulis satu profil, sama seperti tombol Export di Tabby.
 */
public final class TabbyHighlightJson {

    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private TabbyHighlightJson() {
    }

    /** @throws IOException kalau bukan JSON, atau tidak berisi profil highlight */
    public static List<HighlightProfile> parse(byte[] json) throws IOException {
        JsonNode root = MAPPER.readTree(json);
        if (root == null || root.isMissingNode()) {
            throw new IOException("File kosong");
        }
        if (root.has("highlightPlugin")) {
            root = root.get("highlightPlugin");
        }
        if (root.has("highlightProfiles")) {
            root = root.get("highlightProfiles");
        }
        var nodes = new ArrayList<JsonNode>();
        if (root.isArray()) {
            root.forEach(nodes::add);
        } else {
            nodes.add(root);
        }
        var out = new ArrayList<HighlightProfile>();
        for (JsonNode n : nodes) {
            if (!n.isObject() || !n.has("keywords") || !n.get("keywords").isArray()) {
                throw new IOException("Bukan profil highlight Tabby: field \"keywords\" tidak ada");
            }
            out.add(MAPPER.treeToValue(n, HighlightProfile.class));
        }
        if (out.isEmpty()) {
            throw new IOException("Tidak ada profil highlight di file");
        }
        return out;
    }

    public static byte[] export(HighlightProfile profile) {
        try {
            return MAPPER.writer().with(SerializationFeature.INDENT_OUTPUT).writeValueAsBytes(profile);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
