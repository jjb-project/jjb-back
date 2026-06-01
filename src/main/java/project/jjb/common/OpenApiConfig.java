package project.jjb.common;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

	@Bean
	OpenAPI jjbOpenApi() {
		return new OpenAPI()
			.info(new Info()
				.title("jjb API")
				.version("v1")
				.description("긴급 대타/단기 알바 매칭 백엔드 API"));
	}
}
