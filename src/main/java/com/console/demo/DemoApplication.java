package com.console.demo;

import com.console.demo.commands.ai.ChatCommand;
import com.console.demo.commands.ai.ListModelsCommand;
import com.console.demo.commands.ai.StatusRunningCommand;
import com.console.demo.design.BannerDesign;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.PropertySource;
import org.springframework.shell.core.ShellRunner;
import org.springframework.shell.core.command.annotation.EnableCommand;

import java.time.LocalDate;

@EnableCommand({ListModelsCommand.class, StatusRunningCommand.class, ChatCommand.class})
@PropertySource("classpath:application.properties")
@ComponentScan("com.console.demo")
public class DemoApplication {

	public static void main(String[] args) throws Exception {
		ApplicationContext context = new AnnotationConfigApplicationContext(DemoApplication.class);
		if (args.length == 0) {   // hanya saat mode interaktif
			BannerDesign.print("9ROUTER CHAT v0.0.1 @PinkKey",
					"Router : " + context.getEnvironment().getProperty("spring.ai.openai.base-url"),
					"Date :"+ LocalDate.now(),
					"Ketik 'help' untuk daftar command");
		}

		ShellRunner runner = context.getBean(ShellRunner.class);
		runner.run(args);
	}

}
