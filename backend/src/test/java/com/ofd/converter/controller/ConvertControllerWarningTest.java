package com.ofd.converter.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ConvertControllerWarningTest {

    @Autowired
    MockMvc mvc;

    @Test
    void formatsAdvertisesDocxAndMd() throws Exception {
        mvc.perform(get("/api/formats"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.ofd").value(
                org.hamcrest.Matchers.hasItems("pdf", "png", "jpg", "txt", "docx", "md")));
    }
}
