package com.saaketh.budget.imports;

import com.saaketh.budget.auth.AuthenticatedUser;
import com.saaketh.budget.imports.ImportService.ImportResult;
import com.saaketh.budget.imports.ImportService.ImportSummary;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/imports")
public class ImportController {

    private final ImportService importService;

    public ImportController(ImportService importService) {
        this.importService = importService;
    }

    /**
     * Upload a statement as multipart/form-data with fields "accountId" and "file".
     * 201 Created if new transactions were saved; 200 OK if every row was already imported.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImportResult> upload(@AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam("accountId") Long accountId,
            @RequestParam("file") MultipartFile file) {
        ImportResult result = importService.importStatement(user.getId(), accountId, file);
        return ResponseEntity.status(result.importBatchId() == null ? HttpStatus.OK : HttpStatus.CREATED).body(result);
    }

    /** Your past imports, newest first. */
    @GetMapping
    public List<ImportSummary> list(@AuthenticationPrincipal AuthenticatedUser user) {
        return importService.list(user.getId());
    }

    /** Undo an import: deletes it and the transactions it added. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) {
        importService.delete(user.getId(), id);
    }
}
