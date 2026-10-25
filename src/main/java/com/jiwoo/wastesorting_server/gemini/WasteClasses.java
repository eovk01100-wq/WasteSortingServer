package com.jiwoo.wastesorting_server.gemini;

import java.util.List;
import java.util.Locale;

/** 분류 목록 (앱의 WasteClasses.kt, 모델 labels.txt 순서와 같아야 함) */
public final class WasteClasses {

	public static final List<String> NAMES = List.of("can", "glass", "pack", "paper", "pet", "plastic", "vinyl");
	public static final String OTHER = "other";

	private WasteClasses() {
	}

	public static boolean isKnown(String name) {
		return NAMES.contains(name) || OTHER.equals(name);
	}

	/** Gemini가 준 분류를 허용된 값(7개 + other)으로 정리. 그 외 값은 null */
	public static String normalize(String raw) {
		if (raw == null) {
			return null;
		}
		String v = raw.trim().toLowerCase(Locale.ROOT);
		return isKnown(v) ? v : null;
	}
}
