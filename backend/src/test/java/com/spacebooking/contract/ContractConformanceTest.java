package com.spacebooking.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.spacebooking.support.AbstractIntegrationTest;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * A17: the springdoc spec served at /v3/api-docs must say the same thing as docs/contract/openapi.yaml.
 *
 * <p>Compared: method + path set, parameters (name, in, required, type), request bodies, response status codes,
 * response media types and schemas, and per schema: type, nullability, format, enum, properties, required, items.
 *
 * <p>Normalised on both sides (springdoc cosmetics, not contract content): key order; $ref indirection (enum and
 * primitive component schemas are inlined by springdoc, so every schema is compared fully dereferenced); allOf is
 * flattened (and component schemas that exist only to be an allOf member are not compared by name); OpenAPI 3.1
 * {@code type: [x, null]} equals 3.0 {@code type: x, nullable: true}; integer/number {@code format} (int32/int64) is
 * dropped; descriptions, examples, defaults, {@code const}, constraints (min/max/pattern) and vendor extensions are
 * not compared.
 */
class ContractConformanceTest extends AbstractIntegrationTest {

  private static final List<String> METHODS = List.of("get", "put", "post", "delete", "patch", "head", "options");

  private JsonNode contract;
  private JsonNode springdoc;
  private Set<String> allOfMembers;

  @BeforeEach
  void load() throws IOException {
    contract = OpenApiContract.load(mapper);
    Resp docs = get("/v3/api-docs", null);
    assertThat(docs.status()).as("GET /v3/api-docs must be served without a token: %s", docs.body()).isEqualTo(200);
    springdoc = docs.body();
    allOfMembers = allOfMemberNames(contract);
  }

  // ---------------------------------------------------------------- tests

  @Test
  void springdocSpec_methodAndPathSet_matchesContract() {
    assertNoDiff("operations", diff(operationKeys(contract), operationKeys(springdoc)));
  }

  @Test
  void springdocSpec_responseStatusCodes_matchContract() {
    assertNoDiff("response status codes", diff(perOperation(contract, this::statusCodes), perOperation(springdoc, this::statusCodes)));
  }

  @Test
  void springdocSpec_responseBodies_matchContract() {
    assertNoDiff("response media types and schemas", diff(perOperation(contract, this::responses), perOperation(springdoc, this::responses)));
  }

  @Test
  void springdocSpec_parametersAndRequestBodies_matchContract() {
    assertNoDiff(
        "parameters and request bodies",
        diff(perOperation(contract, this::requestSide), perOperation(springdoc, this::requestSide)));
  }

  @Test
  void springdocSpec_schemas_matchContract() {
    assertNoDiff("component schemas", diff(schemas(contract), schemas(springdoc)));
  }

  // ---------------------------------------------------------------- operations

  private ObjectNode operationKeys(JsonNode doc) {
    ObjectNode out = mapper.createObjectNode();
    forEachOperation(doc, (key, pathItem, op) -> out.put(key, true));
    return sorted(out);
  }

  private interface OperationVisitor {
    void visit(String key, JsonNode pathItem, JsonNode operation);
  }

  private void forEachOperation(JsonNode doc, OperationVisitor visitor) {
    Iterator<Map.Entry<String, JsonNode>> paths = doc.path("paths").fields();
    while (paths.hasNext()) {
      Map.Entry<String, JsonNode> path = paths.next();
      for (String method : METHODS) {
        if (path.getValue().has(method)) {
          visitor.visit(method.toUpperCase() + " " + path.getKey(), path.getValue(), path.getValue().get(method));
        }
      }
    }
  }

  private interface OperationView {
    JsonNode of(JsonNode doc, JsonNode pathItem, JsonNode operation);
  }

  private ObjectNode perOperation(JsonNode doc, OperationView view) {
    ObjectNode out = mapper.createObjectNode();
    forEachOperation(doc, (key, pathItem, op) -> out.set(key, view.of(doc, pathItem, op)));
    return sorted(out);
  }

  private JsonNode statusCodes(JsonNode doc, JsonNode pathItem, JsonNode op) {
    List<String> codes = new ArrayList<>();
    op.path("responses").fieldNames().forEachRemaining(codes::add);
    codes.sort(String::compareTo);
    ArrayNode a = mapper.createArrayNode();
    codes.forEach(a::add);
    return a;
  }

