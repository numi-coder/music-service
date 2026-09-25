package kz.genvibe.media_management;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@SpringBootApplication
@EnableScheduling
public class Application {

	private static final String APP_TIME_ZONE = "Asia/Almaty";

	static void main(String[] args) {
		TimeZone.setDefault(TimeZone.getTimeZone(APP_TIME_ZONE));
		SpringApplication.run(Application.class, args);
	}

}
