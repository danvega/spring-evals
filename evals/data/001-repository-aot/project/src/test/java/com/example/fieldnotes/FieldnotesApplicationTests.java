package com.example.fieldnotes;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@SpringBootTest
@AutoConfigureMockMvc
class FieldnotesApplicationTests {

    @Autowired
    MockMvcTester mvc;

    @Test
    void activeCustomersAreListed() {
        mvc.get().uri("/api/customers/active")
                .assertThat()
                .hasStatusOk()
                .bodyText()
                .contains("Lovelace")
                .contains("Hopper")
                .doesNotContain("Dijkstra");
    }

    @Test
    void overdueInvoicesAreListed() {
        mvc.get().uri("/api/invoices/overdue")
                .assertThat()
                .hasStatusOk()
                .bodyText()
                .contains("120");
    }
}
