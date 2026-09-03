package com.example.docs;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @GetMapping
    public List<Document> all() {
        return documentService.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Document> byId(@PathVariable String id) {
        return documentService.find(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/bulk-cleanup")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void bulkCleanup(@RequestBody BulkCleanupRequest request) {
        for (String id : request.documentIds()) {
            documentService.deleteDocument(id);
        }
    }

    public record BulkCleanupRequest(List<String> documentIds) {
    }
}
