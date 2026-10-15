package com.saaketh.budget.category;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.json.JsonMapper;

/**
 * Trained in Python, served in Java: the same inputs must give the same outputs. The cases (with
 * Python's n-grams and probabilities) are written by ml/budget_ml/train.py from the exported model.
 */
class CategoryModelParityTest {

    record Case(String description, String normalized, List<String> ngrams, double[] probabilities) {
    }

    record Cases(List<String> classes, List<Case> cases) {
    }

    private static CategoryModel model;
    private static Cases cases;

    @BeforeAll
    static void load() throws IOException {
        model = new CategoryModel();
        try (var in = new ClassPathResource("ml/parity-cases.json").getInputStream()) {
            cases = JsonMapper.builder().build().readValue(in, Cases.class);
        }
    }

    @Test
    void classesAreInTheSameOrder() {
        assertThat(model.classes()).isEqualTo(cases.classes());
    }

    @Test
    void textCleanupAndNgramsMatchPython() {
        assertThat(cases.cases()).hasSizeGreaterThan(100);
        for (Case c : cases.cases()) {
            assertThat(CategoryModel.normalize(c.description())).as(c.description()).isEqualTo(c.normalized());
            assertThat(CategoryModel.ngrams(c.normalized(), 3, 5)).as(c.description()).isEqualTo(c.ngrams());
        }
    }

    @Test
    void probabilitiesMatchPython() {
        for (Case c : cases.cases()) {
            double[] got = model.probabilities(c.description());
            assertThat(got).as(c.description()).hasSameSizeAs(c.probabilities());
            for (int i = 0; i < got.length; i++) {
                // Only floating-point summation order differs between the two languages.
                assertThat(got[i]).as(c.description() + " / " + cases.classes().get(i))
                        .isCloseTo(c.probabilities()[i], within(1e-9));
            }
        }
    }

    @Test
    void suggestsOnlyWhenConfident() {
        assertThat(model.threshold()).isBetween(0.5, 0.99);
        assertThat(model.suggest("DOMINOSPIZZA 4471 CHAPEL HILL NC"))
                .hasValueSatisfying(s -> assertThat(s.categoryName()).isEqualTo("Food & Dining"));
        assertThat(model.suggest("1234 5678")).isEmpty(); // nothing to go on
    }
}
