package tech.bison.trainee;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.github.sardine.DavResource;
import com.github.sardine.Sardine;
import com.github.sardine.SardineFactory;

import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.model.enums.CompressionLevel;

@Service
public class GitRepoWebdavBackupperService {
	private static final Path LOCAL_PATH = Path.of(System.getenv("LOCAL_PATH"));
	private static final String GIT_USERNAME = System.getenv("USERNAME");
	private static final String GIT_PASSWORD = System.getenv("PASSWORD");
	private static final String WEBDAV_URL = System.getenv("WEBDAV_URL");
	private static final String WEBDAV_USERNAME = System.getenv("WEBDAV_USERNAME");
	private static final String WEBDAV_PASSWORD = System.getenv("WEBDAV_PASSWORD");
	private static final GitService GITHUB = new GitHubService(GIT_PASSWORD);
	private static final Queue<String> backupRequests = new LinkedList<>();
	private static final Date NEVER = null;
	private final Lock lock = new ReentrantLock();

	@Scheduled(fixedRate = 5000)
	public void checkBackupCreationRequest() {
		if (backupRequests.size() >= 1 && lock.tryLock()) {
			createBackup();
		}
	}

	/**
	 * Creates a backup of the repo according to the next entry in the queue if
	 * there can't be found an existing backup that is for sure up-to-date
	 */
	@Async
	public void createBackup() {
		String repoUrl = backupRequests.poll();
		try {
			if (backupIsUpToDate(repoUrl)) {
				System.out.println(String.format("Backup is already up-to-date for \"%s\"", repoUrl));
			} else {
				proceedBackup(repoUrl);
				System.out.println(String.format("Backup created for \"%s\"", repoUrl));
			}
		} catch (GitAPIException | IOException | ParseException e) {
			System.out.println(String.format("Backup creation for \"%s\" failed", repoUrl));
			e.printStackTrace();
		} finally {
			lock.unlock();
		}
	}

	private boolean backupIsUpToDate(String repoUrl) throws IOException, ParseException {
		Date lastBackupDate = findLastBackupDate(repoUrl);
		if (repoUrl.startsWith("https://github.com/"))
			return lastBackupDate != NEVER && lastBackupDate.after(GITHUB.findLastCommitDate(repoUrl));
		else
			return false; // TODO: Implement with other Git service APIs
	}

	private Date findLastBackupDate(String repoUrl) throws IOException, ParseException {
		Sardine sardine = SardineFactory.begin(WEBDAV_USERNAME, WEBDAV_PASSWORD);
		List<DavResource> resources = sardine.list(WEBDAV_URL);
		Date lastBackupped = NEVER;
		for (DavResource res : resources) {
			String fileName = res.getName();
			String repoName = extractRepoName(repoUrl);
			String pattern = String.format("^%s\\d{14}\\.zip", repoName);
			if (res.getName().matches(pattern)) {
				SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMddHHmmss");
				Date backupDate = dateFormat.parse(fileName.substring(fileName.length() - 18, fileName.length() - 4));
				if (lastBackupped == NEVER || backupDate.after(lastBackupped))
					lastBackupped = backupDate;
			}
		}
		return lastBackupped;
	}

	public static Queue<String> getBackupRequests() {
		return backupRequests;
	}

	private void proceedBackup(String repoUrl) throws GitAPIException, IOException {
		Path repoDirectory = cloneRepository(repoUrl);
		archiveRepository(repoDirectory);
		uploadToWebDav();
		FileUtils.cleanDirectory(LOCAL_PATH.toFile());
	}

	private void archiveRepository(Path repoDirectory) throws IOException {
		Path destinationPath = repoDirectory.getParent().resolve(repoDirectory.getFileName().toString()
				+ new SimpleDateFormat("yyyyMMddHHmmss").format(new Date()) + ".zip");
		ZipFile zipFile = new ZipFile(destinationPath.toFile());
		ZipParameters zipParams = new ZipParameters();
		zipParams.setCompressionLevel(CompressionLevel.NO_COMPRESSION);
		zipFile.createSplitZipFileFromFolder(repoDirectory.toFile(), zipParams, true, 10000000);
	}

	private Path cloneRepository(String repoUrl) throws GitAPIException {
		String repoName = extractRepoName(repoUrl);
		File localDirectory = GitRepoWebdavBackupperService.LOCAL_PATH.resolve(repoName).toFile();
		try (Git git = Git.cloneRepository().setURI(repoUrl).setDirectory(localDirectory)
				.setCredentialsProvider(new UsernamePasswordCredentialsProvider(GIT_USERNAME, GIT_PASSWORD))
				.setNoCheckout(true).call()) {
			return localDirectory.toPath();
		}
	}

	private static void uploadToWebDav() throws IOException {
		Sardine sardine = SardineFactory.begin(WEBDAV_USERNAME, WEBDAV_PASSWORD);

		try (DirectoryStream<Path> stream = Files.newDirectoryStream(LOCAL_PATH)) {
			for (Path filePath : stream) {
				if (Files.isRegularFile(filePath)) {
					String remotePath = WEBDAV_URL + "/" + filePath.getFileName().toString();
					sardine.put(remotePath, Files.readAllBytes(filePath));
				}
			}
		}
	}

	private static String extractRepoName(String repoUrl) {
		String[] parts = repoUrl.split("/");
		String repoNameWithExtension = parts[parts.length - 1];
		return repoNameWithExtension.replace(".git", "");
	}
}
