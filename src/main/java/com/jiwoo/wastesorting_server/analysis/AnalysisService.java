package com.jiwoo.wastesorting_server.analysis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.jiwoo.wastesorting_server.common.ApiException;
import com.jiwoo.wastesorting_server.gemini.DisposalResponse;
import com.jiwoo.wastesorting_server.gemini.GeminiClient;
import com.jiwoo.wastesorting_server.gemini.GeminiParser;
import com.jiwoo.wastesorting_server.gemini.GeminiPrompts;
import com.jiwoo.wastesorting_server.gemini.WasteClasses;

/** 사진 분석과 글자 검색. 사진은 저장하지 않고 응답 후 버린다 */
@Service
public class AnalysisService {

	private static final Logger log = LoggerFactory.getLogger(AnalysisService.class);

	private final GeminiClient gemini;

	public AnalysisService(GeminiClient gemini) {
		this.gemini = gemini;
	}

	public DisposalResponse analyzeImage(AnalysisMeta meta, byte[] image, String mimeType) {
		String mode = meta.mode() == null ? "" : meta.mode().trim().toLowerCase();
		String prompt;
		if ("crop".equals(mode)) {
			String label = WasteClasses.normalize(meta.detectedLabel());
			if (label == null) {
				throw new ApiException(HttpStatus.BAD_REQUEST, "BAD_LABEL", "detectedLabel이 올바르지 않아요");
			}
			double conf = meta.confidence() == null ? 0.0 : Math.max(0.0, Math.min(1.0, meta.confidence()));
			prompt = GeminiPrompts.crop(label, conf);
		} else if ("whole".equals(mode)) {
			prompt = GeminiPrompts.whole();
		} else {
			throw new ApiException(HttpStatus.BAD_REQUEST, "BAD_MODE", "mode는 crop 또는 whole이어야 해요");
		}

		long start = System.currentTimeMillis();
		DisposalResponse result = parse(gemini.generate(prompt, image, mimeType));
		log.info("사진 분석 mode={} label={} → {} ({}ms)", mode, meta.detectedLabel(), result.yoloClass(),
				System.currentTimeMillis() - start);
		return result;
	}

	public DisposalResponse search(String query) {
		String q = query == null ? "" : query.trim();
		if (q.isEmpty() || q.length() > 50) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "BAD_QUERY", "검색어는 1~50자로 입력해 주세요");
		}
		long start = System.currentTimeMillis();
		DisposalResponse result = parse(gemini.generate(GeminiPrompts.text(q), null, null));
		log.info("글자 검색 q={} ({}ms)", q, System.currentTimeMillis() - start);
		return result;
	}

	private DisposalResponse parse(String text) {
		try {
			return GeminiParser.parse(text);
		} catch (IllegalStateException e) {
			throw new ApiException(HttpStatus.BAD_GATEWAY, "AI_BAD_RESPONSE", e.getMessage());
		}
	}
}
