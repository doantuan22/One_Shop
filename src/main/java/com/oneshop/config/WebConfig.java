package com.oneshop.config;

import com.oneshop.security.StaffStoreScopeInterceptor;
import com.oneshop.web.SelectedStoreInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.concurrent.TimeUnit;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final StaffStoreScopeInterceptor staffStoreScopeInterceptor;
    private final SelectedStoreInterceptor selectedStoreInterceptor;

    public WebConfig(StaffStoreScopeInterceptor staffStoreScopeInterceptor,
                     SelectedStoreInterceptor selectedStoreInterceptor) {
        this.staffStoreScopeInterceptor = staffStoreScopeInterceptor;
        this.selectedStoreInterceptor = selectedStoreInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Client pages (and the error page, which uses the Client header): Store being browsed, if any.
        registry.addInterceptor(selectedStoreInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns("/admin", "/admin/**", "/staff", "/staff/**", "/api/**", "/decorators/**",
                        "/health", "/css/**", "/js/**", "/images/**", "/vendor/**");
        // Runs after Spring Security has authenticated the request and checked the STAFF role.
        registry.addInterceptor(staffStoreScopeInterceptor)
                .addPathPatterns("/staff", "/staff/**", "/api/staff/**");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Third-party libraries in /vendor never change without a version bump.
        registry.addResourceHandler("/vendor/**")
                .addResourceLocations("classpath:/static/vendor/")
                .setCacheControl(CacheControl.maxAge(30, TimeUnit.DAYS).cachePublic());
    }
}
