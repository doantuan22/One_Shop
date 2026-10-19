package com.oneshop;

import com.oneshop.controller.api.AuthApiController;
import com.oneshop.exception.GlobalExceptionHandler;
import com.oneshop.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ApiInputValidationTest {
    private final MockMvc mvc=MockMvcBuilders.standaloneSetup(new AuthApiController(mock(AuthService.class)))
            .setControllerAdvice(new GlobalExceptionHandler()).build();

    @Test void malformedJsonIsAClientErrorWithNoParserLeak() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"password\": malformed"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("status").value(400))
                .andExpect(jsonPath("message").value("Dữ liệu JSON không hợp lệ"));
    }
    @Test void unsupportedContentTypeKeepsItsHttpStatus() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.TEXT_PLAIN).content("untrusted"))
                .andExpect(status().isUnsupportedMediaType());
    }
}
