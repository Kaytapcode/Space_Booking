package com.spacebooking.contract;

import static org.assertj.core.api.Assertions.assertThat;

import com.spacebooking.support.AbstractIntegrationTest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.init.ScriptUtils;

/**
 * R50 (G14): the context only started because Hibernate validated the entities against the Flyway-built schema
 * (ddl-auto=validate); here Flyway must have applied every migration, and the result must equal docs/contract/schema.sql.
 */
class SchemaIT extends AbstractIntegrationTest {

  private static final String REFERENCE = "schema_sql_reference";

  @Autowired DataSource dataSource;
  @Autowired Environment environment;

  @Test
  void flywayMigrates_andHibernateValidates() throws Exception {
    assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
    // Flyway applied every migration file on the classpath, none failed
    Resource[] migrations = new PathMatchingResourcePatternResolver().getResources("classpath:db/migration/V*__*.sql");
    assertThat(migrations).isNotEmpty();
    assertThat(jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE success", Integer.class)).isEqualTo(migrations.length);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE NOT success", Integer.class)).isZero();

    // Flyway-built schema (public) == schema.sql applied verbatim to a scratch schema of the same database
    Path schemaSql = Path.of("docs/contract/schema.sql");
    if (!Files.exists(schemaSql)) {
      schemaSql = Path.of("../docs/contract/schema.sql");
    }
    String ddl = Files.readString(schemaSql, StandardCharsets.UTF_8);
    jdbc.execute("DROP SCHEMA IF EXISTS " + REFERENCE + " CASCADE");
    jdbc.execute("CREATE SCHEMA " + REFERENCE);
    try {
      try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
        s.execute("SET search_path TO " + REFERENCE + ", public"); // public keeps btree_gist operators visible
        ScriptUtils.executeSqlScript(c, new ByteArrayResource(ddl.getBytes(StandardCharsets.UTF_8)));
        s.execute("RESET search_path");
      }
      assertThat(describe(REFERENCE)).isNotEmpty();
      assertThat(describe("public")).containsExactlyInAnyOrderElementsOf(describe(REFERENCE));
    } finally {
      jdbc.execute("DROP SCHEMA IF EXISTS " + REFERENCE + " CASCADE");
    }
  }

  /** Columns, constraints and indexes of one schema as comparable text (schema qualifier removed). */
  private List<String> describe(String schema) {
    String strip = "(?i)\\b(" + schema + "|public)\\.";
    List<String> out = new ArrayList<>();
    jdbc.query(
        "SELECT table_name, column_name, ordinal_position, data_type, is_nullable, character_maximum_length, column_default"
            + " FROM information_schema.columns WHERE table_schema = ? AND table_name <> 'flyway_schema_history'",
        rs -> {
          out.add(
              "column %s.%s #%d %s null=%s len=%s default=%s"
                  .formatted(
                      rs.getString(1), rs.getString(2), rs.getInt(3), rs.getString(4), rs.getString(5), rs.getString(6),
                      String.valueOf(rs.getString(7)).replaceAll(strip, "")));
        },
        schema);
    jdbc.query(
        "SELECT c.relname, k.conname, pg_get_constraintdef(k.oid) FROM pg_constraint k"
            + " JOIN pg_class c ON c.oid = k.conrelid JOIN pg_namespace n ON n.oid = c.relnamespace"
            + " WHERE n.nspname = ? AND c.relname <> 'flyway_schema_history'",
        rs -> {
          out.add("constraint %s.%s %s".formatted(rs.getString(1), rs.getString(2), rs.getString(3).replaceAll(strip, "")));
        },
        schema);
    jdbc.query(
        "SELECT tablename, indexname, indexdef FROM pg_indexes WHERE schemaname = ? AND tablename <> 'flyway_schema_history'",
        rs -> {
          out.add("index %s.%s %s".formatted(rs.getString(1), rs.getString(2), rs.getString(3).replaceAll(strip, "")));
        },
        schema);
    return out;
  }
}
