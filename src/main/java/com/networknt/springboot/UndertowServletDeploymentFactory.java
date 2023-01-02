/*
 * Copyright 2012-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.networknt.springboot;

import io.undertow.server.HttpHandler;
import io.undertow.server.handlers.resource.*;
import io.undertow.server.session.SessionManager;
import io.undertow.servlet.Servlets;
import io.undertow.servlet.api.*;
import io.undertow.servlet.core.DeploymentImpl;
import io.undertow.servlet.handlers.DefaultServlet;
import io.undertow.servlet.util.ImmediateInstanceFactory;
import org.springframework.boot.web.server.ErrorPage;
import org.springframework.boot.web.server.MimeMappings.Mapping;
import org.springframework.boot.web.server.WebServer;
import org.springframework.boot.web.servlet.ServletContextInitializer;
import org.springframework.boot.web.servlet.server.AbstractServletWebServerFactory;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.Assert;

import javax.servlet.ServletContainerInitializer;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.annotation.HandlesTypes;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLEncoder;
import java.time.Duration;
import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Pattern;

public class UndertowServletDeploymentFactory extends AbstractServletWebServerFactory { //implements ResourceLoaderAware {

	private static final Pattern ENCODED_SLASH = Pattern.compile("%2F", Pattern.LITERAL);

	private static final Set<Class<?>> NO_CLASSES = Collections.emptySet();

	private static DeploymentManager defaultDeploymentManager;

	static DeploymentManager getDefaultDeploymentManager() {
		return defaultDeploymentManager;
	}

	static void undeploy() {
		if(defaultDeploymentManager != null) {
			try {
				defaultDeploymentManager.stop();
				defaultDeploymentManager.undeploy();
				defaultDeploymentManager = null;
			} catch (ServletException ex) {
				throw new RuntimeException(ex);
			}
		}
	}

	private Set<UndertowDeploymentInfoCustomizer> deploymentInfoCustomizers = new LinkedHashSet<>();

	private ResourceLoader resourceLoader;

	private boolean eagerFilterInit = true;

	private boolean preservePathOnForward = false;

//	private DeploymentManager deploymentManager;

	public UndertowServletDeploymentFactory() {
		getJsp().setRegistered(false);
	}

	/**
	 * Create a new {@link UndertowServletDeploymentFactory} with the specified context
	 * path.
	 * @param contextPath the root context path
	 */
	public UndertowServletDeploymentFactory(String contextPath) {
		super(contextPath, -1);
		getJsp().setRegistered(false);
	}


	/**
	 * Set {@link UndertowDeploymentInfoCustomizer}s that should be applied to the
	 * Undertow {@link DeploymentInfo}. Calling this method will replace any existing
	 * customizers.
	 * @param customizers the customizers to set
	 */
	public void setDeploymentInfoCustomizers(Collection<? extends UndertowDeploymentInfoCustomizer> customizers) {
		Assert.notNull(customizers, "Customizers must not be null");
		this.deploymentInfoCustomizers = new LinkedHashSet<>(customizers);
	}

	/**
	 * Add {@link UndertowDeploymentInfoCustomizer}s that should be used to customize the
	 * Undertow {@link DeploymentInfo}.
	 * @param customizers the customizers to add
	 */
	public void addDeploymentInfoCustomizers(UndertowDeploymentInfoCustomizer... customizers) {
		Assert.notNull(customizers, "UndertowDeploymentInfoCustomizers must not be null");
		this.deploymentInfoCustomizers.addAll(Arrays.asList(customizers));
	}

	/**
	 * Returns a mutable collection of the {@link UndertowDeploymentInfoCustomizer}s that
	 * will be applied to the Undertow {@link DeploymentInfo}.
	 * @return the customizers that will be applied
	 */
	public Collection<UndertowDeploymentInfoCustomizer> getDeploymentInfoCustomizers() {
		return this.deploymentInfoCustomizers;
	}

