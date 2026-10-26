package com.jiwoo.wastesorting_server.ratelimit;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * "정해진 시간 창 안에 N번까지" 세는 간단한 제한기 (서버 메모리에 저장).
 * 서버가 1대라서 메모리로 충분하다. 서버를 재시작하면 숫자가 0으로 돌아간다.
 */
public class FixedWindowLimiter {

	private final int limit;
	private final long windowMillis;
	private final Map<String, Window> windows = new ConcurrentHashMap<>();

	public FixedWindowLimiter(int limit, long windowMillis) {
		this.limit = limit;
		this.windowMillis = windowMillis;
	}

	/** 허용되면 0, 막히면 다시 시도할 수 있을 때까지 남은 초 */
	public long tryAcquire(String key, long now) {
		if (windows.size() > 10_000) {
			windows.values().removeIf(w -> now - w.start >= windowMillis);
		}
		Window w = windows.compute(key, (k, old) -> (old == null || now - old.start >= windowMillis)
				? new Window(now) : old);
		synchronized (w) {
			if (w.count < limit) {
				w.count++;
				return 0;
			}
			return Math.max(1, (w.start + windowMillis - now + 999) / 1000);
		}
	}

	private static final class Window {
		final long start;
		int count;

		Window(long start) {
			this.start = start;
		}
	}
}