  private JsonNode responses(JsonNode doc, JsonNode pathItem, JsonNode op) {
    ObjectNode out = mapper.createObjectNode();
    op.path("responses")
        .fields()
        .forEachRemaining(e -> out.set(e.getKey(), content(doc, resolve(doc, e.getValue()).path("content"))));
    return sorted(out);
  }

  private JsonNode requestSide(JsonNode doc, JsonNode pathItem, JsonNode op) {
    ObjectNode out = mapper.createObjectNode();
    ObjectNode params = out.putObject("parameters");
    List<JsonNode> all = new ArrayList<>();
    pathItem.path("parameters").forEach(all::add);
    op.path("parameters").forEach(all::add); // operation-level wins over path-level for the same name+in
    for (JsonNode raw : all) {
      JsonNode p = resolve(doc, raw);
      String in = p.path("in").asText();
      ObjectNode view = mapper.createObjectNode();
      view.put("required", "path".equals(in) || p.path("required").asBoolean(false));
      view.set("schema", canon(doc, p.path("schema")));
      params.set(in + ":" + p.path("name").asText(), view);
    }
    sorted(params);
    if (op.has("requestBody")) {
      JsonNode body = resolve(doc, op.get("requestBody"));
      ObjectNode rb = out.putObject("requestBody");
      rb.put("required", body.path("required").asBoolean(false));
      rb.set("content", content(doc, body.path("content")));
    }
    return out;
  }

  private ObjectNode content(JsonNode doc, JsonNode content) {
    ObjectNode out = mapper.createObjectNode();
    content.fields().forEachRemaining(e -> out.set(e.getKey(), canon(doc, e.getValue().path("schema"))));
    return sorted(out);
  }

  // ---------------------------------------------------------------- schemas

  private ObjectNode schemas(JsonNode doc) {
    ObjectNode out = mapper.createObjectNode();
    doc.path("components")
        .path("schemas")
        .fields()
        .forEachRemaining(
            e -> {
              if (allOfMembers.contains(e.getKey())) {
                return;
              }
              ObjectNode c = canon(doc, e.getValue());
              // enums and primitives are inlined by springdoc; only object schemas are compared by name
              if ("object".equals(c.path("type").asText())) {
                out.set(e.getKey(), c);
              }
            });
    return sorted(out);
  }

  private Set<String> allOfMemberNames(JsonNode doc) {
    Set<String> names = new TreeSet<>();
    doc.path("components").path("schemas").forEach(s -> s.path("allOf").forEach(m -> {
      if (m.has("$ref")) {
        names.add(m.get("$ref").asText().substring(m.get("$ref").asText().lastIndexOf('/') + 1));
      }
    }));
    return names;
  }

  private JsonNode resolve(JsonNode doc, JsonNode node) {
    JsonNode n = node;
    int hops = 0;
    while (n.has("$ref")) {
      String ref = n.get("$ref").asText();
      assertThat(ref).as("only local refs are supported").startsWith("#/");
      n = doc.at(ref.substring(1));
      assertThat(n.isMissingNode()).as("dangling $ref %s", ref).isFalse();
      assertThat(++hops).as("$ref cycle").isLessThan(20);
    }
    return n;
  }

  /** Dereferences and flattens allOf into one schema node. */
  private JsonNode flatten(JsonNode doc, JsonNode schema) {
    JsonNode n = resolve(doc, schema);
    if (!n.has("allOf")) {
      return n;
    }
    ObjectNode out = mapper.createObjectNode();
    ObjectNode props = mapper.createObjectNode();
    Set<String> required = new TreeSet<>();
    List<JsonNode> parts = new ArrayList<>();
    n.get("allOf").forEach(parts::add);
    ObjectNode own = ((ObjectNode) n).deepCopy();
    own.remove("allOf");
    parts.add(0, own);
    for (JsonNode part : parts) {
      JsonNode p = flatten(doc, part);
      p.fields()
          .forEachRemaining(
              e -> {
                switch (e.getKey()) {
                  case "properties" -> props.setAll((ObjectNode) e.getValue());
                  case "required" -> e.getValue().forEach(r -> required.add(r.asText()));
                  default -> out.set(e.getKey(), e.getValue());
                }
              });
    }
    if (!props.isEmpty()) {
      out.set("properties", props);
    }
    if (!required.isEmpty()) {
      ArrayNode r = out.putArray("required");
      required.forEach(r::add);
    }
    return out;
  }

