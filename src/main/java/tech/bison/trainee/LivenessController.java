package tech.bison.trainee;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LivenessController {
	@GetMapping("/probe")
	public String checkLiveness() {
		return "Backupper is live!";
	}
}
