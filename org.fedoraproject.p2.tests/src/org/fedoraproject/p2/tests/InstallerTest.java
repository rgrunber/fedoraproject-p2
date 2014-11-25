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

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;

import org.fedoraproject.p2.installer.Dropin;
import org.fedoraproject.p2.installer.EclipseInstallationRequest;
import org.fedoraproject.p2.installer.EclipseInstallationResult;
import org.fedoraproject.p2.installer.EclipseInstaller;
import org.fedoraproject.p2.installer.Provide;

interface BuildrootVisitor {
	void visitPlugin(String dropin, String id, String ver);

	void visitFeature(String dropin, String id, String ver);

	void visitSymlink(String dropin, String id);

	void visitProvides(String dropin, String id, String ver);

	void visitRequires(String dropin, String id);
}

/**
 * @author Mikolaj Izdebski
 */
public class InstallerTest extends RepositoryTest {
	private final EclipseInstaller installer;
	private Map<String, List<Plugin>> reactorPlugins;
	private Map<String, List<Plugin>> platformPlugins;
	private Map<String, List<Plugin>> internalPlugins;
	private Map<String, List<Plugin>> externalPlugins;
	private BuildrootVisitor visitor;
	private EclipseInstallationRequest request;
	private Path root;
	private Path buildRoot;
	private Path reactor;

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
		reactorPlugins = new LinkedHashMap<>();
		platformPlugins = new LinkedHashMap<>();
		externalPlugins = new LinkedHashMap<>();
		internalPlugins = new LinkedHashMap<>();

		visitor = createMock(BuildrootVisitor.class);

		root = getTempDir().resolve("root");
		Files.createDirectory(root);
		buildRoot = getTempDir().resolve("buildroot");
		Files.createDirectory(buildRoot);
		reactor = getTempDir().resolve("reactor");
		Files.createDirectory(reactor);

