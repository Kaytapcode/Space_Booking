package com.spacebooking.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/** Loads docs/contract/openapi.yaml as a JSON tree (shared by the contract-conformance tests). */
final class OpenApiContract {

  private OpenApiContract() {}

  static JsonNode load(ObjectMapper mapper) throws IOException {
    for (String candidate : List.of("docs/contract/openapi.yaml", "../docs/contract/openapi.yaml")) {
      Path p = Path.of(candidate);
      if (Files.exists(p)) {
        // SnakeYAML (YAML 1.1) ends a flow-style plain scalar at '?', which YAML 1.2 allows; the contract has
        // `instance: /api/...?query` inside flow mappings of error examples. Quote those values so the file parses.
        String text = Files.readString(p, StandardCharsets.UTF_8).replaceAll("instance: (/[^,}\\r\\n']*\\?[^,}\\r\\n']*)", "instance: '$1'");
        Object loaded = new Yaml(new SafeConstructor(new LoaderOptions())).load(text);
        return mapper.valueToTree(loaded);
      }
    }
    throw new IllegalStateException("docs/contract/openapi.yaml not found from " + Path.of("").toAbsolutePath());
  }
}
