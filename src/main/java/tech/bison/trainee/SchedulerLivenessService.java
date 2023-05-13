package tech.bison.trainee;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class SchedulerLivenessService {
	private static final String PROBE_ENDPOINT = System.getenv("PROBE_ENDPOINT");

	@Scheduled(fixedRate = 300000)
	public void assureBackupperIsLive() {
		try {
			URL url = new URL(PROBE_ENDPOINT);
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();

			conn.setRequestMethod("GET");

			int responseCode = conn.getResponseCode();
			System.out.println("Scheduler liveness response Code: " + responseCode);

			try (BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
				String inputLine;
				StringBuilder response = new StringBuilder();

				while ((inputLine = in.readLine()) != null) {
					response.append(inputLine);
				}
				System.out.println("Scheduler liveness response: " + response.toString());
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
