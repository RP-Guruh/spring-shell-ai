package com.console.demo;

import com.console.demo.commands.ai.ChatCommand;
import com.console.demo.commands.ai.ListModelsCommand;
import com.console.demo.commands.ai.StatusRunningCommand;
import com.console.demo.commands.ai.SettingCommand;
import com.console.demo.design.BannerDesign;
import org.jline.reader.LineReader;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.shell.core.ShellRunner;
import org.springframework.shell.core.command.annotation.EnableCommand;

import java.time.LocalDate;

@SpringBootApplication
@EnableCommand({ListModelsCommand.class, StatusRunningCommand.class, ChatCommand.class, SettingCommand.class})
@EnableAsync
public class DemoApplication {

	public static void main(String[] args) throws Exception {
		SpringApplication app = new SpringApplication(DemoApplication.class);
		app.setWebApplicationType(WebApplicationType.NONE);
		app.setBannerMode(Banner.Mode.OFF);

		ConfigurableApplicationContext context = app.run(args);

		if (args.length == 0) {
			LineReader lineReader = context.getBean(LineReader.class);
			String baseUrl = context.getEnvironment()
					.getProperty("spring.ai.openai.base-url", "-");

			BannerDesign.print(lineReader.getTerminal(),
					"9ROUTER CHAT v0.0.1 @PinkKey",
					BannerDesign.row("Router", baseUrl),
					BannerDesign.row("Date", LocalDate.now().toString()),
					"Ketik 'help' untuk daftar command");
		}

		ShellRunner runner = context.getBean(ShellRunner.class);
		runner.run(args);
	}
}