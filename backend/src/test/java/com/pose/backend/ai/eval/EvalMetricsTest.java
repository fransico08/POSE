package com.pose.backend.ai.eval;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static com.pose.backend.ai.eval.EvalMetrics.*;

class EvalMetricsTest {
    @Test void scoresGoldClassesAndMissingPredictions() {
        var result = calculate(List.of(new Pair("A","A"), new Pair("A","B"),
                new Pair("B",NO_PREDICTION), new Pair("C","A")));
        assertThat(result.samples()).isEqualTo(4);
        assertThat(result.accuracy()).isEqualTo(.25);
        assertThat(result.coverage()).isEqualTo(.75);
        assertThat(result.classes().get("A").precision()).isEqualTo(.5);
        assertThat(result.classes().get("A").recall()).isEqualTo(.5);
        assertThat(result.classes().get("A").f1()).isEqualTo(.5);
        assertThat(result.classes().get("B").f1()).isZero();
        assertThat(result.classes().get("C").f1()).isZero();
        assertThat(result.macroF1()).isCloseTo(1.0/6, within(1e-12));
        assertThat(result.confusion().get("B").get(NO_PREDICTION)).isEqualTo(1L);
    }

    @Test void computesCohensKappaFromIndependentLabels() {
        var pairs = List.of(new Pair("A","A"), new Pair("A","A"),
                new Pair("A","B"), new Pair("B","B"));
        assertThat(kappa(pairs)).isEqualTo(.5);
        assertThat(kappa(List.of(new Pair("A","A")))).isNull();
    }
    @Test void reportsPredictedOnlyClassButDoesNotIncludeItInMacroAverage() {
        var result=calculate(List.of(new Pair("A","A"),new Pair("A","B")));
        assertThat(result.classes().get("B").support()).isZero();
        assertThat(result.classes().get("B").f1()).isZero();
        assertThat(result.macroF1()).isCloseTo(2.0/3,within(1e-12));
    }
}
