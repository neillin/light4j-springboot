package com.networknt.springboot;

import com.networknt.config.Config;
import com.networknt.server.Server;
import com.networknt.server.ShutdownHookProvider;
import com.networknt.server.StartupHookProvider;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Light4jSpringBoot {
    private static final String SERVICE_CONFIG = "service.singletons";

    public static void bootstrap(Class<?> springBootConfigClass, String... args) {
        Map<String, Object> config = Config.getInstance().getJsonMapConfig("values");
        if(config == null) {
            config = new HashMap<>();
            Config.getInstance().putInConfigCache("values", config);
        }
        List<Map<String, Object>> serviceConfig = (List<Map<String, Object>>)config.get(SERVICE_CONFIG);
        if(serviceConfig == null) {
            serviceConfig = new ArrayList<>();
            config.put(SERVICE_CONFIG, serviceConfig);
        }
        Map<String, Object> startupHookProviders = serviceConfig.stream().filter(m -> m.containsKey(StartupHookProvider.class.getName())).findFirst().orElse(null);
        List<String> values = null;
        if(startupHookProviders == null) {
            startupHookProviders = new HashMap<>();
            values = new ArrayList<>();
            startupHookProviders.put(StartupHookProvider.class.getName(), values);
            serviceConfig.add(startupHookProviders);
        } else {
            Object value = startupHookProviders.get(StartupHookProvider.class.getName());
            if(value instanceof List) {
                values = (List<String>) value;
            } else {
                values = new ArrayList<>();
                values.add((String)value);
                startupHookProviders.put(StartupHookProvider.class.getName(), values);
            }
        }
        if(!values.contains(SpringBootStartupHookProvider.class.getName())) {
            values.add(SpringBootStartupHookProvider.class.getName());
        }
        values = null;
        Map<String, Object> shutdownHookProviders = serviceConfig.stream().filter(m -> m.containsKey(ShutdownHookProvider.class.getName())).findFirst().orElse(null);
        if(shutdownHookProviders == null) {
            shutdownHookProviders = new HashMap<>();
            values = new ArrayList<>();
            shutdownHookProviders.put(ShutdownHookProvider.class.getName(), values);
            serviceConfig.add(shutdownHookProviders);
        } else {
            Object value = shutdownHookProviders.get(ShutdownHookProvider.class.getName());
            if(value instanceof List) {
                values = (List<String>) value;
            } else {
                values = new ArrayList<>();
                values.add((String)value);
                shutdownHookProviders.put(ShutdownHookProvider.class.getName(), values);
            }
        }
        if(!values.contains(SpringBootShutdownHookProvider.class.getName())) {
            values.add(SpringBootShutdownHookProvider.class.getName());
        }
        SpringBootStartupHookProvider.springBootConfigClass = springBootConfigClass;
        SpringBootStartupHookProvider.args = args;
        Server.main(args);
    }
}
