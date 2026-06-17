package project.jjb.common;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 업로드된 이미지를 /uploads/** 경로로 서빙한다.
 */
@Configuration
public class WebStaticConfig implements WebMvcConfigurer {

	private final FileStorageService fileStorageService;

	public WebStaticConfig(FileStorageService fileStorageService) {
		this.fileStorageService = fileStorageService;
	}

	@Override
	public void addResourceHandlers(ResourceHandlerRegistry registry) {
		registry.addResourceHandler("/uploads/**")
			.addResourceLocations(fileStorageService.directory().toUri().toString());
	}
}
