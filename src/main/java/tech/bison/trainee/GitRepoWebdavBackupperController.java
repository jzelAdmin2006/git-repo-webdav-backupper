package tech.bison.trainee;

import java.io.File;
import java.nio.file.Paths;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class GitRepoWebdavBackupperController {

	@PostMapping("/backup")
	public void backupRepo() {
		String repoUrl = System.getenv("REPO_URL");
		String localPath = System.getenv("LOCAL_PATH");
		String username = System.getenv("USERNAME");
		String password = System.getenv("PASSWORD");

		try {
			cloneRepository(repoUrl, localPath, username, password);
		} catch (GitAPIException e) {
			System.err.println("Error cloning repository: " + e.getMessage());
			e.printStackTrace();
		}
	}

	private static void cloneRepository(String repoUrl, String localPath, String username, String password)
			throws GitAPIException {
		String repoName = extractRepoName(repoUrl);
		File localDirectory = new File(Paths.get(localPath, repoName).toString());

		Git.cloneRepository().setURI(repoUrl).setDirectory(localDirectory)
				.setCredentialsProvider(new UsernamePasswordCredentialsProvider(username, password)).call();
	}

	private static String extractRepoName(String repoUrl) {
		String[] parts = repoUrl.split("/");
		String repoNameWithExtension = parts[parts.length - 1];
		return repoNameWithExtension.replace(".git", "");
	}
}
