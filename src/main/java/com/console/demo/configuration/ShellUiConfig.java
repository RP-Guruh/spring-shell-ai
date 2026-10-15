package com.console.demo.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.shell.jline.tui.style.TemplateExecutor;
import org.springframework.shell.jline.tui.style.Theme;
import org.springframework.shell.jline.tui.style.ThemeRegistry;
import org.springframework.shell.jline.tui.style.ThemeResolver;
import org.springframework.shell.jline.tui.style.ThemeSettings;

@Configuration
public class ShellUiConfig {

    @Bean
    ThemeRegistry themeRegistry() {
        ThemeRegistry registry = new ThemeRegistry();
        registry.register(Theme.of("default", ThemeSettings.defaults()));
        return registry;
    }

    @Bean
    ThemeResolver shellThemeResolver(ThemeRegistry themeRegistry) {
        return new ThemeResolver(themeRegistry, "default");
    }

    @Bean
    TemplateExecutor templateExecutor(ThemeResolver themeResolver) {
        return new TemplateExecutor(themeResolver);
    }
}