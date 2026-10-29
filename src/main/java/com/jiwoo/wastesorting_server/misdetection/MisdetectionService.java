package com.jiwoo.wastesorting_server.misdetection;

import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.jiwoo.wastesorting_server.analysis.Analysis;
import com.jiwoo.wastesorting_server.analysis.AnalysisRepository;
import com.jiwoo.wastesorting_server.analysis.AnalysisSource;
import com.jiwoo.wastesorting_server.common.ApiException;

/**
 * 오탐 원본 업로드와 보관 기간 정리.
 * 받는 조건: 내 분석 기록이고, 박스 분석(CROP)이며, 서버가 오탐(misdetection=true)으로 판정한 건만. 한 건당 한 번.
 * 사진은 정해진 기간(기본 90일)이 지나면 매일 새벽에 지운다.
 */
@Service
public class MisdetectionService {

	private static final Logger log = LoggerFactory.getLogger(MisdetectionService.class);

	private final MisdetectionRepository misdetections;
	private final AnalysisRepository analyses;
	private final FrameStorage storage;
	private final int retentionDays;

	public MisdetectionService(MisdetectionRepository misdetections, AnalysisRepository analyses,
			FrameStorage storage, @Value("${storage.retention-days:90}") int retentionDays) {
		this.misdetections = misdetections;
		this.analyses = analyses;
		this.storage = storage;
		this.retentionDays = retentionDays;
	}

	public Long upload(Long userId, Long analysisId, byte[] image, String mimeType, double[] box) {
		Analysis a = analyses.findByIdAndUserId(analysisId, userId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "기록을 찾을 수 없어요"));
		if (a.getSource() != AnalysisSource.CROP || !a.isMisdetection()) {
			throw new ApiException(HttpStatus.CONFLICT, "NOT_MISDETECTION", "오탐으로 판정된 박스 분석만 올릴 수 있어요");
		}
		if (misdetections.existsByAnalysisId(analysisId)) {
			throw new ApiException(HttpStatus.CONFLICT, "ALREADY_UPLOADED", "이미 올린 사례예요");
		}
		validateBox(box);

		String file = storage.save(image, mimeType);
		try {
			Misdetection saved = misdetections.saveAndFlush(new Misdetection(analysisId, userId, a.getDetectedLabel(),
					a.getYoloClass(), file, box, LocalDateTime.now().plusDays(retentionDays)));
			log.info("오탐 업로드 #{} (분석 #{}, {} → {})", saved.getId(), analysisId, a.getDetectedLabel(),
					a.getYoloClass());
			return saved.getId();
		} catch (DataIntegrityViolationException e) {
			storage.delete(file); // 동시에 두 번 올라온 경우: 사진만 정리
			throw new ApiException(HttpStatus.CONFLICT, "ALREADY_UPLOADED", "이미 올린 사례예요");
		}
	}

	/** YOLO 형식 박스: 4개 값 모두 0~1, 너비·높이는 0보다 커야 함 */
	private static void validateBox(double[] b) {
		boolean ok = b != null && b.length == 4;
		if (ok) {
			for (double v : b) {
				ok &= !Double.isNaN(v) && v >= 0.0 && v <= 1.0;
			}
			ok &= b[2] > 0 && b[3] > 0;
		}
		if (!ok) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "BAD_BOX", "box는 [중심x, 중심y, 너비, 높이] (0~1) 네 개여야 해요");
		}
	}

	/** 매일 새벽 4시(서울): 보관 기간이 지난 사진과 기록 삭제 */
	@Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul")
	public void deleteExpired() {
		int total = 0;
		List<Misdetection> batch;
		do {
			batch = misdetections.findTop200ByDeleteAfterBefore(LocalDateTime.now());
			for (Misdetection m : batch) {
				storage.delete(m.getFrameFile());
				misdetections.delete(m);
			}
			total += batch.size();
		} while (batch.size() == 200);
		if (total > 0) {
			log.info("보관 기간이 지난 오탐 사례 {}건 삭제", total);
		}
	}
}
