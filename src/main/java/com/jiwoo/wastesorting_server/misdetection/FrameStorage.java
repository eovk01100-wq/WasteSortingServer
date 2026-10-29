package com.jiwoo.wastesorting_server.misdetection;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 오탐 원본 사진을 서버 디스크(Docker 볼륨)에 저장한다.
 * 파일 이름은 UUID로 바꿔서 사용자가 보낸 이름을 쓰지 않는다 (경로 조작 방지).
 * 나중에 S3로 바꾸려면 이 클래스만 바꾸면 된다.
 */
@Component
public class FrameStorage {

	private static final Logger log = LoggerFactory.getLogger(FrameStorage.class);

	private final Path dir;

	public FrameStorage(@Value("${storage.upload-dir:uploads}") String uploadDir) {
		this.dir = Path.of(uploadDir, "frames").toAbsolutePath().normalize();
		try {
			Files.createDirectories(dir);
		} catch (IOException e) {
			throw new UncheckedIOException("업로드 폴더를 만들 수 없어요: " + dir, e);
		}
		log.info("오탐 사진 저장 위치: {}", dir);
	}

	/** 저장하고 파일 이름(UUID.jpg 또는 .png)을 돌려준다 */
	public String save(byte[] bytes, String mimeType) {
		String name = UUID.randomUUID() + ("image/png".equals(mimeType) ? ".png" : ".jpg");
		try {
			Files.write(dir.resolve(name), bytes);
		} catch (IOException e) {
			throw new UncheckedIOException("사진을 저장하지 못했어요", e);
		}
		return name;
	}

	public Path resolve(String fileName) {
		Path p = dir.resolve(fileName).normalize();
		if (!p.startsWith(dir)) {
			throw new IllegalArgumentException("잘못된 파일 이름");
		}
		return p;
	}

	public byte[] read(String fileName) {
		try {
			return Files.readAllBytes(resolve(fileName));
		} catch (IOException e) {
			throw new UncheckedIOException("사진을 읽지 못했어요: " + fileName, e);
		}
	}

	public boolean exists(String fileName) {
		return Files.exists(resolve(fileName));
	}

	public void delete(String fileName) {
		try {
			Files.deleteIfExists(resolve(fileName));
		} catch (IOException e) {
			log.warn("사진 삭제 실패: {}", fileName);
		}
	}
}
