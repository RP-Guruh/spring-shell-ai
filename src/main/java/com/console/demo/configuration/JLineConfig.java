package com.console.demo.configuration;

import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

@Configuration
public class JLineConfig {

    @Bean(destroyMethod = "close")
    Terminal terminal() throws IOException {
        return TerminalBuilder.builder()
                .system(true)
                .build();
    }

    @Bean
    LineReader lineReader(Terminal terminal) {
        return LineReaderBuilder.builder()
                .terminal(terminal)
                .build();
    }
}