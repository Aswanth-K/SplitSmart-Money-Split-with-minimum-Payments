package com.splitsmart.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.ShallowEtagHeaderFilter;

@Configuration
public class WebConfiguration {
    @Bean
    FilterRegistrationBean<ShallowEtagHeaderFilter> staticAssetEtags() {
        var registration = new FilterRegistrationBean<>(new ShallowEtagHeaderFilter());
        registration.addUrlPatterns("/", "/index.html", "/app.js", "/style.css");
        return registration;
    }
}
