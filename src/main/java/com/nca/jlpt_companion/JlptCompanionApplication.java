package com.nca.jlpt_companion;

import com.nca.jlpt_companion.common.config.AppJwtProperties;
import com.nca.jlpt_companion.common.config.AppSyncProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({AppJwtProperties.class, AppSyncProperties.class})
public class JlptCompanionApplication {

	public static void main(String[] args) {
		SpringApplication.run(JlptCompanionApplication.class, args);
	}

}
