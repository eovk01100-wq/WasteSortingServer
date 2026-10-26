package com.jiwoo.wastesorting_server.ratelimit;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.jiwoo.wastesorting_server.auth.TokenAuthFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Gemini 비용이 갑자기 늘지 않게 막는 사용량 제한. 숫자는 .env에서 바꾼다.
 * - AI 요청(/analyses, /search): 토큰당 1분 N회, IP당 1분 N회, 서버 전체 하루 N회
 * - 토큰 발급(/auth/device): IP당 1시간 N회 (토큰을 마구 찍어 내는 것 방지)
 * 토큰 검사(TokenAuthFilter) 다음에 실행된다.
 */
@Component
@Order(2)
public class RateLimitFilter extends OncePerRequestFilter {

	private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);
	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

	private final FixedWindowLimiter perToken;
	private final FixedWindowLimiter perIp;
	private final FixedWindowLimiter deviceIssue;
	private final int dailyCap;

	private volatile LocalDate day = LocalDate.now(SEOUL);
	private final AtomicInteger dailyCount = new AtomicInteger();

	public RateLimitFilter(
			@Value("${rate-limit.per-token-per-minute:20}") int perTokenPerMinute,
			@Value("${rate-limit.per-ip-per-minute:60}") int perIpPerMinute,
			@Value("${rate-limit.device-issue-per-ip-per-hour:10}") int deviceIssuePerHour,
			@Value("${rate-limit.daily-ai-cap:500}") int dailyCap) {
		this.perToken = new FixedWindowLimiter(perTokenPerMinute, 60_000);
		this.perIp = new FixedWindowLimiter(perIpPerMinute, 60_000);
		this.deviceIssue = new FixedWindowLimiter(deviceIssuePerHour, 3_600_000);
		this.dailyCap = dailyCap;
		log.info("사용량 제한: 토큰당 {}/분, IP당 {}/분, 토큰 발급 IP당 {}/시간, 하루 AI {}회",
				perTokenPerMinute, perIpPerMinute, deviceIssuePerHour, dailyCap);
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		String path = request.getRequestURI();
		return !(isAiRequest(path) || path.equals("/api/v1/auth/device"));
	}

	private static boolean isAiRequest(String path) {
		return path.startsWith("/api/v1/analyses") || path.startsWith("/api/v1/search");
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		long now = System.currentTimeMillis();
		String ip = request.getRemoteAddr();
		String path = request.getRequestURI();

		if (path.equals("/api/v1/auth/device")) {
			long wait = deviceIssue.tryAcquire(ip, now);
			if (wait > 0) {
				reject(response, wait, "RATE_LIMITED", "토큰 발급 요청이 너무 많아요. 잠시 뒤 다시 시도하세요");
				return;
			}
			chain.doFilter(request, response);
			return;
		}

		Object userId = request.getAttribute(TokenAuthFilter.USER_ID);
		long wait = Math.max(perIp.tryAcquire(ip, now),
				userId == null ? 0 : perToken.tryAcquire("u" + userId, now));
		if (wait > 0) {
			reject(response, wait, "RATE_LIMITED", "요청이 너무 많아요. " + wait + "초 뒤 다시 시도하세요");
			return;
		}
		if (!takeDaily()) {
			log.warn("하루 AI 호출 상한({}회)에 도달했어요", dailyCap);
			reject(response, secondsUntilMidnight(), "DAILY_LIMIT", "오늘 사용량이 모두 찼어요. 내일 다시 이용해 주세요");
			return;
		}
		chain.doFilter(request, response);
	}

	/** 서울 기준 날짜가 바뀌면 0으로 되돌리고, 상한 안이면 1 늘린다 */
	private synchronized boolean takeDaily() {
		LocalDate today = LocalDate.now(SEOUL);
		if (!today.equals(day)) {
			day = today;
			dailyCount.set(0);
		}
		if (dailyCount.get() >= dailyCap) {
			return false;
		}
		dailyCount.incrementAndGet();
		return true;
	}

	private static long secondsUntilMidnight() {
		var now = java.time.ZonedDateTime.now(SEOUL);
		return java.time.Duration.between(now, now.toLocalDate().plusDays(1).atStartOfDay(SEOUL)).getSeconds();
	}

	private static void reject(HttpServletResponse response, long retryAfterSeconds, String code, String message)
			throws IOException {
		response.setStatus(429);
		response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		response.getWriter().write("{\"code\":\"" + code + "\",\"message\":\"" + message + "\"}");
	}
}
