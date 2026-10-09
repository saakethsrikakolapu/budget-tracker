package com.saaketh.budget.imports;

import com.saaketh.budget.imports.csv.ColumnInference;
import com.saaketh.budget.imports.csv.ColumnMapping;
import com.saaketh.budget.imports.csv.CsvTable;
import com.saaketh.budget.imports.parser.StatementParseException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Decides how to read a statement file. Priority: the column choices the user just made in the
 * preview, then a format they saved for files shaped like this one, then automatic inference.
 */
@Service
public class StatementReader {

    public enum Source {
        /** The user chose the columns in the preview. */
        CUSTOM,
        /** A format the user saved earlier for files with this shape. */
        SAVED,
        /** Worked out automatically from the values (ColumnInference). */
        DETECTED
    }

    /** @param mapping null when inference failed and nothing else applies */
    public record Plan(CsvTable table, ColumnInference.Layout layout, String signature, ColumnMapping mapping,
            Source source, List<String> warnings, String failure) {
    }

    static final int MAX_SIGNATURE_LENGTH = 2000;

    private final ImportFormatRepository formatRepository;
    private final JsonMapper json;

    public StatementReader(ImportFormatRepository formatRepository, JsonMapper json) {
        this.formatRepository = formatRepository;
        this.json = json;
    }

    @Transactional(readOnly = true)
    public Plan plan(Long userId, String csvText, String customMappingJson) {
        CsvTable table = CsvTable.read(csvText);
        ColumnInference.Layout layout = ColumnInference.layout(table);
        String signature = signature(table, layout);

        if (customMappingJson != null && !customMappingJson.isBlank()) {
            return new Plan(table, layout, signature, parseMapping(customMappingJson), Source.CUSTOM, List.of(), null);
        }
        Optional<ImportFormat> saved = formatRepository.findByUserIdAndSignature(userId, signature);
        if (saved.isPresent()) {
            return new Plan(table, layout, signature, parseMapping(saved.get().getMapping()), Source.SAVED, List.of(), null);
        }
        try {
            ColumnInference.Result inferred = ColumnInference.infer(table);
            return new Plan(table, layout, signature, inferred.mapping(), Source.DETECTED, inferred.warnings(), null);
        } catch (StatementParseException e) {
            return new Plan(table, layout, signature, null, Source.DETECTED, List.of(), e.getErrors().getFirst());
        }
    }

    /** Remember (or update) the column choices for files shaped like this one. */
    @Transactional
    public void remember(Long userId, String signature, ColumnMapping mapping) {
        String mappingJson = json.writeValueAsString(mapping);
        formatRepository.findByUserIdAndSignature(userId, signature)
                .ifPresentOrElse(
                        existing -> existing.setMapping(mappingJson),
                        () -> formatRepository.save(new ImportFormat(userId, signature, mappingJson)));
    }

    /**
     * Identifies files that share a format: the header names (case and spacing ignored), or the
     * column count when there's no header. Two exports from the same bank get the same signature.
     */
    static String signature(CsvTable table, ColumnInference.Layout layout) {
        String signature = layout.headerRow() >= 0
                ? "header:" + layout.columnNames().stream()
                        .map(n -> n.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT))
                        .collect(Collectors.joining("|"))
                : "no-header:" + table.columnCount() + " columns";
        return signature.length() > MAX_SIGNATURE_LENGTH ? signature.substring(0, MAX_SIGNATURE_LENGTH) : signature;
    }

    private ColumnMapping parseMapping(String mappingJson) {
        try {
            return json.readValue(mappingJson, ColumnMapping.class);
        } catch (JacksonException e) {
            throw new InvalidUploadException("The column choices couldn't be read. Choose the columns again.");
        }
    }
}
