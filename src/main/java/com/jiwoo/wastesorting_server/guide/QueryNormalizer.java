package com.jiwoo.wastesorting_server.guide;

import java.util.Locale;

/** "  아이스팩 " 과 "아이스팩", "PET 병" 과 "pet 병" 을 같은 검색어로 본다 */
public final class QueryNormalizer {

	private QueryNormalizer() {
	}

	public static String normalize(String raw) {
		if (raw == null) {
			return "";
		}
		return raw.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
	}
}
