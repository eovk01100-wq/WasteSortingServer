package com.jiwoo.wastesorting_server.analysis;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 분석 요청과 함께 오는 정보 (multipart의 meta 칸, JSON 글자)
 * 예: {"mode":"crop","detectedLabel":"pet","confidence":0.8}
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AnalysisMeta(String mode, String detectedLabel, Double confidence) {
}
