package tech.bison.trainee;

import java.io.IOException;
import java.util.Date;

public interface GitService {
	Date findLastCommitDate(String repoUrl) throws IOException, RuntimeException;
}
