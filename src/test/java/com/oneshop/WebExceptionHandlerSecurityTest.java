package com.oneshop;

import com.oneshop.exception.WebExceptionHandler;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import static org.assertj.core.api.Assertions.assertThat;

class WebExceptionHandlerSecurityTest {
    @ParameterizedTest
    @ValueSource(strings={"https://attacker.example/admin/", "//attacker.example/admin/", "/\\attacker.example/admin/", "/admin/../login"})
    void oversizedUploadCannotRedirectToAnUntrustedReferer(String referer) {
        var request=new MockHttpServletRequest("POST","/unknown-upload");
        request.addHeader("Referer",referer);
        assertThat(new WebExceptionHandler().handleMaxUploadSizeExceeded(request,new MaxUploadSizeExceededException(1)))
                .isEqualTo("redirect:/admin/products?error=image-size");
    }
}
