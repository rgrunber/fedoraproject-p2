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

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;

import org.fedoraproject.p2.installer.EclipseInstallationRequest;
import org.fedoraproject.p2.installer.EclipseInstaller;

/**
 * @author Mikolaj Izdebski
 */
class Plugin {
	private final Set<String> imports = new LinkedHashSet<>();
	private final Set<String> exports = new LinkedHashSet<>();
	private final Set<String> requires = new LinkedHashSet<>();
	private final Manifest mf = new Manifest();
	private final Attributes attr = mf.getMainAttributes();

	public Plugin(String id) {
		attr.put(Attributes.Name.MANIFEST_VERSION, "1.0");
		attr.put(new Attributes.Name("Bundle-ManifestVersion"), "2");
		attr.put(new Attributes.Name("Bundle-SymbolicName"), id);
		attr.put(new Attributes.Name("Bundle-Version"), "1.0.0");
	}

	public Plugin importPackage(String name) {
		imports.add(name);
		return this;
	}

	public Plugin exportPackage(String name) {
		exports.add(name);
		return this;
	}

	public Plugin requireBundle(String name) {
		requires.add(name);
		return this;
	}

	public Plugin addMfEntry(String key, String value) {
		attr.put(new Attributes.Name(key), value);
		return this;
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
public class InstallerTest extends RepositoryTest {
	private final EclipseInstaller installer;
	private Path tempDir;
	private Map<String, Plugin> reactorPlugins;
	private Map<String, Plugin> platformPlugins;
	private Map<String, Plugin> internalPlugins;
	private Map<String, Plugin> externalPlugins;
	private BuildrootVisitor visitor;
	private EclipseInstallationRequest request;
	private Path root;
	private Path buildRoot;
	private Path reactor;

	@Rule
	public TestName testName = new TestName();

	public InstallerTest() {
		BundleContext context = getBundleContext();
		ServiceReference<EclipseInstaller> serviceReference = context
				.getServiceReference(EclipseInstaller.class);
		assertNotNull(serviceReference);
		installer = context.getService(serviceReference);
		assertNotNull(installer);
	}

	@Before
	public void setUp() throws Exception {
		tempDir = Paths.get("target/installer-test")
				.resolve(testName.getMethodName()).toAbsolutePath();
		delete(tempDir);
		Files.createDirectories(tempDir);

		reactorPlugins = new LinkedHashMap<>();
		platformPlugins = new LinkedHashMap<>();
		externalPlugins = new LinkedHashMap<>();
		internalPlugins = new LinkedHashMap<>();

		visitor = createMock(BuildrootVisitor.class);

		root = tempDir.resolve("root");
		Files.createDirectory(root);
		buildRoot = tempDir.resolve("buildroot");
		Files.createDirectory(buildRoot);
		reactor = tempDir.resolve("reactor");
		Files.createDirectory(reactor);

		request = new EclipseInstallationRequest();
		request.setBuildRoot(buildRoot);
		request.setTargetDropinDirectory(Paths.get("dropins"));
		request.setMainPackageId("main");
		request.addPrefix(root);
	}

	private void delete(Path path) throws IOException {
		if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))
			for (Path child : Files.newDirectoryStream(path))
				delete(child);

		Files.deleteIfExists(path);
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

	public Plugin addPlatformPlugin(String id) {
		return addPlugin(id, platformPlugins);
	}

	public Plugin addIntarnalPlugin(String id) {
		return addPlugin(id, internalPlugins);
	}

	public Plugin addExternalPlugin(String id) {
		return addPlugin(id, externalPlugins);
	}

	public void performTest() throws Exception {
		for (Path path : collectPlugins(reactorPlugins, reactor))
			request.addPlugin(path);
		collectPlugins(platformPlugins, root.resolve("usr/lib/eclipse/plugins"));
		collectPlugins(internalPlugins,
				root.resolve("usr/lib/eclipse/dropins/foo/eclipse/plugins"));
		collectPlugins(externalPlugins, root.resolve("usr/share/java"));

		installer.performInstallation(request);

		replay(visitor);
		visitDropins(buildRoot.resolve("dropins"));
		verify(visitor);
	}

	private Set<Path> collectPlugins(Map<String, Plugin> plugins, Path dir)
			throws Exception {
		Files.createDirectories(dir);
		LinkedHashSet<Path> result = new LinkedHashSet<>();
		for (Entry<String, Plugin> entry : plugins.entrySet()) {
			String id = entry.getKey();
			Plugin plugin = entry.getValue();
			Path path = dir.resolve(id + ".jar");
			plugin.writeBundle(path);
			result.add(path);
		}
		return result;
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
						else if (isPlugin)
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

	// The simplest case possible: one plugin with no deps
	@Test
	public void simpleTest() throws Exception {
		addReactorPlugin("foo");
		expectPlugin("main", "foo");
		performTest();
	}

	// Two plugins with no deps
	@Test
	public void twoPluginsTest() throws Exception {
		addReactorPlugin("foo");
		addReactorPlugin("bar");
		expectPlugin("main", "foo");
		expectPlugin("main", "bar");
		performTest();
	}

	// Directory-shaped plugin
	@Test
	public void dirShapedPlugin() throws Exception {
		addReactorPlugin("foo").addMfEntry("Eclipse-BundleShape", "dir");
		expectPlugin("main", "foo");
		performTest();
		Path dir = buildRoot.resolve("dropins/main/eclipse/plugins/foo_1.0.0");
		assertTrue(Files.isDirectory(dir, LinkOption.NOFOLLOW_LINKS));
	}

	// Two plugins manually assigned to subpackages, third implicitly installed
	// to main pkg
	@Test
	public void subpackageSplitTest() throws Exception {
		addReactorPlugin("foo");
		addReactorPlugin("bar");
		addReactorPlugin("baz");
		request.addPackageMapping("foo", "sub1");
		request.addPackageMapping("bar", "sub2");
		expectPlugin("sub1", "foo");
		expectPlugin("sub2", "bar");
		expectPlugin("main", "baz");
		performTest();
	}

	// Plugin B is required by A, hence it is getting installed in subpackage
	// together with A.
	@Test
	public void interdepSplitTest() throws Exception {
		addReactorPlugin("A").requireBundle("B");
		addReactorPlugin("B");
		addReactorPlugin("C");
		request.addPackageMapping("A", "sub");
		expectPlugin("sub", "A");
		expectPlugin("sub", "B");
		expectPlugin("main", "C");
		performTest();
	}

	// Plugins B1,B2 are required by both A and C, which are installed to
	// different supackages. Installer puts B1 next to A and C, but puts B2 in
	// different package because this was explicitly requested by user. B3 lands
	// in main package as it's not required by anything and not explicitly put
	// in any package by user.
	@Test
	public void interdepCommonTest() throws Exception {
		addReactorPlugin("A").requireBundle("B1").requireBundle("B2");
		addReactorPlugin("B1");
		addReactorPlugin("B2");
		addReactorPlugin("B3");
		addReactorPlugin("C").requireBundle("B2").requireBundle("B1");
		request.addPackageMapping("A", "sub");
		request.addPackageMapping("C", "sub");
		request.addPackageMapping("B2", "different");
		expectPlugin("sub", "A");
		expectPlugin("sub", "C");
		expectPlugin("sub", "B1");
		expectPlugin("different", "B2");
		expectPlugin("main", "B3");
		performTest();
	}

	// Plugin B is required by both A and C, which are installed to different
	// subpackages. Installation fails as installer cannot guess where to
	// install B.
	@Test(expected = RuntimeException.class)
	public void interdepImpossibleSplitTest() throws Exception {
		addReactorPlugin("A").requireBundle("B");
		addReactorPlugin("B");
		addReactorPlugin("C").requireBundle("B");
		request.addPackageMapping("A", "sub1");
		request.addPackageMapping("C", "sub2");
		performTest();
	}

	private void addCommonsBundles() {
		addExternalPlugin("org.apache.commons.io").exportPackage(
				"org.apache.commons.io");
		addExternalPlugin("org.apache.commons.lang").exportPackage(
				"org.apache.commons.lang");
		addExternalPlugin("org.apache.commons.net").exportPackage(
				"org.apache.commons.net");
	}

	// One bundle which has dependency on two external libs, one through
	// Require-Bundle and one through Import-Package. Both libs are expected to
	// be symlinked next to our plugin.
	@Test
	public void symlinkTest() throws Exception {
		addCommonsBundles();
		Plugin myPlugin = addReactorPlugin("my-plugin");
		myPlugin.requireBundle("org.apache.commons.io");
		myPlugin.importPackage("org.apache.commons.lang");
		expectPlugin("main", "my-plugin");
		expectSymlink("main", "org.apache.commons.io");
		expectSymlink("main", "org.apache.commons.lang");
		performTest();
	}

	private void addJunitBundles() {
		addExternalPlugin("org.junit").requireBundle("org.hamcrest.core")
				.exportPackage("org.junit").exportPackage("junit.framework");
		addExternalPlugin("org.hamcrest.core");
	}

	// Plugin which directly depends on junit. Besides junit, hamcrest is
	// expected to be symlinked too as junit depends on hamcrest.
	@Test
	public void transitiveSymlinkTest() throws Exception {
		addJunitBundles();
		addReactorPlugin("my.tests").importPackage("junit.framework");
		expectPlugin("main", "my.tests");
		expectSymlink("main", "org.junit");
		expectSymlink("main", "org.hamcrest.core");
		performTest();
	}

	// Two independant plugins, both require junit. Junit and hamcrest must be
	// symlinked next to both plugins.
	@Test
	public void indepPluginsCommonDep() throws Exception {
		addJunitBundles();
		addReactorPlugin("A").importPackage("junit.framework");
		addReactorPlugin("B").requireBundle("org.junit");
		request.addPackageMapping("A", "pkg1");
		request.addPackageMapping("B", "pkg2");
		expectPlugin("pkg1", "A");
		expectSymlink("pkg1", "org.junit");
		expectSymlink("pkg1", "org.hamcrest.core");
		expectPlugin("pkg2", "B");
		expectSymlink("pkg2", "org.junit");
		expectSymlink("pkg2", "org.hamcrest.core");
		performTest();
	}

	// FIXME this doesn't work currently
	@Ignore
	// Two plugins A and B, where B requires A. Both require junit. Junit and
	// hamcrest are symlinked only next to A.
	@Test
	public void depPluginsCommonDep() throws Exception {
		addJunitBundles();
		addReactorPlugin("A").importPackage("junit.framework");
		addReactorPlugin("B").requireBundle("org.junit").requireBundle("A");
		request.addPackageMapping("A", "pkg1");
		request.addPackageMapping("B", "pkg2");
		expectPlugin("pkg1", "A");
		expectSymlink("pkg1", "org.junit");
		expectSymlink("pkg1", "org.hamcrest.core");
		expectPlugin("pkg2", "B");
		performTest();
	}

	// Unresolved dependencies shouldn't cause installation failure.
	@Test
	public void unresolvedDependencyTest() throws Exception {
		addReactorPlugin("A").requireBundle("non-existent-plugin");
		expectPlugin("main", "A");
		performTest();
	}

	// Requirement on system bundle is equivalent to dependency on OSGi
	// framework (org.eclipse.osgi)
	@Test
	public void systemBundleTest() throws Exception {
		addExternalPlugin("org.eclipse.osgi");
		addExternalPlugin("system.bundle");
		addReactorPlugin("A").requireBundle("system.bundle");
		expectPlugin("main", "A");
		expectSymlink("main", "org.eclipse.osgi");
		performTest();
	}

	// Two unit satisfy one dependency. Both should be symlinked to let OSGi
	// framework choose better provider at runtime.
	@Test
	public void multipleProvidersTest() throws Exception {
		addExternalPlugin("lib1").exportPackage("foo.bar");
		addExternalPlugin("another-lib").exportPackage("foo.bar");
		addReactorPlugin("A").importPackage("foo.bar");
		expectPlugin("main", "A");
		expectSymlink("main", "lib1");
		expectSymlink("main", "another-lib");
		performTest();
	}

	private void addVersionedPlugins() {
		addExternalPlugin("P2").exportPackage("foo.bar;version=2");
		addExternalPlugin("P3").exportPackage("foo.bar;version=3");
		addExternalPlugin("P4").exportPackage("foo.bar;version=4");
		addExternalPlugin("P5").exportPackage("foo.bar;version=5");
	}

	// Versioned requirement must be satisfied only by bundles with matching
	// versions.
	@Test
	public void versionedRequirementTest() throws Exception {
		addVersionedPlugins();
		addReactorPlugin("A").importPackage("foo.bar;version=4.0.0");
		expectPlugin("main", "A");
		expectSymlink("main", "P4");
		expectSymlink("main", "P5");
		performTest();
	}

	// Version ranges should be respected.
	@Test
	public void versionRangeTest() throws Exception {
		addVersionedPlugins();
		addReactorPlugin("A").importPackage("foo.bar;version=\"[2.5,5.0.0)\"");
		expectPlugin("main", "A");
		expectSymlink("main", "P3");
		expectSymlink("main", "P4");
		performTest();
	}

	// Version 0 matches everything.
	@Test
	public void anyVersionTest() throws Exception {
		addVersionedPlugins();
		addReactorPlugin("A").importPackage("foo.bar;version=0.0.0");
		expectPlugin("main", "A");
		expectSymlink("main", "P2");
		expectSymlink("main", "P3");
		expectSymlink("main", "P4");
		expectSymlink("main", "P5");
		performTest();
	}

	// Symlinks for optional dependencies should be created.
	@Test
	public void optionalDependencyTest() throws Exception {
		addExternalPlugin("X");
		addReactorPlugin("A").requireBundle("X;optional=true");
		expectPlugin("main", "A");
		expectSymlink("main", "X");
		performTest();
	}
}