//	@Override
	public void setResourceLoader(ResourceLoader resourceLoader) {
		this.resourceLoader = resourceLoader;
	}

	/**
	 * Return if filters should be eagerly initialized.
	 * @return {@code true} if filters are eagerly initialized, otherwise {@code false}.
	 * @since 2.4.0
	 */
	public boolean isEagerFilterInit() {
		return this.eagerFilterInit;
	}

	/**
	 * Set whether filters should be eagerly initialized.
	 * @param eagerFilterInit {@code true} if filters are eagerly initialized, otherwise
	 * {@code false}.
	 * @since 2.4.0
	 */
	public void setEagerFilterInit(boolean eagerFilterInit) {
		this.eagerFilterInit = eagerFilterInit;
	}

	/**
	 * Return whether the request path should be preserved on forward.
	 * @return {@code true} if the path should be preserved when a request is forwarded,
	 * otherwise {@code false}.
	 * @since 2.4.0
	 */
	public boolean isPreservePathOnForward() {
		return this.preservePathOnForward;
	}

	/**
	 * Set whether the request path should be preserved on forward.
	 * @param preservePathOnForward {@code true} if the path should be preserved when a
	 * request is forwarded, otherwise {@code false}.
	 * @since 2.4.0
	 */
	public void setPreservePathOnForward(boolean preservePathOnForward) {
		this.preservePathOnForward = preservePathOnForward;
	}

	@Override
	public WebServer getWebServer(ServletContextInitializer... initializers) {
//		Builder builder = this.delegate.createBuilder(this);
//		DeploymentManager manager = createManager(initializers);
//		return getUndertowWebServer(builder, manager, getPort());
		throw new IllegalStateException("This method should not be called since undertow server already started.");
	}

	public void init(Consumer<ServletContext> initCallback) {
		ServiceLoader<ServletContainerInitializer> initializers = ServiceLoader.load(ServletContainerInitializer.class, getServletClassLoader());
		List<ServletContainerInitializer> list = new ArrayList<>();
		initializers.forEach(list::add);
		if(initCallback != null) {
			list.add(new Initializer(initCallback));
		}
		DeploymentInfo deployment = Servlets.deployment();
		registerServletContainerInitializer(deployment, list.toArray(new ServletContainerInitializer[list.size()]));
		deployment.setClassLoader(getServletClassLoader());
		deployment.setContextPath(getContextPath());
		deployment.setDisplayName(getDisplayName());
		deployment.setDeploymentName("networknt-spring-boot");
		if (isRegisterDefaultServlet()) {
			deployment.addServlet(Servlets.servlet("default", DefaultServlet.class));
		}
		configureErrorPages(deployment);
		deployment.setServletStackTraces(ServletStackTraces.NONE);
		deployment.setResourceManager(getDocumentRootResourceManager());
		deployment.setTempDir(createTempDir("undertow"));
		deployment.setEagerFilterInit(this.eagerFilterInit);
		deployment.setPreservePathOnForward(this.preservePathOnForward);
		configureMimeMappings(deployment);
		configureWebListeners(deployment);
		for (UndertowDeploymentInfoCustomizer customizer : this.deploymentInfoCustomizers) {
			customizer.customize(deployment);
		}
		if (getSession().isPersistent()) {
			File dir = getValidSessionStoreDir();
			deployment.setSessionPersistenceManager(new FileSessionPersistence(dir));
		}
		addLocaleMappings(deployment);
		DeploymentManager manager = Servlets.newContainer().addDeployment(deployment);
		manager.deploy();
		if (manager.getDeployment() instanceof DeploymentImpl) {
			removeSuperfluousMimeMappings((DeploymentImpl) manager.getDeployment(), deployment);
		}
		SessionManager sessionManager = manager.getDeployment().getSessionManager();
		Duration timeoutDuration = getSession().getTimeout();
		int sessionTimeout = (isZeroOrLess(timeoutDuration) ? -1 : (int) timeoutDuration.getSeconds());
		sessionManager.setDefaultSessionTimeout(sessionTimeout);
		undeploy();
		defaultDeploymentManager = manager;
	}

	private void configureWebListeners(DeploymentInfo deployment) {
		for (String className : getWebListenerClassNames()) {
			try {
				deployment.addListener(new ListenerInfo(loadWebListenerClass(className)));
			}
			catch (ClassNotFoundException ex) {
				throw new IllegalStateException("Failed to load web listener class '" + className + "'", ex);
			}
		}
	}

	@SuppressWarnings("unchecked")
	private Class<? extends EventListener> loadWebListenerClass(String className) throws ClassNotFoundException {
		return (Class<? extends EventListener>) getServletClassLoader().loadClass(className);
	}

	private boolean isZeroOrLess(Duration timeoutDuration) {
		return timeoutDuration == null || timeoutDuration.isZero() || timeoutDuration.isNegative();
	}

	private void addLocaleMappings(DeploymentInfo deployment) {
		getLocaleCharsetMappings().forEach(
				(locale, charset) -> deployment.addLocaleCharsetMapping(locale.toString(), charset.toString()));
	}

	private void registerServletContainerInitializer(DeploymentInfo deployment,
			ServletContainerInitializer... initializers) {
//		ServletContextInitializer[] mergedInitializers = mergeInitializers(initializers);
//		Initializer initializer = new Initializer(mergedInitializers);
		for(ServletContainerInitializer sci: initializers) {
			HandlesTypes ht = sci.getClass().getAnnotation(HandlesTypes.class);
			deployment.addServletContainerInitializer(new ServletContainerInitializerInfo(sci.getClass(),
					new ImmediateInstanceFactory<ServletContainerInitializer>(sci), ht != null && ht.value().length > 0 ? new HashSet<>(Arrays.asList(ht.value())) : NO_CLASSES));
		}
	}

	private ClassLoader getServletClassLoader() {
		if (this.resourceLoader != null) {
			return this.resourceLoader.getClassLoader();
		}
		return getClass().getClassLoader();
	}

	private ResourceManager getDocumentRootResourceManager() {
		File root = getValidDocumentRoot();
		File docBase = getCanonicalDocumentRoot(root);
		List<URL> metaInfResourceUrls = getUrlsOfJarsWithMetaInfResources();
		List<URL> resourceJarUrls = new ArrayList<>();
		List<ResourceManager> managers = new ArrayList<>();
		ResourceManager rootManager = (docBase.isDirectory() ? new FileResourceManager(docBase, 0)
				: new JarResourceManager(docBase));
		if (root != null) {
			rootManager = new LoaderHidingResourceManager(rootManager);
		}
		managers.add(rootManager);
		for (URL url : metaInfResourceUrls) {
			if ("file".equals(url.getProtocol())) {
				try {
					File file = new File(url.toURI());
					if (file.isFile()) {
						resourceJarUrls.add(new URL("jar:" + url + "!/"));
					}
					else {
						managers.add(new FileResourceManager(new File(file, "META-INF/resources"), 0));
					}
				}
				catch (Exception ex) {
					throw new RuntimeException(ex);
				}
			}
			else {
				resourceJarUrls.add(url);
			}
		}
		managers.add(new MetaInfResourcesResourceManager(resourceJarUrls));
		return new CompositeResourceManager(managers.toArray(new ResourceManager[0]));
	}

	private File getCanonicalDocumentRoot(File docBase) {
		try {
			File root = (docBase != null) ? docBase : createTempDir("undertow-docbase");
			return root.getCanonicalFile();
		}
		catch (IOException ex) {
			throw new IllegalStateException("Cannot get canonical document root", ex);
		}
	}

	private void configureErrorPages(DeploymentInfo deployment) {
		for (ErrorPage errorPage : getErrorPages()) {
			deployment.addErrorPage(getUndertowErrorPage(errorPage));
		}
	}

	private io.undertow.servlet.api.ErrorPage getUndertowErrorPage(ErrorPage errorPage) {
		if (errorPage.getStatus() != null) {
			return new io.undertow.servlet.api.ErrorPage(errorPage.getPath(), errorPage.getStatusCode());
		}
		if (errorPage.getException() != null) {
			return new io.undertow.servlet.api.ErrorPage(errorPage.getPath(), errorPage.getException());
		}
		return new io.undertow.servlet.api.ErrorPage(errorPage.getPath());
	}

	private void configureMimeMappings(DeploymentInfo deployment) {
		for (Mapping mimeMapping : getMimeMappings()) {
			deployment.addMimeMapping(new MimeMapping(mimeMapping.getExtension(), mimeMapping.getMimeType()));
		}
	}

	private void removeSuperfluousMimeMappings(DeploymentImpl deployment, DeploymentInfo deploymentInfo) {
		// DeploymentManagerImpl will always add MimeMappings.DEFAULT_MIME_MAPPINGS
		// but we only want ours
		Map<String, String> mappings = new HashMap<>();
		for (MimeMapping mapping : deploymentInfo.getMimeMappings()) {
			mappings.put(mapping.getExtension().toLowerCase(Locale.ENGLISH), mapping.getMimeType());
		}
		deployment.setMimeExtensionMappings(mappings);
	}



