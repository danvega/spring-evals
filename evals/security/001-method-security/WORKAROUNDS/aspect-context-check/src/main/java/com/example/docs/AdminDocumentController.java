package com.example.docs;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/documents")
public class AdminDocumentController {

    private final DocumentService documentService;

    public AdminDocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        documentService.deleteDocument(id);
    }

    @PutMapping("/{id}/owner")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changeOwner(@PathVariable String id, @RequestBody OwnerChangeRequest request) {
        documentService.changeOwner(id, request.owner());
    }

    public record OwnerChangeRequest(String owner) {
    }
}
