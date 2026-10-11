package com.saaketh.budget.recurring;

import static org.assertj.core.api.Assertions.assertThat;

import com.saaketh.budget.recurring.RecurringDetector.Detection;
import com.saaketh.budget.recurring.SyntheticStatements.Dataset;
import com.saaketh.budget.recurring.SyntheticStatements.Label;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Measures the detector on {@value #USERS} synthetic students (18 months each) with known answers.
 *
 * <ul>
 *   <li>precision: of the charges we call recurring, how many really are</li>
 *   <li>recall: of the really recurring charges, how many we found</li>
 * </ul>
 * The data is synthetic, so these numbers show the detector handles the cases we thought of, not
 * how it does on real statements.
 */
class RecurringAccuracyTest {

    static final int USERS = 200;

    @Test
    void precisionAndRecallOnSyntheticStudents() {
        int truePositives = 0;
        int falsePositives = 0;
        int falseNegatives = 0;
        int rightCadence = 0;
        int rightActive = 0;
        List<String> mistakes = new ArrayList<>();

        for (long seed = 1; seed <= USERS; seed++) {
            Dataset data = SyntheticStatements.generate(seed);
            List<String> found = new ArrayList<>();
            for (Detection d : RecurringDetector.detect(data.charges(), data.asOf())) {
                found.add(d.merchantKey());
                Label label = data.labels().get(d.merchantKey());
                if (label == null || !label.recurring()) {
                    falsePositives++;
                    mistakes.add("seed " + seed + " false positive: " + d.merchantKey() + " " + d.cadence());
                    continue;
                }
                truePositives++;
                rightCadence += d.cadence() == label.cadence() ? 1 : 0;
                rightActive += d.active() == label.active() ? 1 : 0;
            }
            for (var entry : data.labels().entrySet()) {
                if (entry.getValue().recurring() && !found.contains(entry.getKey())) {
                    falseNegatives++;
                    mistakes.add("seed " + seed + " missed: " + entry.getKey());
                }
            }
        }

        double precision = (double) truePositives / (truePositives + falsePositives);
        double recall = (double) truePositives / (truePositives + falseNegatives);
        System.out.printf("Recurring detection on %d synthetic users: precision %.3f, recall %.3f, "
                        + "cadence right %.3f, active/cancelled right %.3f (TP %d, FP %d, FN %d)%n",
                USERS, precision, recall, (double) rightCadence / truePositives,
                (double) rightActive / truePositives, truePositives, falsePositives, falseNegatives);
        mistakes.stream().limit(20).forEach(System.out::println);

        assertThat(precision).isGreaterThanOrEqualTo(0.95);
        assertThat(recall).isGreaterThanOrEqualTo(0.95);
        assertThat(rightCadence).isEqualTo(truePositives);
        assertThat(rightActive).isEqualTo(truePositives);
    }
}
