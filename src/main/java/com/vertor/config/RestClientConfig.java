package com.vertor.config;

import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;

import java.util.ArrayList;
import java.util.List;

/**
 * RestClient 定制配置：解决通义千问等第三方 OpenAI 兼容 API
 * 返回 Content-Type: application/octet-stream 导致反序列化失败的问题。
 * <p>
 * 通过将 APPLICATION_OCTET_STREAM 加入 Jackson 转换器的支持媒体类型，
 * 使 RestClient 能够将其作为 JSON 进行反序列化。
 */
@Configuration
public class RestClientConfig {

	@Bean
	public RestClientCustomizer octetStreamJsonRestClientCustomizer() {
		return builder -> builder.messageConverters(converters -> {
			for (int i = 0; i < converters.size(); i++) {
				if (converters.get(i) instanceof MappingJackson2HttpMessageConverter jacksonConverter) {
					List<MediaType> supportedMediaTypes = new ArrayList<>(jacksonConverter.getSupportedMediaTypes());
					supportedMediaTypes.add(MediaType.APPLICATION_OCTET_STREAM);
					jacksonConverter.setSupportedMediaTypes(supportedMediaTypes);
					break;
				}
			}
		});
	}

}
