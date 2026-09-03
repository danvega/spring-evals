package com.example.docs;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Hidden eval assertions: authorization for the sensitive operations must be
 * enforced on every invocation path, not only at the web entry points. Each
 * test method targets its own document ids so execution order does not matter.
 */
@SpringBootTest
@AutoConfigureMockMvc
class MethodSecurityEvalTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    DocumentService documentService;

    @Test
    void bulkCleanupAsPlainUserIsDeniedAndDocumentsSurvive() throws Exception {
        mockMvc.perform(post("/api/documents/bulk-cleanup")
                .with(user("eve").roles("USER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"documentIds\":[\"doc-1\",\"doc-2\"]}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/documents/doc-1").with(user("eve").roles("USER")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/documents/doc-2").with(user("eve").roles("USER")))
                .andExpect(status().isOk());
    }

    @Test
    void adminEndpointsStillWorkForAdmins() throws Exception {
        mockMvc.perform(delete("/api/admin/documents/doc-3").with(user("root").roles("ADMIN")))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/documents/doc-3").with(user("root").roles("ADMIN")))
                .andExpect(status().isNotFound());

        mockMvc.perform(put("/api/admin/documents/doc-4/owner")
                .with(user("root").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"owner\":\"carol\"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/documents/doc-4").with(user("root").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.owner").value("carol"));
    }

    @Test
    void bulkCleanupStillWorksForAdmins() throws Exception {
        mockMvc.perform(post("/api/documents/bulk-cleanup")
                .with(user("root").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"documentIds\":[\"doc-5\"]}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/documents/doc-5").with(user("root").roles("ADMIN")))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "eve", roles = "USER")
    void sensitiveOperationsAreDeniedOnDirectInvocationAsUser() throws Exception {
        assertThatThrownBy(() -> documentService.deleteDocument("doc-6"))
                .as("deleting a document as a plain USER must be denied at the operation itself, "
                        + "not only at whichever web endpoints have rules today")
                .isInstanceOf(AccessDeniedException.class);

        assertThatThrownBy(() -> documentService.changeOwner("doc-6", "eve"))
                .as("changing an owner as a plain USER must be denied at the operation itself")
                .isInstanceOf(AccessDeniedException.class);

        mockMvc.perform(get("/api/documents/doc-6").with(user("eve").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.owner").value("alice"));
    }

    @Test
    @WithMockUser(username = "root", roles = "ADMIN")
    void sensitiveOperationsStillWorkOnDirectInvocationAsAdmin() throws Exception {
        documentService.changeOwner("doc-7", "dave");

        mockMvc.perform(get("/api/documents/doc-7").with(user("root").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.owner").value("dave"));
    }
}