//	private HttpHandlerFactory getCookieHandlerFactory(Deployment deployment) {
//		SameSite sessionSameSite = getSession().getCookie().getSameSite();
//		List<CookieSameSiteSupplier> suppliers = new ArrayList<>();
//		if (sessionSameSite != null) {
//			String sessionCookieName = deployment.getServletContext().getSessionCookieConfig().getName();
//			suppliers.add(CookieSameSiteSupplier.of(sessionSameSite).whenHasName(sessionCookieName));
//		}
//		if (!CollectionUtils.isEmpty(getCookieSameSiteSuppliers())) {
//			suppliers.addAll(getCookieSameSiteSuppliers());
//		}
//		return (!suppliers.isEmpty()) ? (next) -> new SuppliedSameSiteCookieHandler(next, suppliers) : null;
//	}

	/**
	 * {@link ServletContainerInitializer} to initialize {@link ServletContextInitializer
	 * ServletContextInitializers}.
	 */
	private static class Initializer implements ServletContainerInitializer {

		private final Consumer<ServletContext> initializer;
		Initializer( Consumer<ServletContext> initializer) {
			this.initializer = initializer;
		}

		@Override
		public void onStartup(Set<Class<?>> classes, ServletContext servletContext) throws ServletException {
			this.initializer.accept(servletContext);
		}

	}

	/**
	 * {@link ResourceManager} that exposes resource in {@code META-INF/resources}
	 * directory of nested (in {@code BOOT-INF/lib} or {@code WEB-INF/lib}) jars.
	 */
	private static final class MetaInfResourcesResourceManager implements ResourceManager {

		private final List<URL> metaInfResourceJarUrls;

		private MetaInfResourcesResourceManager(List<URL> metaInfResourceJarUrls) {
			this.metaInfResourceJarUrls = metaInfResourceJarUrls;
		}

		@Override
		public void close() throws IOException {
		}

		@Override
		public Resource getResource(String path) {
			for (URL url : this.metaInfResourceJarUrls) {
				URLResource resource = getMetaInfResource(url, path);
				if (resource != null) {
					return resource;
				}
			}
			return null;
		}

		@Override
		public boolean isResourceChangeListenerSupported() {
			return false;
		}

		@Override
		public void registerResourceChangeListener(ResourceChangeListener listener) {
		}

		@Override
		public void removeResourceChangeListener(ResourceChangeListener listener) {

		}

		private URLResource getMetaInfResource(URL resourceJar, String path) {
			try {
				String urlPath = URLEncoder.encode(ENCODED_SLASH.matcher(path).replaceAll("/"), "UTF-8");
				URL resourceUrl = new URL(resourceJar + "META-INF/resources" + urlPath);
				URLResource resource = new URLResource(resourceUrl, path);
				if (resource.getContentLength() < 0) {
					return null;
				}
				return resource;
			}
			catch (Exception ex) {
				return null;
			}
		}

	}

	/**
	 * {@link ResourceManager} to hide Spring Boot loader classes.
	 */
	private static final class LoaderHidingResourceManager implements ResourceManager {

		private final ResourceManager delegate;

		private LoaderHidingResourceManager(ResourceManager delegate) {
			this.delegate = delegate;
		}

		@Override
		public Resource getResource(String path) throws IOException {
			if (path.startsWith("/org/springframework/boot")) {
				return null;
			}
			return this.delegate.getResource(path);
		}

		@Override
		public boolean isResourceChangeListenerSupported() {
			return this.delegate.isResourceChangeListenerSupported();
		}

		@Override
		public void registerResourceChangeListener(ResourceChangeListener listener) {
			this.delegate.registerResourceChangeListener(listener);
		}

		@Override
		public void removeResourceChangeListener(ResourceChangeListener listener) {
			this.delegate.removeResourceChangeListener(listener);
		}

		@Override
		public void close() throws IOException {
			this.delegate.close();
		}

	}

