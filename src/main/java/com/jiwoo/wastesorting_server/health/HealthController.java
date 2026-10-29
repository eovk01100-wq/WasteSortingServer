package com.jiwoo.wastesorting_server.health;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 서버가 켜져 있는지 확인하는 가장 단순한 API.
 * GET /api/v1/health → {"status":"ok"}
 */
@RestController
@Tag(name = "상태", description = "서버가 켜져 있는지 확인")
@RequestMapping("/api/v1")
public class HealthController {

	@GetMapping("/health")
	public Map<String, String> health() {
		return Map.of("status", "ok");
	}
}
