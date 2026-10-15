package com.saaketh.budget.category;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * The machine-learning category model, trained in Python (see ml/) and run here. It suggests a
 * category for a description no rule or dictionary word covers, e.g. "DOMINOSPIZZA 4471" (glued
 * words) or "GOLDEN DRAGON KITC" (cut off).
 *
 * <p>How a prediction works, step for step the same as ml/budget_ml/model.py predict_exported:
 * <ol>
 *   <li>clean the text (uppercase, letters/digits only, drop words with digits);</li>
 *   <li>cut each word into overlapping 3-5 letter pieces (" PI", "PIZ", "IZZ", ...);</li>
 *   <li>weigh each known piece: (1 + ln count) * idf, then scale the vector to length 1 (TF-IDF);</li>
 *   <li>score each category: intercept + sum(weight * that category's coefficient);</li>
 *   <li>turn the scores into probabilities that add up to 1 (softmax).</li>
 * </ol>
 * The parity test (CategoryModelParityTest) checks these steps against Python's output.
 */
@Component
public class CategoryModel {

    static final String RESOURCE = "ml/category-model.json";
    static final String FORMAT = "budget-tracker-category-model/1";

    /** A category the model is confident about, with how confident (0..1). */
    public record Suggestion(String categoryName, double confidence) {
    }

    /** The exported JSON, as written by ml/budget_ml/model.py export(). */
    record ModelFile(String format, String description, int ngramMin, int ngramMax, double suggestThreshold,
            List<String> classes, Map<String, Integer> vocabulary, double[] idf, double[][] coef, double[] intercept) {
    }

    private final ModelFile model;

    public CategoryModel() throws IOException {
        this(load(new ClassPathResource(RESOURCE).getInputStream()));
    }

    CategoryModel(ModelFile model) {
        if (!FORMAT.equals(model.format())) {
            throw new IllegalStateException("Unsupported category model format: " + model.format());
        }
        this.model = model;
    }

    static ModelFile load(InputStream in) throws IOException {
        try (in) {
            return JsonMapper.builder().build().readValue(in, ModelFile.class);
        }
    }

    public double threshold() {
        return model.suggestThreshold();
    }

    /** The most likely category, only if the model is at least as sure as the trained cutoff. */
    public Optional<Suggestion> suggest(String description) {
        double[] probabilities = probabilities(description);
        int best = 0;
        for (int i = 1; i < probabilities.length; i++) {
            if (probabilities[i] > probabilities[best]) {
                best = i;
            }
        }
        return probabilities[best] >= model.suggestThreshold()
                ? Optional.of(new Suggestion(model.classes().get(best), probabilities[best]))
                : Optional.empty();
    }

    /** One probability per class, in the order of classes(). */
    double[] probabilities(String description) {
        Map<Integer, Integer> counts = new HashMap<>();
        for (String gram : ngrams(normalize(description), model.ngramMin(), model.ngramMax())) {
            Integer index = model.vocabulary().get(gram);
            if (index != null) {
                counts.merge(index, 1, Integer::sum);
            }
        }
        Map<Integer, Double> weights = new HashMap<>();
        double squares = 0;
        for (Map.Entry<Integer, Integer> e : counts.entrySet()) {
            double w = (1 + Math.log(e.getValue())) * model.idf()[e.getKey()];
            weights.put(e.getKey(), w);
            squares += w * w;
        }
        double norm = Math.sqrt(squares);

        double[] scores = new double[model.classes().size()];
        for (int c = 0; c < scores.length; c++) {
            double score = model.intercept()[c];
            for (Map.Entry<Integer, Double> e : weights.entrySet()) {
                score += model.coef()[c][e.getKey()] * (norm > 0 ? e.getValue() / norm : 0);
            }
            scores[c] = score;
        }
        double top = Arrays.stream(scores).max().orElse(0);
        double[] exps = Arrays.stream(scores).map(s -> Math.exp(s - top)).toArray(); // shifted: no overflow
        double total = Arrays.stream(exps).sum();
        return Arrays.stream(exps).map(e -> e / total).toArray();
    }

    List<String> classes() {
        return model.classes();
    }

    /**
     * "PURCHASE AUTHORIZED ON 10/21 Domino's #123" -> "PURCHASE AUTHORIZED ON DOMINO S".
     * Same as ml/budget_ml/text.py normalize: words with digits (dates, store numbers) are dropped.
     */
    static String normalize(String description) {
        return Arrays.stream(description.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", " ").split(" "))
                .filter(w -> !w.isEmpty())
                .filter(w -> w.chars().noneMatch(Character::isDigit))
                .collect(Collectors.joining(" "));
    }

    /** Character n-grams within words, exactly like scikit-learn's analyzer="char_wb". */
    static List<String> ngrams(String text, int min, int max) {
        List<String> grams = new ArrayList<>();
        for (String word : text.split(" ")) {
            if (word.isEmpty()) {
                continue;
            }
            String padded = " " + word + " ";
            for (int n = min; n <= max; n++) {
                int offset = 0;
                grams.add(padded.substring(offset, Math.min(offset + n, padded.length())));
                while (offset + n < padded.length()) {
                    offset++;
                    grams.add(padded.substring(offset, offset + n));
                }
                if (offset == 0) { // word shorter than n: longer n-grams would repeat the whole word
                    break;
                }
            }
        }
        return grams;
    }
}
