package com.leeburke.springgame.api;

import java.util.Objects;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.leeburke.springgame.config.GameApiProperties;

/**
 * Web wiring for the API: the run-token interceptor on run paths, the body size limit, and CORS
 * only for explicitly configured origins (off by default, since the PWA will be served same-origin).
 */
@Configuration(proxyBeanMethods = false)
class ApiWebConfiguration implements WebMvcConfigurer {

	private final RunTokenInterceptor runTokens;
	private final GameApiProperties properties;

	ApiWebConfiguration(RunTokenInterceptor runTokens, GameApiProperties properties) {
		this.runTokens = Objects.requireNonNull(runTokens, "runTokens");
		this.properties = Objects.requireNonNull(properties, "properties");
	}

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(runTokens).addPathPatterns("/api/v1/runs/*", "/api/v1/runs/*/**");
	}

	@Override
	public void addCorsMappings(CorsRegistry registry) {
		if (!properties.corsOrigins().isEmpty()) {
			registry.addMapping("/api/**").allowedOrigins(properties.corsOrigins().toArray(String[]::new))
					.allowedMethods("GET", "POST").allowedHeaders("Authorization", "Content-Type", "Idempotency-Key", "X-Invite-Code");
		}
	}

	/**
	 * Static so the servlet container can register the filter early, during its own startup, without
	 * instantiating this configuration and its dependencies (which need JPA) too soon.
	 */
	@Bean
	static FilterRegistrationBean<RequestSizeLimitFilter> requestSizeLimitFilter() {
		FilterRegistrationBean<RequestSizeLimitFilter> registration = new FilterRegistrationBean<>(new RequestSizeLimitFilter());
		registration.addUrlPatterns("/api/*");
		return registration;
	}
}
