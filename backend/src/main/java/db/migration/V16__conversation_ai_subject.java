package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Replaces the unnamed V12 check, whose generated name differs across databases. */
public class V16__conversation_ai_subject extends BaseJavaMigration {
    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        List<String> checks = new ArrayList<>();
        String sql = """
                SELECT tc.constraint_name, cc.check_clause
                FROM information_schema.table_constraints tc
                JOIN information_schema.check_constraints cc
                  ON cc.constraint_catalog = tc.constraint_catalog
                 AND cc.constraint_schema = tc.constraint_schema
                 AND cc.constraint_name = tc.constraint_name
                WHERE LOWER(tc.table_name) = 'ai_invocations'
                  AND tc.constraint_type = 'CHECK'
                  AND LOWER(tc.table_schema) = LOWER(?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, connection.getSchema());
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    String clause = rows.getString(2).toUpperCase(Locale.ROOT);
                    // PostgreSQL 18 also exposes the column's NOT NULL constraint as a CHECK.
                    if (clause.contains("SUBJECT_TYPE") && clause.contains("'CUSTOMER'") && clause.contains("'LEAD'")) {
                        checks.add(rows.getString(1));
                    }
                }
            }
        }
        if (checks.size() != 1) throw new IllegalStateException("Expected one AI invocation subject check");
        String quote = connection.getMetaData().getIdentifierQuoteString();
        String name = quote + checks.getFirst().replace(quote, quote + quote) + quote;
        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE ai_invocations DROP CONSTRAINT " + name);
            statement.execute("ALTER TABLE ai_invocations ADD CONSTRAINT ck_ai_invocation_subject_type "
                    + "CHECK (subject_type IN ('CUSTOMER','LEAD','CONVERSATION'))");
        }
    }
}
