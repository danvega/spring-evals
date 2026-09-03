package com.example.docs;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

@Service
public class DocumentService {

    private final Map<String, Document> documents = new ConcurrentHashMap<>();

    public DocumentService() {
        save(new Document("doc-1", "Q3 Financial Report", "alice"));
        save(new Document("doc-2", "Board Meeting Minutes", "alice"));
        save(new Document("doc-3", "Vendor Contract Draft", "bob"));
        save(new Document("doc-4", "Security Audit 2025", "alice"));
        save(new Document("doc-5", "Onboarding Checklist", "bob"));
        save(new Document("doc-6", "Product Roadmap", "alice"));
        save(new Document("doc-7", "Incident Postmortem", "alice"));
    }

    public List<Document> findAll() {
        return documents.values().stream()
                .sorted(Comparator.comparing(Document::getId))
                .toList();
    }

    public Optional<Document> find(String id) {
        return Optional.ofNullable(documents.get(id));
    }

    public void deleteDocument(String id) {
        documents.remove(id);
    }

    public void changeOwner(String id, String newOwner) {
        Document document = documents.get(id);
        if (document != null) {
            document.setOwner(newOwner);
        }
    }

    private void save(Document document) {
        documents.put(document.getId(), document);
    }
}
