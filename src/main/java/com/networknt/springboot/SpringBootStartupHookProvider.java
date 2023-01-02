package com.networknt.springboot;

import com.networknt.config.Config;
import com.networknt.handler.Handler;
import com.networknt.handler.config.HandlerConfig;
import com.networknt.server.StartupHookProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationContextFactory;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.boot.web.servlet.context.AnnotationConfigServletWebServerApplicationContext;
import org.springframework.boot.web.servlet.support.ServletContextApplicationContextInitializer;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.web.context.ConfigurableWebEnvironment;
import org.springframework.web.context.WebApplicationContext;

import javax.servlet.ServletContext;
import java.util.ArrayList;
import java.util.List;

public class SpringBootStartupHookProvider implements StartupHookProvider {

    private static final Logger log = LoggerFactory.getLogger(SpringBootStartupHookProvider.class);

    static Class<?> springBootConfigClass;
    static String[] args;

    static ConfigurableApplicationContext currentSpringContext;

    public static final ConfigurableApplicationContext getCurrentSpringContext() {
        return currentSpringContext;
    }

    @Override
    public void onStartup() {
        if(springBootConfigClass == null) {
            throw new IllegalStateException("Spring Boot config class must be specified.");
        }
        log.info("Initializing Spring Boot Application Context ...");
        UndertowServletDeploymentFactory factory = new UndertowServletDeploymentFactory();
        factory.init(servletContext -> {
            SpringApplication app = new SpringApplication(springBootConfigClass);
            app.setBannerMode(Banner.Mode.OFF);
            app.addInitializers(new ServletContextApplicationContextInitializer(servletContext));
//            app.setApplicationContextFactory((webApplicationType) -> {
//                var ctx = ApplicationContextFactory.DEFAULT.create(webApplicationType);
//                log.info("Created Spring Boot Application Context: {}", ctx.getClass().getName());
//                return ctx;
//            });
            app.addListeners(new WebEnvironmentPropertySourceInitializer(servletContext));
            currentSpringContext = app.run(args);
            log.info("Created Spring Boot Application Context: {}", currentSpringContext.getClass().getName());
        });
        setupSpringBootWebHandler();
        log.info("Spring Boot Application Context Initialized.");
    }

    void setupSpringBootWebHandler() {
        HandlerConfig config = Handler.config;
        if(config == null) {
            config = new HandlerConfig();
            Handler.config = config;
            Config.getInstance().putInConfigCache("handlers", config);
        }
        config.setEnabled(true);
        List<Object> handlers = config.getHandlers();
        if(handlers == null) {
            handlers = new ArrayList<>();
            config.setHandlers(handlers);
        }
        String hname = DeploymentManagerHandler.class.getName()+"@springboot";
        if(!handlers.contains(hname)) {
            handlers.add(hname);
        }
        List<String> defaultHandlers = config.getDefaultHandlers();
        if(defaultHandlers == null) {
            defaultHandlers = new ArrayList<>();
            config.setDefaultHandlers(defaultHandlers);
        }
        if(!defaultHandlers.contains("springboot")) {
            defaultHandlers.add("springboot");
        }

    }

    private static final class WebEnvironmentPropertySourceInitializer
            implements ApplicationListener<ApplicationEnvironmentPreparedEvent>, Ordered {

        private final ServletContext servletContext;

        private WebEnvironmentPropertySourceInitializer(ServletContext servletContext) {
            this.servletContext = servletContext;
        }

        @Override
        public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
            ConfigurableEnvironment environment = event.getEnvironment();
            if (environment instanceof ConfigurableWebEnvironment) {
                ((ConfigurableWebEnvironment) environment).initPropertySources(this.servletContext, null);
            }
        }

        @Override
        public int getOrder() {
            return Ordered.HIGHEST_PRECEDENCE;
        }

    }

}