		request = new EclipseInstallationRequest();
		request.setBuildRoot(buildRoot);
		request.setTargetDropinDirectory(Paths.get("dropins"));
		request.setMainPackageId("main");
		request.addPrefix(root);
	}

	private Plugin addPlugin(String id, String ver, Map<String, List<Plugin>> map) {
		List<Plugin> plugins = map.get(id);
		if (plugins == null) {
			plugins = new ArrayList<>();
			map.put(id, plugins);
		}
		Plugin plugin = new Plugin(id, ver);
		plugins.add(plugin);
		return plugin;
	}

	public Plugin addReactorPlugin(String id) {
		return addReactorPlugin(id, "1.0.0");
	}

	public Plugin addReactorPlugin(String id, String ver) {
		return addPlugin(id, ver, reactorPlugins);
	}

	public Plugin addPlatformPlugin(String id) {
		return addPlatformPlugin(id, "1.0.0");
	}

	public Plugin addPlatformPlugin(String id, String ver) {
		return addPlugin(id, ver, platformPlugins);
	}

	public Plugin addInternalPlugin(String id) {
		return addInternalPlugin(id, "1.0.0");
	}

	public Plugin addInternalPlugin(String id, String ver) {
		return addPlugin(id, ver, internalPlugins);
	}

	public Plugin addExternalPlugin(String id) {
		return addExternalPlugin(id, "1.0.0");
	}

	public Plugin addExternalPlugin(String id, String ver) {
		return addPlugin(id, ver, externalPlugins);
	}

	public void performTest() throws Exception {
		for (Path path : collectPlugins(reactorPlugins, reactor))
			request.addPlugin(path);
		collectPlugins(platformPlugins, root.resolve("usr/lib/eclipse/plugins"));
		collectPlugins(internalPlugins,
				root.resolve("usr/lib/eclipse/dropins/foo/eclipse/plugins"));
		collectPlugins(externalPlugins, root.resolve("usr/share/java"));

		EclipseInstallationResult result = installer
				.performInstallation(request);

		replay(visitor);
		visitDropins(buildRoot.resolve("dropins"));
		visitResult(result);
		verify(visitor);
	}

	private Set<Path> collectPlugins(Map<String, List<Plugin>> map, Path dir)
			throws Exception {
		Files.createDirectories(dir);
		LinkedHashSet<Path> result = new LinkedHashSet<>();
		for (Entry<String, List<Plugin>> entry : map.entrySet()) {
			List<Plugin> plugins = entry.getValue();
			for (Plugin plugin : plugins) {
				Path path = dir.resolve(plugin.getId() + "_" + plugin.getVersion() + ".jar");
				plugin.writeBundle(path);
				result.add(path);
			}
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
						String id = name.replaceAll("_.*$", "");
						String ver = name.replaceAll("^.*_", "").replaceAll("\\.jar$", "");
						if (isLink)
							visitor.visitSymlink(dropin, id);
						else if (isPlugin)
							visitor.visitPlugin(dropin, id, ver);
						else if (isFeature)
							visitor.visitFeature(dropin, id, ver);
						else
							fail();
					}
				}
			}
		}
	}

	private void visitResult(EclipseInstallationResult result) {
		assertNotNull(result);
		assertFalse(result.getDropins().isEmpty());
		for (Dropin dropin : result.getDropins()) {
			assertNotNull(dropin);
			assertFalse(dropin.getOsgiProvides().isEmpty());
			Set<String> requires = new LinkedHashSet<>();
			for (Provide provide : dropin.getOsgiProvides()) {
				visitor.visitProvides(dropin.getId(), provide.getId(), provide.getVersion());
				String reqStr = provide.getProperties().get("osgi.requires");
				if (reqStr == null)
					continue;
				for (String req : reqStr.split(","))
					requires.add(req);
			}
			for (String req : requires)
				visitor.visitRequires(dropin.getId(), req);
		}
	}

	public void expectPlugin(String plugin) {
		expectPlugin("main", plugin, "1.0.0");
	}

	public void expectPlugin(String dropin, String plugin) {
		expectPlugin(dropin, plugin, "1.0.0");
	}

	public void expectPlugin(String dropin, String plugin, String version) {
		visitor.visitPlugin(dropin, plugin, version);
		expectLastCall();
	}

	public void expectFeature(String feature) {
		expectFeature("main", feature, "1.0.0");
	}

	public void expectFeature(String dropin, String feature) {
		expectFeature(dropin, feature, "1.0.0");
	}

	public void expectFeature(String dropin, String feature, String version) {
		visitor.visitFeature(dropin, feature, version);
		expectLastCall();
	}

	public void expectSymlink(String plugin) {
		expectSymlink("main", plugin);
	}

	public void expectSymlink(String dropin, String plugin) {
		visitor.visitSymlink(dropin, plugin);
		expectLastCall();
	}

	public void expectRequires(String req) {
		expectRequires("main", req);
	}

	public void expectRequires(String dropin, String req) {
		visitor.visitRequires(dropin, req);
		expectLastCall();
	}

	public void expectProvides(String prov) {
		expectProvides("main", prov, "1.0.0");
	}

	public void expectProvides(String dropin, String prov) {
		expectProvides(dropin, prov, "1.0.0");
	}

	public void expectProvides(String dropin, String prov, String version) {
		visitor.visitProvides(dropin, prov, version);
		expectLastCall();
	}

	// The simplest case possible: one plugin with no deps
	@Test
	public void simpleTest() throws Exception {
		addReactorPlugin("foo");
		expectPlugin("foo");
		expectProvides("foo");
		performTest();
	}

	// Two plugins with no deps
	@Test
	public void twoPluginsTest() throws Exception {
		addReactorPlugin("foo");
		addReactorPlugin("bar");
		expectPlugin("foo");
		expectPlugin("bar");
		expectProvides("foo");
		expectProvides("bar");
		performTest();
	}

	// Directory-shaped plugin
	@Test
	public void dirShapedPlugin() throws Exception {
		addReactorPlugin("foo").addMfEntry("Eclipse-BundleShape", "dir");
		expectPlugin("foo");
		expectProvides("foo");
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
		expectPlugin("baz");
		expectProvides("sub1", "foo");
		expectProvides("sub2", "bar");
		expectProvides("baz");
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
		expectPlugin("C");
		expectProvides("sub", "A");
		expectProvides("sub", "B");
		expectProvides("C");
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
		expectPlugin("B3");
		expectRequires("sub", "B2");
		expectProvides("sub", "A");
		expectProvides("sub", "C");
		expectProvides("sub", "B1");
		expectProvides("different", "B2");
		expectProvides("B3");
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
		expectPlugin("my-plugin");
		expectSymlink("org.apache.commons.io");
		expectSymlink("org.apache.commons.lang");
		expectRequires("org.apache.commons.io");
		expectRequires("org.apache.commons.lang");
		expectProvides("my-plugin");
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
		expectPlugin("my.tests");
		expectSymlink("org.junit");
		expectSymlink("org.hamcrest.core");
		expectRequires("org.junit");
		expectProvides("my.tests");
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
		expectRequires("pkg1", "org.junit");
		expectRequires("pkg2", "org.junit");
		expectProvides("pkg1", "A");
		expectProvides("pkg2", "B");
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
		expectRequires("pkg1", "org.junit");
		expectRequires("pkg2", "org.junit");
		expectRequires("pkg2", "A");
		expectProvides("pkg1", "A");
		expectProvides("pkg2", "B");
		performTest();
	}

	// Unresolved dependencies shouldn't cause installation failure.
	@Test
	public void unresolvedDependencyTest() throws Exception {
		addReactorPlugin("A").requireBundle("non-existent-plugin");
		expectPlugin("A");
		expectProvides("A");
		performTest();
	}

	// Requirement on system bundle is equivalent to dependency on OSGi
	// framework (org.eclipse.osgi)
	@Test
	public void systemBundleTest() throws Exception {
		addExternalPlugin("org.eclipse.osgi");
		addExternalPlugin("system.bundle");
		addReactorPlugin("A").requireBundle("system.bundle");
		expectPlugin("A");
		expectSymlink("org.eclipse.osgi");
		expectRequires("org.eclipse.osgi");
		expectProvides("A");
		performTest();
	}

	// Two unit satisfy one dependency. Both should be symlinked to let OSGi
	// framework choose better provider at runtime.
	@Test
	public void multipleProvidersTest() throws Exception {
		addExternalPlugin("lib1").exportPackage("foo.bar");
		addExternalPlugin("another-lib").exportPackage("foo.bar");
		addReactorPlugin("A").importPackage("foo.bar");
		expectPlugin("A");
		expectSymlink("lib1");
		expectSymlink("another-lib");
		expectRequires("lib1");
		expectRequires("another-lib");
		expectProvides("A");
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
		expectPlugin("A");
		expectSymlink("P4");
		expectSymlink("P5");
		expectRequires("P4");
		expectRequires("P5");
		expectProvides("A");
		performTest();
	}

	// Version ranges should be respected.
	@Test
	public void versionRangeTest() throws Exception {
		addVersionedPlugins();
		addReactorPlugin("A").importPackage("foo.bar;version=\"[2.5,5.0.0)\"");
		expectPlugin("A");
		expectSymlink("P3");
		expectSymlink("P4");
		expectRequires("P3");
		expectRequires("P4");
		expectProvides("A");
		performTest();
	}

	// Version 0 matches everything.
	@Test
	public void anyVersionTest() throws Exception {
		addVersionedPlugins();
		addReactorPlugin("A").importPackage("foo.bar;version=0.0.0");
		expectPlugin("A");
		expectSymlink("P2");
		expectSymlink("P3");
		expectSymlink("P4");
		expectSymlink("P5");
		expectRequires("P2");
		expectRequires("P3");
		expectRequires("P4");
		expectRequires("P5");
		expectProvides("A");
		performTest();
	}

	// Symlinks for optional dependencies should be created.
	@Test
	public void optionalDependencyTest() throws Exception {
		addExternalPlugin("X");
		addReactorPlugin("A").requireBundle("X;optional=true");
		expectPlugin("A");
		expectSymlink("X");
		expectRequires("X");
		expectProvides("A");
		performTest();
	}

	// Platform requirements shouldn't be generated.
	@Test
	public void platformRequirementTest() throws Exception {
		addPlatformPlugin("Plat");
		addInternalPlugin("Int");
		addExternalPlugin("Ext");
		addReactorPlugin("React").requireBundle("Plat").requireBundle("Int")
				.requireBundle("Ext");
		expectPlugin("React");
		expectSymlink("Ext");
		expectRequires("Int");
		expectRequires("Ext");
		expectProvides("React");
		performTest();
	}

	// Requirements should not be generated for self-dependencies.
	@Test
	public void selfDependencyTest() throws Exception {
		addReactorPlugin("A").requireBundle("B");
		addReactorPlugin("B");
		expectPlugin("A");
		expectPlugin("B");
		expectProvides("A");
		expectProvides("B");
		performTest();
	}

	// Has to be able to deal with two bundles in the reactor that have the same
	// symbolic name, but with different versions.
	@Test
	public void sameBundleSymbolicNamesTest() throws Exception {
		addReactorPlugin("A", "1.0.0");
		addReactorPlugin("A", "2.0.0");
		expectPlugin("main", "A", "1.0.0");
		expectPlugin("main", "A", "2.0.0");
		expectProvides("main", "A", "1.0.0");
		expectProvides("main", "A", "2.0.0");
		performTest();
	}
}
