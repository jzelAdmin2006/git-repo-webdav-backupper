package tech.bison.trainee;

import java.nio.charset.StandardCharsets;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.google.common.hash.Hashing;

@RestController
public class GitRepoWebdavBackupperController {
	private static final String TOKEN_HASH = System.getenv("TOKEN_HASH");

	@PostMapping("/backup")
	public ResponseEntity<String> backupRepo(@RequestHeader String token, @RequestBody String repoUrl) {
		if (authorizationIsOK(token)) {
			try {
				GitRepoWebdavBackupperService.getBackupRequests().add(repoUrl);
				return ResponseEntity.status(202).body("Backup creation queued");
			} catch (Exception e) {
				System.err.println("Error creating queue");
				e.printStackTrace();
				return ResponseEntity.status(500).body("Error creating queue:\n" + e.getStackTrace());
			}
		} else {
			return ResponseEntity.status(401).body("Unauthorized");
		}
	}

	private boolean authorizationIsOK(String token) {
		return token == null ? false
				: TOKEN_HASH.equals(Hashing.sha256().hashString(token, StandardCharsets.UTF_8).toString());
	}
}
