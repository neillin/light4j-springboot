package com.networknt.springboot;

import com.networknt.server.ShutdownHookProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SpringBootShutdownHookProvider implements ShutdownHookProvider {
    private static final Logger log = LoggerFactory.getLogger(SpringBootStartupHookProvider.class);
    @Override
    public void onShutdown() {
        log.info("Undeploying undertow servlet container ...");
        UndertowServletDeploymentFactory.undeploy();
        log.info("Undertow servlet container undeployed.");
    }

}
