package com.jiwoo.wastesorting_server.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

/**
 * Swagger UI 설정. 주소: /swagger-ui/index.html
 * 오른쪽 위 [Authorize]에 기기 토큰(dev_...) 또는 로그인 accessToken을 넣으면 모든 요청에 붙는다.
 */
@Configuration
public class OpenApiConfig {

	private static final String BEARER = "bearer";

	@Bean
	OpenAPI openAPI() {
		return new OpenAPI()
				.info(new Info()
						.title("버림가이드 API")
						.version("v1")
						.description("사진·글자로 한국 분리배출 방법을 안내하는 Android 앱의 서버. "
								+ "먼저 POST /api/v1/auth/device 로 기기 토큰을 받아 [Authorize]에 넣으세요."))
				.components(new Components().addSecuritySchemes(BEARER, new SecurityScheme()
						.type(SecurityScheme.Type.HTTP)
						.scheme("bearer")
						.description("기기 토큰(dev_...) 또는 로그인 후 받은 accessToken")))
				.addSecurityItem(new SecurityRequirement().addList(BEARER));
	}
}
