package com.console.demo;

import com.console.demo.commands.ai.ChatCommand;
import com.console.demo.commands.ai.ListModelsCommand;
import com.console.demo.commands.ai.StatusRunningCommand;
import com.console.demo.design.BannerDesign;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.shell.core.ShellRunner;
import org.springframework.shell.core.command.annotation.EnableCommand;

import java.time.LocalDate;

@SpringBootApplication
@EnableCommand({ListModelsCommand.class, StatusRunningCommand.class, ChatCommand.class})
public class DemoApplication {

	public static void main(String[] args) throws Exception {
		SpringApplication app = new SpringApplication(DemoApplication.class);
		app.setWebApplicationType(WebApplicationType.NONE);
		app.setBannerMode(Banner.Mode.OFF);

		ConfigurableApplicationContext context = app.run(args);

		if (args.length == 0) {
			BannerDesign.print("9ROUTER CHAT v0.0.1 @PinkKey",
					"Router : " + context.getEnvironment().getProperty("spring.ai.openai.base-url"),
					"Date :" + LocalDate.now(),
					"Ketik 'help' untuk daftar command");
		}

		ShellRunner runner = context.getBean(ShellRunner.class);
		runner.run(args);
	}
}
