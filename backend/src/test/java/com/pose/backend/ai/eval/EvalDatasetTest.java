package com.pose.backend.ai.eval;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import static org.assertj.core.api.Assertions.*;

class EvalDatasetTest {
    @TempDir Path dir;
    private static final String VALID="{\"sampleId\":\"S1\",\"industry\":\"EDU\",\"split\":\"test\",\"text\":\"synthetic secret\",\"goldIntent\":\"ASK_PRICE\",\"goldSentiment\":\"NEUTRAL\"}";
    @Test void reportsInvalidLinesAndDuplicatesWithoutEchoingText() throws Exception {
        Path file=dir.resolve("invalid.jsonl");
        Files.writeString(file,VALID+"\n"+VALID+"\n",StandardCharsets.UTF_8);
        assertThatThrownBy(() -> EvalDataset.read(file)).hasMessageContaining("line 2").hasMessageContaining("duplicate").hasMessageNotContaining("synthetic secret");
        Files.writeString(file,VALID.replace("ASK_PRICE","ALIEN"),StandardCharsets.UTF_8);
        assertThatThrownBy(() -> EvalDataset.read(file)).hasMessageContaining("line 1").hasMessageContaining("label");
        Files.writeString(file,"not json",StandardCharsets.UTF_8);
        assertThatThrownBy(() -> EvalDataset.read(file)).hasMessageContaining("line 1").hasMessageContaining("invalid JSON");
    }
    @Test void blocksNonIsolatedUrls() {
        for (String url : new String[]{"jdbc:postgresql://127.0.0.1:5432/pose_ai_eval_test", "jdbc:postgresql://db.example.supabase.com:54329/pose_ai_eval_test", "jdbc:postgresql://127.0.0.1:54329/pose_crm_local", "jdbc:h2:file:./local"})
            assertThatThrownBy(() -> EvalDatasourceGuard.validate(url)).isInstanceOf(IllegalArgumentException.class);
        assertThatCode(() -> EvalDatasourceGuard.validate("jdbc:postgresql://127.0.0.1:54329/pose_ai_eval_test")).doesNotThrowAnyException();
        assertThatCode(() -> EvalDatasourceGuard.validate("jdbc:h2:mem:test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")).doesNotThrowAnyException();
        assertThatThrownBy(() -> EvalDatasourceGuard.validateOverrides("jdbc:h2:mem:test", "jdbc:postgresql://example.supabase.com:5432/postgres", null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> EvalDatasourceGuard.validateOverrides("jdbc:h2:mem:test", null, "jdbc:postgresql://127.0.0.1:5432/pose")).isInstanceOf(IllegalArgumentException.class);
    }
}