//	/**
//	 * {@link HttpHandler} to apply {@link CookieSameSiteSupplier supplied}
//	 * {@link SameSite} cookie values.
//	 */
//	private static class SuppliedSameSiteCookieHandler implements HttpHandler {
//
//		private final HttpHandler next;
//
//		private final List<CookieSameSiteSupplier> suppliers;
//
//		SuppliedSameSiteCookieHandler(HttpHandler next, List<CookieSameSiteSupplier> suppliers) {
//			this.next = next;
//			this.suppliers = suppliers;
//		}
//
//		@Override
//		public void handleRequest(HttpServerExchange exchange) throws Exception {
//			exchange.addResponseCommitListener(this::beforeCommit);
//			this.next.handleRequest(exchange);
//		}
//
//		private void beforeCommit(HttpServerExchange exchange) {
//			for (Cookie cookie : exchange.responseCookies()) {
//				SameSite sameSite = getSameSite(asServletCookie(cookie));
//				if (sameSite != null) {
//					cookie.setSameSiteMode(sameSite.attributeValue());
//				}
//			}
//		}
//
//		private javax.servlet.http.Cookie asServletCookie(Cookie cookie) {
//			PropertyMapper map = PropertyMapper.get().alwaysApplyingWhenNonNull();
//			javax.servlet.http.Cookie result = new javax.servlet.http.Cookie(cookie.getName(), cookie.getValue());
//			map.from(cookie::getComment).to(result::setComment);
//			map.from(cookie::getDomain).to(result::setDomain);
//			map.from(cookie::getMaxAge).to(result::setMaxAge);
//			map.from(cookie::getPath).to(result::setPath);
//			result.setSecure(cookie.isSecure());
//			result.setVersion(cookie.getVersion());
//			result.setHttpOnly(cookie.isHttpOnly());
//			return result;
//		}
//
//		private SameSite getSameSite(javax.servlet.http.Cookie cookie) {
//			for (CookieSameSiteSupplier supplier : this.suppliers) {
//				SameSite sameSite = supplier.getSameSite(cookie);
//				if (sameSite != null) {
//					return sameSite;
//				}
//			}
//			return null;
//		}
//
//	}

}
