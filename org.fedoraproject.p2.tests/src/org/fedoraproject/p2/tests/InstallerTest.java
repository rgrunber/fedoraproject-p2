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

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;

import org.fedoraproject.p2.installer.EclipseInstallationRequest;
import org.fedoraproject.p2.installer.EclipseInstaller;

/**
 * @author Mikolaj Izdebski
 */
class Plugin {
	private final String id;
	private final Set<String> imports = new LinkedHashSet<>();
	private final Set<String> exports = new LinkedHashSet<>();
	private final Set<String> requires = new LinkedHashSet<>();

	public Plugin(String id) {
		this.id = id;
	}

	public void importPackage(String name) {
		imports.add(name);
	}

	public void exportPackage(String name) {
		exports.add(name);
	}

	public void requireBundle(String name) {
		requires.add(name);
	}

	private void addManifestSet(Attributes attr, String key, Set<String> values) {
		Iterator<String> it = values.iterator();
		if (!it.hasNext())
			return;
		StringBuilder sb = new StringBuilder(it.next());
		while (it.hasNext())
			sb.append(',').append(it.next());
		attr.put(new Attributes.Name(key), sb.toString());
	}

	public void writeBundle(Path path) throws IOException {
		Manifest mf = new Manifest();
		Attributes attr = mf.getMainAttributes();
		attr.put(Attributes.Name.MANIFEST_VERSION, "1.0");
		attr.put(new Attributes.Name("Bundle-ManifestVersion"), "2");
		attr.put(new Attributes.Name("Bundle-SymbolicName"), id);
		attr.put(new Attributes.Name("Bundle-Version"), "1.0.0");
		addManifestSet(attr, "Import-Package", imports);
		addManifestSet(attr, "Export-Package", exports);
		addManifestSet(attr, "Require-Bundle", requires);
		try (OutputStream os = Files.newOutputStream(path)) {
			try (OutputStream jos = new JarOutputStream(os, mf)) {
			}
		}
	}
}

interface BuildrootVisitor {
	void visitPlugin(String dropin, String id);

	void visitFeature(String dropin, String id);

	void visitSymlink(String dropin, String id);
}

/**
 * @author Mikolaj Izdebski
 */
public class InstallerTest {
	private Path tempDir;
	private EclipseInstaller installer;
	private Map<String, Plugin> reactorPlugins = new LinkedHashMap<>();
	private BuildrootVisitor visitor;

	@Before
	public void setUp() throws Exception {
		tempDir = Files.createTempDirectory("fp-p2-");

		BundleContext context = Activator.getBundleContext();
		ServiceReference<EclipseInstaller> serviceReference = context
				.getServiceReference(EclipseInstaller.class);
		assertNotNull(serviceReference);
		installer = context.getService(serviceReference);
		assertNotNull(installer);

		visitor = createMock(BuildrootVisitor.class);
	}

	private void delete(Path path) throws IOException {
		if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))
			for (Path child : Files.newDirectoryStream(path))
				delete(child);

		Files.delete(path);
	}

	@After
	public void tearDown() throws Exception {
		delete(tempDir);
	}

	private Plugin addPlugin(String id, Map<String, Plugin> map) {
		Plugin plugin = map.get(id);
		if (plugin == null) {
			plugin = new Plugin(id);
			map.put(id, plugin);
		}
		return plugin;
	}

	public Plugin addReactorPlugin(String id) {
		return addPlugin(id, reactorPlugins);
	}

	public void performInstallation() throws Exception {
		Path root = tempDir.resolve("root");
		Files.createDirectory(root);
		Path reactor = tempDir.resolve("reactor");
		Files.createDirectory(reactor);

		EclipseInstallationRequest request = new EclipseInstallationRequest();
		request.setBuildRoot(root);
		request.setTargetDropinDirectory(Paths.get("dropins"));
		request.setMainPackageId("main");

		// Create reactor plugins
		for (Entry<String, Plugin> entry : reactorPlugins.entrySet()) {
			String id = entry.getKey();
			Plugin plugin = entry.getValue();
			Path path = reactor.resolve(id + ".jar");
			plugin.writeBundle(path);
			request.addPlugin(path);
		}

		installer.performInstallation(request);

		replay(visitor);
		visitDropins(root.resolve("dropins"));
		verify(visitor);
	}

	private void visitDropins(Path dropins) throws Exception {
		assertTrue(Files.isDirectory(dropins, LinkOption.NOFOLLOW_LINKS));

		for (Path dropinPath : Files.newDirectoryStream(dropins)) {
			assertTrue(Files.isDirectory(dropinPath, LinkOption.NOFOLLOW_LINKS));
			String dropin = dropinPath.getFileName().toString();

			for (Path dropinSubdir : Files.newDirectoryStream(dropinPath)) {
				assertTrue(Files.isDirectory(dropinSubdir,
						LinkOption.NOFOLLOW_LINKS));
				assertEquals("eclipse", dropinSubdir.getFileName().toString());

				for (Path categoryPath : Files.newDirectoryStream(dropinSubdir)) {
					assertTrue(Files.isDirectory(categoryPath,
							LinkOption.NOFOLLOW_LINKS));
					String cat = categoryPath.getFileName().toString();
					boolean isPlugin = cat.equals("plugins");
					boolean isFeature = cat.equals("features");
					assertTrue(isPlugin ^ isFeature);

					for (Path unit : Files.newDirectoryStream(categoryPath)) {
						String name = unit.getFileName().toString();
						boolean isDir = Files.isDirectory(unit);
						boolean isLink = Files.isSymbolicLink(unit);
						// Either dir-shaped or ends with .jar
						assertTrue(isDir ^ name.endsWith(".jar"));
						// While theoretically possible, symlinks to
						// directory-shaped units are not expected
						assertFalse(isLink && isDir);
						// We never symlink features
						assertFalse(isFeature && isLink);
						String id = name.replaceAll("_.*", "");
						if (isLink)
							visitor.visitSymlink(dropin, id);
						if (isPlugin)
							visitor.visitPlugin(dropin, id);
						else if (isFeature)
							visitor.visitFeature(dropin, id);
						else
							fail();
					}
				}
			}
		}
	}

	public void expectPlugin(String dropin, String plugin) {
		visitor.visitPlugin(dropin, plugin);
		expectLastCall();
	}

	public void expectFeature(String dropin, String plugin) {
		visitor.visitFeature(dropin, plugin);
		expectLastCall();
	}

	public void expectSymlink(String dropin, String plugin) {
		visitor.visitSymlink(dropin, plugin);
		expectLastCall();
	}

	@Test
	public void simpleTest() throws Exception {
		addReactorPlugin("foo");
		expectPlugin("main", "foo");
		performInstallation();
	}

	@Test
	public void twoPluginsTest() throws Exception {
		addReactorPlugin("foo");
		addReactorPlugin("bar");
		expectPlugin("main", "foo");
		expectPlugin("main", "bar");
		performInstallation();
	}
}
