/*******************************************************************************
 * Copyright (c) 2014 Red Hat Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.fedoraproject.p2.tests;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

import org.eclipse.core.runtime.Platform;
import org.eclipse.equinox.p2.core.IProvisioningAgent;
import org.eclipse.equinox.p2.core.IProvisioningAgentProvider;
import org.eclipse.equinox.p2.repository.artifact.IArtifactRepositoryManager;
import org.eclipse.equinox.p2.repository.metadata.IMetadataRepositoryManager;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.rules.TestName;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;

public class RepositoryTest {

	static final String NAMESPACE = "fedora:";
	// TODO: Create our own test repositories in resoures/ folder
	static final String JAVADIR = NAMESPACE + "/usr/share/java";
	static final String ECLIPSE_DIR = NAMESPACE + "/usr/lib"
						+ (System.getProperty("os.arch").contains("64") ? "64" : "")
						+ "/eclipse/plugins";

	private static BundleContext bc;
	private static IProvisioningAgent agent;
	private static IMetadataRepositoryManager metadataRM;
	private static IArtifactRepositoryManager artifactRM;
	private Path tempDir;

	@Rule
	public TestName testName = new TestName();

    /**
     * All access to p2 services happen through the provisioning agent.
     *
     * @throws Exception
     *             If the provisioning agent could not be created.
     */
	@BeforeClass
	public static void beforeClass() throws Exception {
		bc = Platform.getBundle("org.fedoraproject.p2.tests").getBundleContext();
		ServiceReference<?> sr = bc.getServiceReference(IProvisioningAgentProvider.SERVICE_NAME);
		IProvisioningAgentProvider pr = (IProvisioningAgentProvider) bc.getService(sr);
		agent = pr.createAgent(null);
		metadataRM = (IMetadataRepositoryManager) agent.getService(IMetadataRepositoryManager.SERVICE_NAME);
		artifactRM = (IArtifactRepositoryManager) agent.getService(IArtifactRepositoryManager.SERVICE_NAME);
	}

	@Before
	public void createTestTempDir() throws Exception {
		tempDir = Paths.get("target/repository-test")
				.resolve(testName.getMethodName()).toAbsolutePath();
		delete(tempDir);
		Files.createDirectories(tempDir);
	}

	private void delete(Path path) throws IOException {
		if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))
			for (Path child : Files.newDirectoryStream(path))
				delete(child);

		Files.deleteIfExists(path);
	}

	protected BundleContext getBundleContext () {
		return bc;
	}

	protected IMetadataRepositoryManager getMetadataRepoManager () {
		return metadataRM;
	}

	protected IArtifactRepositoryManager getArtifactRepoManager () {
		return artifactRM;
	}

	protected Path getTempDir () {
		return tempDir;
	}

	protected void writeSclConfig(Path confPath, String name, Path prefix)
			throws Exception {

		Path eclipseRoot = prefix.resolve("usr/lib/eclipse");
		Path archDropins = prefix.resolve("usr/lib/eclipse/dropins");
		Path noarchDropins = prefix.resolve("usr/share/eclipse/dropins");
		Path bundlesDir = prefix.resolve("usr/share/java");

		Files.createDirectories(eclipseRoot);
		Files.createDirectories(archDropins);
		Files.createDirectories(noarchDropins);
		Files.createDirectories(bundlesDir);

		Properties conf = new Properties();
		conf.setProperty("eclipse.root", eclipseRoot.toString());
		conf.setProperty("eclipse.dropins.archful", archDropins.toString());
		conf.setProperty("eclipse.dropins.noarch", noarchDropins.toString());
		conf.setProperty("eclipse.bundles", bundlesDir.toString());
		conf.setProperty("scl.namespace", name);
		conf.setProperty("scl.root", prefix.toString());

		try (OutputStream stream = Files.newOutputStream(confPath)) {
			conf.store(stream, "Conf for SCL " + name);
		}
	}
}
