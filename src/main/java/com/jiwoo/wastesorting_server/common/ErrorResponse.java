package com.jiwoo.wastesorting_server.common;

/** 모든 오류 응답의 모양: { "code": "...", "message": "..." } */
public record ErrorResponse(String code, String message) {
}