  private ObjectNode canon(JsonNode doc, JsonNode schema) {
    ObjectNode out = mapper.createObjectNode();
    if (schema == null || schema.isMissingNode()) {
      return out;
    }
    JsonNode n = flatten(doc, schema);

    Set<String> types = new TreeSet<>();
    boolean nullable = n.path("nullable").asBoolean(false);
    JsonNode t = n.get("type");
    if (t != null && t.isArray()) {
      for (JsonNode x : t) {
        if ("null".equals(x.asText())) {
          nullable = true;
        } else {
          types.add(x.asText());
        }
      }
    } else if (t != null) {
      types.add(t.asText());
    }
    if (types.isEmpty() && n.has("properties")) {
      types.add("object");
    }
    if (types.size() == 1) {
      out.put("type", types.iterator().next());
    } else if (types.size() > 1) {
      ArrayNode a = out.putArray("type");
      types.forEach(a::add);
    }
    if (nullable) {
      out.put("nullable", true);
    }
    if (n.has("enum")) {
      Set<String> values = new TreeSet<>();
      n.get("enum").forEach(v -> values.add(v.asText()));
      ArrayNode a = out.putArray("enum");
      values.forEach(a::add);
    }
    if (n.has("format") && !types.contains("integer") && !types.contains("number")) {
      out.put("format", n.get("format").asText());
    }
    if (n.has("properties")) {
      ObjectNode props = out.putObject("properties");
      n.get("properties").fields().forEachRemaining(e -> props.set(e.getKey(), canon(doc, e.getValue())));
      sorted(props);
    }
    if (n.has("required") && !n.get("required").isEmpty()) {
      Set<String> req = new TreeSet<>();
      n.get("required").forEach(r -> req.add(r.asText()));
      ArrayNode a = out.putArray("required");
      req.forEach(a::add);
    }
    if (n.has("items")) {
      out.set("items", canon(doc, n.get("items")));
    }
    return out;
  }

  private static ObjectNode sorted(ObjectNode node) {
    TreeMap<String, JsonNode> m = new TreeMap<>();
    node.fields().forEachRemaining(e -> m.put(e.getKey(), e.getValue()));
    node.removeAll();
    m.forEach(node::set);
    return node;
  }

  // ---------------------------------------------------------------- diff

  private void assertNoDiff(String what, List<String> diffs) {
    if (!diffs.isEmpty()) {
      fail("springdoc %s differ from docs/contract/openapi.yaml (%d differences):%n  %s", what, diffs.size(), String.join("\n  ", diffs));
    }
  }

  /** Readable differences between the contract (expected) and springdoc (actual). */
  private List<String> diff(JsonNode expected, JsonNode actual) {
    List<String> out = new ArrayList<>();
    diff("$", expected, actual, out);
    return out;
  }

  private void diff(String path, JsonNode exp, JsonNode act, List<String> out) {
    if (exp.isObject() && act.isObject()) {
      Set<String> keys = new TreeSet<>();
      exp.fieldNames().forEachRemaining(keys::add);
      act.fieldNames().forEachRemaining(keys::add);
      for (String k : keys) {
        if (!act.has(k)) {
          out.add("%s.%s: in contract, missing in springdoc: %s".formatted(path, k, brief(exp.get(k))));
        } else if (!exp.has(k)) {
          out.add("%s.%s: in springdoc, not in contract: %s".formatted(path, k, brief(act.get(k))));
        } else {
          diff(path + "." + k, exp.get(k), act.get(k), out);
        }
      }
    } else if (!exp.equals(act)) {
      out.add("%s: contract %s, springdoc %s".formatted(path, brief(exp), brief(act)));
    }
  }

  private static String brief(JsonNode n) {
    String s = n.toString();
    return s.length() > 160 ? s.substring(0, 160) + "..." : s;
  }
}
