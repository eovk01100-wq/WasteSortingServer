package com.jiwoo.wastesorting_server.gemini;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.jiwoo.wastesorting_server.common.ApiException;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/** Gemini REST API(generateContent)를 실제로 호출한다. API 키는 .env의 GEMINI_API_KEY */
@Component
public class HttpGeminiClient implements GeminiClient {

	private static final Logger log = LoggerFactory.getLogger(HttpGeminiClient.class);
	private static final JsonMapper JSON = JsonMapper.builder().build();

	private final String apiKey;
	private final String model;
	private final RestClient rest;

	public HttpGeminiClient(
			@Value("${gemini.api-key:}") String apiKey,
			@Value("${gemini.model}") String model) {
		this.apiKey = apiKey;
		this.model = model;

		HttpClient http = HttpClient.newBuilder()
				.version(HttpClient.Version.HTTP_1_1) // HTTP/2 협상 문제를 피하려고 1.1로 고정
				.connectTimeout(Duration.ofSeconds(15))
				.build();
		JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
		factory.setReadTimeout(Duration.ofSeconds(30));
		this.rest = RestClient.builder()
				.baseUrl("https://generativelanguage.googleapis.com")
				.requestFactory(factory)
				.build();

		if (apiKey == null || apiKey.isBlank()) {
			log.warn("GEMINI_API_KEY가 비어 있어요. .env를 확인하세요 (분석 요청은 실패합니다)");
		} else {
			log.info("Gemini 모델: {}", model); // 키 값은 절대 로그에 남기지 않는다
		}
	}

	@Override
	public String generate(String prompt, byte[] image, String mimeType) {
		if (apiKey == null || apiKey.isBlank()) {
			throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI_NOT_CONFIGURED", "서버에 AI 키가 설정되지 않았어요");
		}

		List<Part> parts = new ArrayList<>();
		parts.add(new Part(prompt, null));
		if (image != null) {
			parts.add(new Part(null, new InlineData(mimeType, Base64.getEncoder().encodeToString(image))));
		}
		Request body = new Request(List.of(new Content("user", parts)), new GenerationConfig("application/json", 0.2));

		String json = JSON.writeValueAsString(body);
		// 503(혼잡)·500은 잠깐 뒤 다시 시도하면 되는 경우가 많아 최대 3번까지 보낸다
		final int maxAttempts = 3;
		for (int attempt = 1; ; attempt++) {
			try {
				return extractText(send(json));
			} catch (RestClientResponseException e) {
				int code = e.getStatusCode().value();
				// 503(혼잡)·500만 다시 시도한다. 키 문제(400·401·403)나 한도 초과(429)는 다시 보내도 같으므로 바로 알린다
				boolean retryable = code == 503 || code == 500;
				if (retryable && attempt < maxAttempts) {
					log.warn("Gemini HTTP {} → {}번째 다시 시도", code, attempt + 1);
					sleep(attempt * 1000L);
					continue;
				}
				log.warn("Gemini HTTP {} ({}번째 시도에서 중단)", code, attempt);
				throw new ApiException(HttpStatus.BAD_GATEWAY, "AI_BUSY", "AI 서버 오류 " + code + hint(code));
			}
		}
	}

	private String send(String json) {
		try {
			return rest.post()
					.uri("/v1beta/models/{model}:generateContent", model)
					.header("x-goog-api-key", apiKey)
					.contentType(MediaType.APPLICATION_JSON)
					.body(json)
					.retrieve()
					.body(String.class);
		} catch (RestClientResponseException e) {
			int code = e.getStatusCode().value();
			if (code == 503 || code == 500) {
				throw e; // 위의 반복문에서 다시 시도
			}
			log.warn("Gemini 오류 응답: HTTP {}", code);
			throw new ApiException(HttpStatus.BAD_GATEWAY, "AI_ERROR", "AI 서버 오류 " + code + hint(code));
		} catch (ResourceAccessException e) {
			// 시간 초과인지, 아예 연결을 못 한 건지 원인을 로그에 자세히 남긴다
			Throwable root = e;
			while (root.getCause() != null) {
				root = root.getCause();
			}
			boolean timeout = root instanceof java.net.http.HttpTimeoutException
					|| root instanceof java.net.SocketTimeoutException;
			log.warn("Gemini 연결 실패 ({}): {}", root.getClass().getSimpleName(), root.getMessage());
			if (timeout) {
				throw new ApiException(HttpStatus.GATEWAY_TIMEOUT, "AI_TIMEOUT", "AI 서버 응답이 늦어요. 다시 시도해 주세요");
			}
			throw new ApiException(HttpStatus.BAD_GATEWAY, "AI_UNREACHABLE", "AI 서버에 연결하지 못했어요");
		}
	}

	private static String hint(int code) {
		return switch (code) {
			case 404 -> " (모델 이름을 확인하세요)";
			case 400, 401, 403 -> " (API 키를 확인하세요)";
			case 429 -> " (사용량 한도를 넘었어요)";
			case 500, 503 -> " (AI 서버가 혼잡해요. 잠시 뒤 다시 시도하세요)";
			default -> "";
		};
	}

	private static void sleep(long ms) {
		try {
			Thread.sleep(ms);
		} catch (InterruptedException ie) {
			Thread.currentThread().interrupt();
		}
	}

	private String extractText(String raw) {
		if (raw == null) {
			throw new ApiException(HttpStatus.BAD_GATEWAY, "AI_EMPTY", "AI 응답이 비어 있어요");
		}
		try {
			Response res = JSON.readValue(raw, Response.class);
			if (res.candidates() != null) {
				for (Candidate c : res.candidates()) {
					if (c.content() == null || c.content().parts() == null) {
						continue;
					}
					for (Part p : c.content().parts()) {
						if (p.text() != null) {
							return p.text();
						}
					}
				}
			}
		} catch (JacksonException e) {
			throw new ApiException(HttpStatus.BAD_GATEWAY, "AI_BAD_RESPONSE", "AI 응답을 읽지 못했어요");
		}
		throw new ApiException(HttpStatus.BAD_GATEWAY, "AI_EMPTY", "AI 응답이 비어 있어요");
	}

	// ---- Gemini REST 요청/응답 모양 ----
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record Request(List<Content> contents, GenerationConfig generationConfig) {
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Content(String role, List<Part> parts) {
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Part(String text, InlineData inlineData) {
	}

	public record InlineData(String mimeType, String data) {
	}

	public record GenerationConfig(String responseMimeType, double temperature) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Response(List<Candidate> candidates) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Candidate(Content content) {
	}
}
