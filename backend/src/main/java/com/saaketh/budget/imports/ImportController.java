package com.saaketh.budget.imports;

import com.saaketh.budget.auth.AuthenticatedUser;
import com.saaketh.budget.imports.ImportService.ImportResult;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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

    /** Upload a statement as multipart/form-data with fields "accountId" and "file". */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ImportResult upload(@AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam("accountId") Long accountId,
            @RequestParam("file") MultipartFile file) {
        return importService.importStatement(user.getId(), accountId, file);
    }
}
