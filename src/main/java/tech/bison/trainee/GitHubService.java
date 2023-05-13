package tech.bison.trainee;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Instant;
import java.util.Date;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

public class GitHubService implements GitService {
	private String token;

	public GitHubService(String token) {
		this.token = token;
	}

	@Override
	public Date findLastCommitDate(String repoUrl) throws IOException, RuntimeException {
		String apiUrl = repoUrl.replaceFirst("https://github.com/", "https://api.github.com/repos/");
		apiUrl = apiUrl.replaceFirst("\\.git$", "");
		apiUrl += "/commits";
		URL url = new URL(apiUrl);
		HttpURLConnection conn = (HttpURLConnection) url.openConnection();
		conn.setRequestMethod("GET");
		conn.setRequestProperty("Authorization", "token " + token);
		conn.connect();
		int responseCode = conn.getResponseCode();
		if (responseCode == 200) {
			Reader reader = new InputStreamReader(conn.getInputStream());
			JsonElement jsonElement = JsonParser.parseReader(reader);
			String dateString = jsonElement.getAsJsonArray().get(0).getAsJsonObject().get("commit").getAsJsonObject()
					.get("author").getAsJsonObject().get("date").getAsString();
			Instant instant = Instant.parse(dateString);
			return Date.from(instant);
		} else {
			throw new RuntimeException("HttpResponseCode: " + responseCode);
		}
	}
}
