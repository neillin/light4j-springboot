package com.networknt.springboot;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.servlet.api.DeploymentManager;

import javax.servlet.ServletException;
import java.io.Closeable;
import java.io.IOException;

public class DeploymentManagerHandler implements HttpHandler {

		private final DeploymentManager deploymentManager;

		private final HttpHandler handler;

		public DeploymentManagerHandler() {
			try {
				this.deploymentManager = UndertowServletDeploymentFactory.getDefaultDeploymentManager();
				this.handler = this.deploymentManager.start();
			}
			catch (ServletException ex) {
				throw new RuntimeException(ex);
			}
		}

		@Override
		public void handleRequest(HttpServerExchange exchange) throws Exception {
			this.handler.handleRequest(exchange);
		}

		DeploymentManager getDeploymentManager() {
			return this.deploymentManager;
		}

	}