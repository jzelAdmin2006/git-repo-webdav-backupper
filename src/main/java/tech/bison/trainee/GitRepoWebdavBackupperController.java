package tech.bison.trainee;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.github.sardine.Sardine;
import com.github.sardine.SardineFactory;
import com.google.common.hash.Hashing;

import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.model.enums.CompressionLevel;

@RestController
public class GitRepoWebdavBackupperController {

	@PostMapping("/backup")
	public ResponseEntity<String> backupRepo(@RequestHeader String token, @RequestBody String repoUrl) {
		String tokenHash = System.getenv("TOKEN_HASH");
		if (authorizationIsOK(token, tokenHash)) {
			String localPath = System.getenv("LOCAL_PATH");
			String username = System.getenv("USERNAME");
			String password = System.getenv("PASSWORD");
			String webdavUrl = System.getenv("WEBDAV_URL");
			String webdavUsername = System.getenv("WEBDAV_USERNAME");
			String webdavPassword = System.getenv("WEBDAV_PASSWORD");

			return proceedBackup(repoUrl, Path.of(localPath), username, password, webdavUrl, webdavUsername,
					webdavPassword);
		} else {
			return ResponseEntity.status(401).body("Unauthorized");
		}
	}

	private boolean authorizationIsOK(String token, String tokenHash) {
		return token == null ? false
				: tokenHash.equals(Hashing.sha256().hashString(token, StandardCharsets.UTF_8).toString());
	}

	private ResponseEntity<String> proceedBackup(String repoUrl, Path localPath, String username, String password,
			String webdavUrl, String webdavUsername, String webdavPassword) {
		try {
			Path repoDirectory = cloneRepository(repoUrl, localPath, username, password);
			archiveRepository(repoDirectory);
			uploadToWebDav(localPath, webdavUrl, webdavUsername, webdavPassword);
			FileUtils.cleanDirectory(localPath.toFile());
			return ResponseEntity.status(200).body("Backup created");
		} catch (GitAPIException | IOException e) {
			System.err.println("Error cloning repository and uploading to WebDAV: " + e.getMessage());
			e.printStackTrace();
			return ResponseEntity.status(500).body("Backup failed:\n" + e.getStackTrace());
		}
	}

	private void archiveRepository(Path repoDirectory) throws IOException {
		Path destinationPath = repoDirectory.getParent().resolve(repoDirectory.getFileName().toString()
				+ new SimpleDateFormat("yyyyMMddHHmmss").format(new Date()) + ".zip");
		ZipFile zipFile = new ZipFile(destinationPath.toFile());
		ZipParameters zipParams = new ZipParameters();
		zipParams.setCompressionLevel(CompressionLevel.NO_COMPRESSION);
		zipFile.createSplitZipFileFromFolder(repoDirectory.toFile(), zipParams, true, 10000000);
	}

	private Path cloneRepository(String repoUrl, Path localPath, String username, String password)
			throws GitAPIException {
		String repoName = extractRepoName(repoUrl);
		File localDirectory = localPath.resolve(repoName).toFile();
		Git.cloneRepository().setURI(repoUrl).setDirectory(localDirectory)
				.setCredentialsProvider(new UsernamePasswordCredentialsProvider(username, password)).setNoCheckout(true)
				.call().close();
		return localDirectory.toPath();
	}

	private static void uploadToWebDav(Path sourcePath, String webdavUrl, String username, String password)
			throws IOException {
		Sardine sardine = SardineFactory.begin(username, password);

		DirectoryStream<Path> stream = Files.newDirectoryStream(sourcePath);
		for (Path filePath : stream) {
			if (Files.isRegularFile(filePath)) {
				String remotePath = webdavUrl + "/" + filePath.getFileName().toString();
				sardine.put(remotePath, Files.readAllBytes(filePath));
			}
		}
		stream.close();
		// ToDo: Always finally close resources
	}

	private static String extractRepoName(String repoUrl) {
		String[] parts = repoUrl.split("/");
		String repoNameWithExtension = parts[parts.length - 1];
		return repoNameWithExtension.replace(".git", "");
	}
}
