package com.aditya.minsearch.shared.infrastructure.openapi;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
		info = @Info(title = "Minsearch API", version = "v1", description = "Search and document management API"),
		servers = {@Server(url = "/", description = "Default server")})
@SecurityScheme(
		name = OpenApiConfiguration.BEARER_AUTH_SCHEME,
		type = SecuritySchemeType.HTTP,
		scheme = "bearer",
		bearerFormat = "JWT",
		in = SecuritySchemeIn.HEADER)
public class OpenApiConfiguration {

	public static final String BEARER_AUTH_SCHEME = "bearerAuth";
}
