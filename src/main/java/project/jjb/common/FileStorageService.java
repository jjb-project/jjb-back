package project.jjb.common;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * 업로드 이미지를 로컬 디렉터리에 저장하고 정적 URL(/uploads/...)을 반환한다.
 */
@Service
public class FileStorageService {

	private final Path uploadDir;

	public FileStorageService(@Value("${jjb.upload-dir:uploads}") String uploadDir) {
		this.uploadDir = Paths.get(uploadDir).toAbsolutePath().normalize();
		try {
			Files.createDirectories(this.uploadDir);
		}
		catch (IOException e) {
			throw new IllegalStateException("업로드 디렉터리를 생성할 수 없습니다.", e);
		}
	}

	public Path directory() {
		return uploadDir;
	}

	/**
	 * 이미지 파일을 저장하고 접근 가능한 URL을 반환한다. 비어 있으면 null.
	 */
	public String store(MultipartFile file) {
		if (file == null || file.isEmpty()) {
			return null;
		}
		String contentType = file.getContentType();
		if (contentType == null || !contentType.startsWith("image/")) {
			throw ApiException.badRequest("INVALID_IMAGE", "이미지 파일만 업로드할 수 있습니다.");
		}
		String ext = extension(file.getOriginalFilename());
		String filename = UUID.randomUUID() + ext;
		try {
			Files.copy(file.getInputStream(), uploadDir.resolve(filename));
		}
		catch (IOException e) {
			throw ApiException.badRequest("UPLOAD_FAILED", "이미지 업로드에 실패했습니다.");
		}
		return "/uploads/" + filename;
	}

	private String extension(String original) {
		if (original == null) {
			return "";
		}
		int dot = original.lastIndexOf('.');
		if (dot < 0 || dot == original.length() - 1) {
			return "";
		}
		String ext = original.substring(dot).toLowerCase();
		return ext.matches("\\.(jpg|jpeg|png|gif|webp)") ? ext : "";
	}
}
