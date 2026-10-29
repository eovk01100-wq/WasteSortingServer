package com.jiwoo.wastesorting_server.misdetection;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.imageio.ImageIO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jiwoo.wastesorting_server.analysis.PageResponse;
import com.jiwoo.wastesorting_server.common.ApiException;
import com.jiwoo.wastesorting_server.gemini.WasteClasses;
import com.jiwoo.wastesorting_server.misdetection.AdminDtos.Decision;
import com.jiwoo.wastesorting_server.misdetection.AdminDtos.MisdetectionItem;

/**
 * 관리자 검수: 오탐 목록 보기, 박스를 그린 사진 보기, 정답 확정·제외, YOLO 학습 형식 zip 내보내기.
 * zip 구성: images/{id}.jpg, labels/{id}.txt ("클래스번호 cx cy w h", other는 빈 파일 = 배경 사진), data.yaml
 */
@Service
public class AdminMisdetectionService {

	private static final Logger log = LoggerFactory.getLogger(AdminMisdetectionService.class);

	private final MisdetectionRepository misdetections;
	private final FrameStorage storage;

	public AdminMisdetectionService(MisdetectionRepository misdetections, FrameStorage storage) {
		this.misdetections = misdetections;
		this.storage = storage;
	}

	@Transactional(readOnly = true)
	public PageResponse<MisdetectionItem> list(MisdetectionStatus status, int page, int size) {
		int s = Math.max(1, Math.min(100, size));
		return PageResponse.of(misdetections.findByStatusOrderByIdDesc(status, PageRequest.of(Math.max(0, page), s))
				.map(MisdetectionItem::of));
	}

	@Transactional
	public MisdetectionItem review(Long id, Decision decision, String rawLabel, Long adminId) {
		Misdetection m = find(id);
		if (decision == Decision.REJECT) {
			m.reject(adminId);
		} else {
			String label = rawLabel == null ? null : rawLabel.trim().toLowerCase(Locale.ROOT);
			if (label == null || !WasteClasses.isKnown(label)) {
				throw new ApiException(HttpStatus.BAD_REQUEST, "BAD_LABEL",
						"label은 " + WasteClasses.NAMES + " 또는 other 중 하나여야 해요");
			}
			m.confirm(label, adminId);
		}
		log.info("검수 #{} → {} {}", id, m.getStatus(), m.getConfirmedLabel() == null ? "" : m.getConfirmedLabel());
		return MisdetectionItem.of(m);
	}

	/** 원본 사진에 박스를 빨간 테두리로 그려서 돌려준다 (그리기 실패 시 원본 그대로) */
	@Transactional(readOnly = true)
	public byte[] image(Long id, boolean drawBox) {
		Misdetection m = find(id);
		byte[] raw = readOr404(m);
		if (!drawBox) {
			return raw;
		}
		try {
			BufferedImage src = ImageIO.read(new ByteArrayInputStream(raw));
			if (src == null) {
				return raw;
			}
			BufferedImage img = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
			Graphics2D g = img.createGraphics();
			g.drawImage(src, 0, 0, null);
			int w = img.getWidth();
			int h = img.getHeight();
			int bw = (int) Math.round(m.getBoxW() * w);
			int bh = (int) Math.round(m.getBoxH() * h);
			int x = (int) Math.round(m.getBoxCx() * w - bw / 2.0);
			int y = (int) Math.round(m.getBoxCy() * h - bh / 2.0);
			g.setColor(Color.RED);
			g.setStroke(new BasicStroke(Math.max(3f, w / 200f)));
			g.drawRect(x, y, bw, bh);
			g.dispose();
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			ImageIO.write(img, "jpg", out);
			return out.toByteArray();
		} catch (IOException e) {
			return raw;
		}
	}

	/** 정답이 확정된 사례만 YOLO 학습 형식 zip으로 */
	@Transactional(readOnly = true)
	public byte[] exportZip() {
		List<Misdetection> confirmed = misdetections.findByStatusOrderByIdAsc(MisdetectionStatus.CONFIRMED);
		if (confirmed.isEmpty()) {
			throw new ApiException(HttpStatus.NOT_FOUND, "NOTHING_TO_EXPORT", "정답이 확정된 사례가 없어요. 먼저 검수해 주세요");
		}
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		int count = 0;
		try (ZipOutputStream zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
			for (Misdetection m : confirmed) {
				if (!storage.exists(m.getFrameFile())) {
					continue; // 보관 기간이 지나 지워진 경우
				}
				String ext = m.getFrameFile().endsWith(".png") ? ".png" : ".jpg";
				put(zip, "images/" + m.getId() + ext, storage.read(m.getFrameFile()));
				put(zip, "labels/" + m.getId() + ".txt", labelLine(m).getBytes(StandardCharsets.UTF_8));
				count++;
			}
			put(zip, "data.yaml", dataYaml().getBytes(StandardCharsets.UTF_8));
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		log.info("YOLO zip 내보내기: {}건", count);
		return bytes.toByteArray();
	}

	public static String exportFileName() {
		return "wastesorting-yolo-" + LocalDate.now() + ".zip";
	}

	/** "클래스번호 cx cy w h" (other면 빈 줄 = 배경 사진) */
	static String labelLine(Misdetection m) {
		int classId = WasteClasses.NAMES.indexOf(m.getConfirmedLabel());
		if (classId < 0) {
			return "";
		}
		return String.format(Locale.ROOT, "%d %.6f %.6f %.6f %.6f%n", classId, m.getBoxCx(), m.getBoxCy(), m.getBoxW(),
				m.getBoxH());
	}

	private static String dataYaml() {
		StringBuilder sb = new StringBuilder("# 버림가이드 오탐 검수 데이터 (기존 데이터셋과 합쳐서 재학습)\n");
		sb.append("train: images\nval: images\nnc: ").append(WasteClasses.NAMES.size()).append("\nnames:\n");
		for (int i = 0; i < WasteClasses.NAMES.size(); i++) {
			sb.append("  ").append(i).append(": ").append(WasteClasses.NAMES.get(i)).append('\n');
		}
		return sb.toString();
	}

	private static void put(ZipOutputStream zip, String name, byte[] data) throws IOException {
		zip.putNextEntry(new ZipEntry(name));
		zip.write(data);
		zip.closeEntry();
	}

	private Misdetection find(Long id) {
		return misdetections.findById(id)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "오탐 사례를 찾을 수 없어요"));
	}

	private byte[] readOr404(Misdetection m) {
		if (!storage.exists(m.getFrameFile())) {
			throw new ApiException(HttpStatus.NOT_FOUND, "IMAGE_GONE", "사진이 보관 기간이 지나 삭제됐어요");
		}
		return storage.read(m.getFrameFile());
	}
}
