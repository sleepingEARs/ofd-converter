package com.ofd.converter.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ConvertControllerTest {

    @Autowired
    MockMvc mvc;

    @Test
    void healthOk() throws Exception {
        mvc.perform(get("/health")).andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ok"));
    }

    @Test
    void formatsReturnsGrouped() throws Exception {
        mvc.perform(get("/api/formats")).andExpect(status().isOk())
            .andExpect(jsonPath("$.ofd").isArray())
            .andExpect(jsonPath("$.pdf[0]").value("ofd"));
    }
}
