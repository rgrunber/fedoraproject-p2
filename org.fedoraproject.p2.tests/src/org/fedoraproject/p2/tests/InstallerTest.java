/*******************************************************************************
 * Copyright (c) 2014-2018 Red Hat Inc.
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
import org.easymock.IExpectationSetters;
import org.fedoraproject.p2.SCL;
import org.fedoraproject.p2.installer.Dropin;
import org.fedoraproject.p2.installer.EclipseArtifact;
import org.fedoraproject.p2.installer.EclipseInstallationRequest;
import org.fedoraproject.p2.installer.EclipseInstallationResult;
import org.fedoraproject.p2.installer.EclipseInstaller;

interface BuildrootVisitor {
	void visitPlugin(String droplet, String id, String ver);

	void visitFeature(String droplet, String id, String ver);

	void visitSymlink(String droplet, String id);

	void visitProvides(String droplet, String id, String ver);

	void visitRequires(String droplet, String id);
}

/**
 * @author Mikolaj Izdebski
 */
public class InstallerTest extends RepositoryTest {
	private final EclipseInstaller installer;
	private Map<String, List<Plugin>> reactorPlugins;
	private Map<String, List<Feature>> reactorFeatures;
	private Map<String, List<Plugin>> platformPlugins;
	private Map<String, List<Feature>> platformFeatures;
	private Map<String, List<Plugin>> internalPlugins;
	private Map<String, List<Feature>> internalFeatures;
	private Map<String, List<Plugin>> externalPlugins;
	private BuildrootVisitor visitor;
	private EclipseInstallationRequest request;
	private Path root;
	private Path buildRoot;
	private Path reactor;
	private SCL scl;

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
		reactorFeatures = new LinkedHashMap<>();
		platformPlugins = new LinkedHashMap<>();
		platformFeatures = new LinkedHashMap<>();
		internalPlugins = new LinkedHashMap<>();
		internalFeatures = new LinkedHashMap<>();
		externalPlugins = new LinkedHashMap<>();

		visitor = createMock(BuildrootVisitor.class);

		root = getTempDir().resolve("root");
		Files.createDirectory(root);
		buildRoot = getTempDir().resolve("buildroot");
		Files.createDirectory(buildRoot);
		reactor = getTempDir().resolve("reactor");
		Files.createDirectory(reactor);

		request = new EclipseInstallationRequest();
		request.setBuildRoot(buildRoot);
		request.setMainPackageId("main");

		Path sclConf = getTempDir().resolve("eclipse.conf");
		writeSclConfig(sclConf, "", root);
		request.addConfigFile(sclConf);
		scl = new SCL(sclConf);
	}

	private Plugin addPlugin(String id, String ver,
			Map<String, List<Plugin>> map) {
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

	private Feature addFeature(String id, String ver,
			Map<String, List<Feature>> map) {
		List<Feature> features = map.get(id);
		if (features == null) {
			features = new ArrayList<>();
			map.put(id, features);
		}
		Feature feature = new Feature(id, ver);
		features.add(feature);
		return feature;
	}

	public Feature addReactorFeature(String id) {
		return addReactorFeature(id, "1.0.0");
	}

	public Feature addReactorFeature(String id, String ver) {
		return addFeature(id, ver, reactorFeatures);
	}

	public Feature addPlatformFeature(String id) {
		return addPlatformFeature(id, "1.0.0");
	}

	public Feature addPlatformFeature(String id, String ver) {
		return addFeature(id, ver, platformFeatures);
	}

	public Feature addInternalFeature(String id) {
		return addInternalFeature(id, "1.0.0");
	}

	public Feature addInternalFeature(String id, String ver) {
		return addFeature(id, ver, internalFeatures);
	}

	public void ignoreOptionalDeps(String iu) {
		request.addOptionalDepsIgnored(iu);
	}

	public void performTest() throws Exception {
		boolean visitArchful = false;
		for (Plugin plugin : collectPlugins(reactorPlugins, reactor)) {
			EclipseArtifact artifact = new EclipseArtifact(plugin.getPath(),
					false, plugin.isNative());
			artifact.setTargetPackage(plugin.getTargetPackage());
			request.addArtifact(artifact);
			if (plugin.isNative()) {
				visitArchful = true;
			}
		}
		for (Feature feature : collectFeatures(reactorFeatures, reactor)) {
			EclipseArtifact artifact = new EclipseArtifact(feature.getPath(),
					true, false);
			artifact.setTargetPackage(feature.getTargetPackage());
			request.addArtifact(artifact);
		}
		collectPlugins(platformPlugins, scl.getEclipseRoot().resolve("plugins"));
		collectFeatures(platformFeatures, scl.getEclipseRoot().resolve("features"));
		collectPlugins(internalPlugins,
				scl.getNoarchDropletDir().resolve("foo/plugins"));
		collectFeatures(internalFeatures,
				scl.getNoarchDropletDir().resolve("foo/features"));
		collectPlugins(externalPlugins, scl.getBundleLocations().iterator()
				.next());

		EclipseInstallationResult result = installer
				.performInstallation(request);

		replay(visitor);
		if (visitArchful) {
			visitDroplets(buildRoot.resolve(Paths.get("/").relativize(
					scl.getArchDropletDir())));
		}
		visitDroplets(buildRoot.resolve(Paths.get("/").relativize(
				scl.getNoarchDropletDir())));
		visitResult(result);
		verify(visitor);
	}

	private Set<Plugin> collectPlugins(Map<String, List<Plugin>> map, Path dir)
			throws Exception {
		Files.createDirectories(dir);
		LinkedHashSet<Plugin> result = new LinkedHashSet<>();
		for (Entry<String, List<Plugin>> entry : map.entrySet()) {
			List<Plugin> plugins = entry.getValue();
			for (Plugin plugin : plugins) {
				Path path = dir.resolve(plugin.getId() + "_"
						+ plugin.getVersion() + ".jar");
				plugin.writeBundle(path);
				result.add(plugin);
			}
		}
		return result;
	}

	private Set<Feature> collectFeatures(Map<String, List<Feature>> map, Path dir)
			throws Exception {
		LinkedHashSet<Feature> result = new LinkedHashSet<>();
		for (Entry<String, List<Feature>> entry : map.entrySet()) {
			List<Feature> features = entry.getValue();
			for (Feature feature : features) {
				Path path = dir.resolve(feature.getId() + "_"
						+ feature.getVersion());
				feature.write(path);
				result.add(feature);
			}
		}
		return result;
	}

	private void visitDroplets(Path droplets) throws Exception {
		assertTrue(Files.isDirectory(droplets, LinkOption.NOFOLLOW_LINKS));

		for (Path dropletPath : Files.newDirectoryStream(droplets)) {
			assertTrue(Files.isDirectory(dropletPath, LinkOption.NOFOLLOW_LINKS));
			String droplet = dropletPath.getFileName().toString();

			if (Files.exists(Paths.get(dropletPath.toString(), "plugins"))) {
				assertTrue(Files.exists(Paths.get(dropletPath.toString(), "fragment.info")));
			}
			for (Path categoryPath : Files.newDirectoryStream(dropletPath)) {
				if (categoryPath.getFileName().toString().equals("fragment.info")) {
					continue;
				}
				assertTrue(Files.isDirectory(categoryPath, LinkOption.NOFOLLOW_LINKS));
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
					int uscore = name.lastIndexOf("_");
					String id = name.substring(0, uscore);
					String ver = name.substring(uscore + 1).replaceAll("\\.jar$", "");
					if (isLink)
						visitor.visitSymlink(droplet, id);
					else if (isPlugin)
						visitor.visitPlugin(droplet, id, ver);
					else if (isFeature)
						visitor.visitFeature(droplet, id, ver);
					else
						fail();
				}
			}
		}
	}

	private void visitResult(EclipseInstallationResult result) {
		assertNotNull(result);
		assertFalse(result.getDropins().isEmpty());
		for (Dropin droplet : result.getDropins()) {
			assertNotNull(droplet);
			assertFalse(droplet.getOsgiProvides().isEmpty());
			Set<String> requires = new LinkedHashSet<>();
			for (EclipseArtifact provide : droplet.getOsgiProvides()) {
				visitor.visitProvides(droplet.getId(), provide.getId(),
						provide.getVersion());
				String reqStr = provide.getProperties().get("osgi.requires");
				if (reqStr == null)
					continue;
				for (String req : reqStr.split(","))
					requires.add(req);
			}
			for (String req : requires)
				visitor.visitRequires(droplet.getId(), req);
		}
	}

	public IExpectationSetters<Object> expectPlugin(String plugin) {
		return expectPlugin("main", plugin, "1.0.0");
	}

	public IExpectationSetters<Object> expectPlugin(String droplet, String plugin) {
		return expectPlugin(droplet, plugin, "1.0.0");
	}

	public IExpectationSetters<Object> expectPlugin(String droplet, String plugin, String version) {
		visitor.visitPlugin(droplet, plugin, version);
		return expectLastCall();
	}

	public IExpectationSetters<Object> expectFeature(String feature) {
		return expectFeature("main", feature, "1.0.0");
	}

	public IExpectationSetters<Object> expectFeature(String droplet, String feature) {
		return expectFeature(droplet, feature, "1.0.0");
	}

	public IExpectationSetters<Object> expectFeature(String droplet, String feature, String version) {
		visitor.visitFeature(droplet, feature, version);
		return expectLastCall();
	}

	public IExpectationSetters<Object> expectSymlink(String plugin) {
		return expectSymlink("main", plugin);
	}

	public IExpectationSetters<Object> expectSymlink(String droplet, String plugin) {
		visitor.visitSymlink(droplet, plugin);
		return expectLastCall();
	}

	public IExpectationSetters<Object> expectRequires(String req) {
		return expectRequires("main", req);
	}

	public IExpectationSetters<Object> expectRequires(String droplet, String req) {
		visitor.visitRequires(droplet, req);
		return expectLastCall();
	}

	public IExpectationSetters<Object> expectProvides(String prov) {
		return expectProvides("main", prov, "1.0.0");
	}

	public IExpectationSetters<Object> expectProvides(String droplet, String prov) {
		return expectProvides(droplet, prov, "1.0.0");
	}

	public IExpectationSetters<Object> expectProvides(String droplet, String prov, String version) {
		visitor.visitProvides(droplet, prov, version);
		return expectLastCall();
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
		Path dir = buildRoot.resolve(Paths.get("/")
				.relativize(scl.getNoarchDropletDir())
				.resolve("main/plugins/foo_1.0.0"));
		assertTrue(Files.isDirectory(dir, LinkOption.NOFOLLOW_LINKS));
	}

	// Two plugins manually assigned to subpackages, third implicitly installed
	// to main pkg
	@Test
	public void subpackageSplitTest() throws Exception {
		addReactorPlugin("foo").assignToTargetPackage("sub1");
		addReactorPlugin("bar").assignToTargetPackage("sub2");
		addReactorPlugin("baz");
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
		addReactorPlugin("A").requireBundle("B").assignToTargetPackage("sub");
		addReactorPlugin("B");
		addReactorPlugin("C");
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
		addReactorPlugin("A").requireBundle("B1").requireBundle("B2").assignToTargetPackage("sub");
		addReactorPlugin("B1");
		addReactorPlugin("B2").assignToTargetPackage("different");
		addReactorPlugin("B3");
		addReactorPlugin("C").requireBundle("B2").requireBundle("B1").assignToTargetPackage("sub");
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
		addReactorPlugin("A").requireBundle("B").assignToTargetPackage("sub1");
		addReactorPlugin("B");
		addReactorPlugin("C").requireBundle("B").assignToTargetPackage("sub2");
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
		addReactorPlugin("A").importPackage("junit.framework").assignToTargetPackage("pkg1");
		addReactorPlugin("B").requireBundle("org.junit").assignToTargetPackage("pkg2");
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
		addReactorPlugin("A").importPackage("junit.framework").assignToTargetPackage("pkg1");
		addReactorPlugin("B").requireBundle("org.junit").requireBundle("A").assignToTargetPackage("pkg2");
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

	@Test
	public void trickyExpandVirtualPackagesLogic() throws Exception {
		// mockito -> junit
		addExternalPlugin("org.junit").exportPackage("org.junit");
		addExternalPlugin("org.mockito")
		.exportPackage("org.mockito")
		.importPackage("org.junit");

		// mylyn-sdk -> junit
		addReactorPlugin("org.eclipse.mylyn.sdk")
		.exportPackage("org.eclipse.mylyn.test.util")
		.importPackage("org.junit")
		.assignToTargetPackage("mylyn-sdk");

		// mylyn-test -> mylyn-sdk
		// mylyn-test -> mockito
		// mylyn-test -> junit
		addReactorPlugin("org.eclipse.mylyn.test")
		.importPackage("org.eclipse.mylyn.test.util")
		.importPackage("org.junit")
		.importPackage("org.mockito")
		.assignToTargetPackage("mylyn-test");

		expectPlugin("mylyn-test", "org.eclipse.mylyn.test");
		expectProvides("mylyn-test", "org.eclipse.mylyn.test");
		expectPlugin("mylyn-sdk", "org.eclipse.mylyn.sdk");
		expectProvides("mylyn-sdk", "org.eclipse.mylyn.sdk");

		expectSymlink("mylyn-test", "org.junit");
		expectSymlink("mylyn-test", "org.mockito");
		expectRequires("mylyn-test", "org.eclipse.mylyn.sdk");
		expectRequires("mylyn-test", "org.junit");
		expectRequires("mylyn-test", "org.mockito");

		expectSymlink("mylyn-sdk", "org.junit");
		expectRequires("mylyn-sdk", "org.junit");

		/*
		 * expectSymlink("mylyn-sdk", "org.mockito");
		 * This line was would have been expected in the past.
		 * 
		 * [junit], [mockito], [mylyn-sdk], [mylyn-test]
		 * 
		 * mylyn-test, mylyn-sdk, and mockito depend on the junit
		 * virtual package, so it was merged into each of these.
		 * 
		 * [mockito, junit], [mylyn-sdk, junit], [mylyn-test, junit]
		 * 
		 * The thing to note is that [mylyn-sdk, junit] actually depends
		 * on [mockito, junit]. This is because the merging states
		 * (among other things) that [mockito, junit] shall inherit all
		 * the reverse dependencies of [junit]. In other words, if
		 * something depended on [junit] before (eg. [mylyn-sdk], it
		 * now must depend on [mockito, junit]. This is fine for
		 * mylyn-test since it needs mockito, but mylyn-sdk has
		 * basically grown an extraneous dependency.
		 * 
		 * Now, the virtual package containing mockito and junit
		 * get merged into mylyn-test and mylyn-sdk, causing this
		 * bug, because mylyn-sdk doesn't need mockito.
		 * 
		 * [mylyn-sdk, junit, mockito], [mylyn-test, junit, mockito]
		 * 
		 */

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

	// Multiple units satisfy one dependency.
	// highest versioned unit, with least provides should be symlinked
	@Test
	public void multipleProvidersTest() throws Exception {
		addExternalPlugin("lib1", "5.0.0").exportPackage("foo.bar").exportPackage("baz").exportPackage("biz");
		addExternalPlugin("lib2", "5.0.0").exportPackage("foo.bar").exportPackage("baz");
		addExternalPlugin("lib3", "3.0.0").exportPackage("foo.bar");
		addReactorPlugin("A").importPackage("foo.bar");
		expectPlugin("A");
		expectSymlink("lib2");
		expectRequires("lib2");
		expectProvides("A");
		performTest();
	}

	private void addVersionedPlugins() {
		addExternalPlugin("P2", "2").exportPackage("foo.bar;version=2");
		addExternalPlugin("P3", "3").exportPackage("foo.bar;version=3");
		addExternalPlugin("P4", "4").exportPackage("foo.bar;version=4");
		addExternalPlugin("P5", "5").exportPackage("foo.bar;version=5");
	}

	// Versioned requirement must be satisfied only by bundles with matching
	// versions.
	@Test
	public void versionedRequirementTest() throws Exception {
		addVersionedPlugins();
		addReactorPlugin("A").importPackage("foo.bar;version=4.0.0");
		expectPlugin("A");
		expectSymlink("P5");
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
		expectSymlink("P4");
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
		expectSymlink("P5");
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

	// Symlinks for optional dependencies that are specified as "ignored" should
	// not be created.
	@Test
	public void optionalDependencyIgnoredTest() throws Exception {
		ignoreOptionalDeps("B");
		addExternalPlugin("X");
		addExternalPlugin("Y");
		addReactorPlugin("A").requireBundle("X;optional=true");
		addReactorPlugin("B").requireBundle("Y;optional=true");
		expectPlugin("A");
		expectPlugin("B");
		expectSymlink("X");
		expectRequires("X");
		expectProvides("A");
		expectProvides("B");
		performTest();
	}

	// As above but for optional dependencies of external units
	@Test
	public void optionalDependencyIgnoredExternalTest() throws Exception {
		ignoreOptionalDeps("X");
		addExternalPlugin("X").requireBundle("Y;optional=true");
		addExternalPlugin("Y");
		addReactorPlugin("A").requireBundle("X");
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

	// Same as above, but with split packages
	@Test
	public void sameBundleSymbolicNamesSubpackageSplitTest() throws Exception {
		addReactorPlugin("A", "1.0.0");
		addReactorPlugin("A", "2.0.0").assignToTargetPackage("pkg1");
		addReactorPlugin("B", "2.0.0").assignToTargetPackage("pkg1");
		addReactorPlugin("C", "2.0.0");
		expectPlugin("main", "A", "1.0.0");
		expectPlugin("pkg1", "A", "2.0.0");
		expectPlugin("pkg1", "B", "2.0.0");
		expectPlugin("main", "C", "2.0.0");
		expectProvides("main", "A", "1.0.0");
		expectProvides("pkg1", "A", "2.0.0");
		expectProvides("pkg1", "B", "2.0.0");
		expectProvides("main", "C", "2.0.0");
		performTest();
	}

	// Test we can install bundles whose names contain underscores
	@Test
	public void sneakyBundleNameUnderscoresTest() throws Exception {
		addReactorPlugin("A");
		addReactorPlugin("B_B").assignToTargetPackage("pkg1");
		expectPlugin("main", "A");
		expectPlugin("pkg1", "B_B");
		expectProvides("main", "A");
		expectProvides("pkg1", "B_B");
		performTest();
	}

	// Test feature installation
	@Test
	public void featureSimpleTest() throws Exception {
		addReactorFeature("feat");
		expectFeature("feat");
		expectProvides("feat");
		performTest();
	}

	// Test feature installation to subpagkage
	@Test
	public void featureSplitTest() throws Exception {
		addReactorFeature("A");
		addReactorFeature("B").assignToTargetPackage("subpkg");
		expectFeature("A");
		expectFeature("subpkg", "B");
		expectProvides("A");
		expectProvides("subpkg", "B");
		performTest();
	}

	// Test duplicate feature symbolic name
	@Test
	public void duplicateFeatureTest() throws Exception {
		addReactorFeature("A", "1.0.0");
		addReactorFeature("A", "2.0.0");
		expectFeature("main", "A", "1.0.0");
		expectFeature("main", "A", "2.0.0");
		expectProvides("main", "A", "1.0.0");
		expectProvides("main", "A", "2.0.0");
		performTest();
	}

	// Make sure that installation of plugins and features with identical
	// symbolic names is supported.
	@Test
	public void duplicateFeatureAndPluginTest() throws Exception {
		addReactorPlugin("A", "1.0.0");
		addReactorPlugin("A", "2.0.0");
		addReactorFeature("A", "1.0.0");
		addReactorFeature("A", "2.0.0");
		expectPlugin("main", "A", "1.0.0");
		expectPlugin("main", "A", "2.0.0");
		expectFeature("main", "A", "1.0.0");
		expectFeature("main", "A", "2.0.0");
		expectProvides("main", "A", "1.0.0").times(2);
		expectProvides("main", "A", "2.0.0").times(2);
		performTest();
	}

	// Test installing of virtual bundles provided by p2.inf
	@Test
	public void p2infProvideTest() throws Exception {
		Feature f = addReactorFeature("A");
		f.addP2Inf("units.1.id", "org.maven.ide.eclipse");
		f.addP2Inf("units.1.version", "$version$");
		f.addP2Inf("units.1.singleton", "true");
		f.addP2Inf("units.1.provides.0.namespace", "osgi.bundle");
		f.addP2Inf("units.1.provides.0.name", "org.maven.ide.eclipse");
		f.addP2Inf("units.1.provides.0.version", "$version$");
		expectFeature("A");
		expectProvides("A");
		performTest();
	}

	// Package ending in '-tests' should install to correct directory
	@Test
	public void testSubPackageTest() throws Exception {
		addReactorPlugin("A");
		addReactorPlugin("testA").assignToTargetPackage("pkg-tests");
		expectPlugin("main", "A");
		expectProvides("main", "A");
		expectProvides("pkg-tests", "testA");
		performTest();
		Path bundle = buildRoot.resolve(Paths.get("/")
				.relativize(scl.getTestBundleDir())
				.resolve("pkg-tests/plugins/testA_1.0.0.jar"));
		assertTrue(Files.exists(bundle, LinkOption.NOFOLLOW_LINKS));
	}

	// If at least one bundle in the droplet has a native component or uses
	// native code then the droplet should be installed into the "archful"
	// droplets location
	@Test
	public void archfulDropletInstallTest() throws Exception {
		addReactorPlugin("A");
		addReactorPlugin("B").hasNative();
		addReactorPlugin("A_A").assignToTargetPackage("noarch-subpkg");
		addReactorPlugin("B_B").assignToTargetPackage("noarch-subpkg");
		expectPlugin("A");
		expectPlugin("B");
		expectProvides("A");
		expectProvides("B");
		expectPlugin("noarch-subpkg", "A_A");
		expectPlugin("noarch-subpkg", "B_B");
		expectProvides("noarch-subpkg", "A_A");
		expectProvides("noarch-subpkg", "B_B");
		performTest();
		// check install locations
		Path noarchDroplet = buildRoot.resolve(Paths.get("/")
				.relativize(scl.getNoarchDropletDir()).resolve("noarch-subpkg"));
		Path archfulDroplet = buildRoot.resolve(Paths.get("/")
				.relativize(scl.getArchDropletDir()).resolve("main"));
		assertTrue(Files.exists(noarchDroplet, LinkOption.NOFOLLOW_LINKS));
		assertTrue(Files.exists(archfulDroplet, LinkOption.NOFOLLOW_LINKS));
	}

	// If a feature's external plug-ins have a cyclic dependency, we should be
	// able to deal with that
	@Test
	public void cyclicDepsInFeatureExternalPlugins() throws Exception {
		addExternalPlugin("slf4j.api", "1.7.12").importPackage("org.slf4j.impl").exportPackage("org.slf4j");
		addExternalPlugin("slf4j.simple", "1.7.12").importPackage("org.slf4j").exportPackage("org.slf4j.impl");
		addReactorFeature("org.eclipse.foo").addPlugin("slf4j.api", "1.7.12").addPlugin("slf4j.simple", "1.7.12");
		expectFeature("org.eclipse.foo");
		expectProvides("org.eclipse.foo");
		expectSymlink("slf4j.api");
		expectSymlink("slf4j.simple");
		performTest();
	}

	// What if a plug-in depends on a different version of an external bundle
	// that already exists in the platform?
	@Test
	public void externalDepOrPlatformDep() throws Exception {
		addPlatformPlugin("org.lucene", "5.0.0");
		addExternalPlugin("org.lucene", "3.0.0");
		addReactorPlugin("com.example.old").requireBundle("org.lucene;bundle-version=\"[3.0.0,4.0.0)\"");
		addReactorPlugin("com.example.new").requireBundle("org.lucene;bundle-version=\"[5.0.0,6.0.0)\"");
		expectPlugin("com.example.old");
		expectPlugin("com.example.new");
		expectProvides("com.example.old");
		expectProvides("com.example.new");
		expectSymlink("org.lucene");
		expectRequires("org.lucene");
		performTest();
		Path plugins = buildRoot.resolve(Paths.get("/")
				.relativize(scl.getNoarchDropletDir()).resolve("main/plugins"));
		assertTrue(Files.exists(plugins.resolve("org.lucene_3.0.0.jar"), LinkOption.NOFOLLOW_LINKS));
		assertFalse(Files.exists(plugins.resolve("org.lucene_5.0.0.jar"), LinkOption.NOFOLLOW_LINKS));
	}

	@Test
	// For now, cycles in reactor content shouldn't fail
	public void cyclicDepsInReactorPlugins() throws Exception {
		addReactorPlugin("a").requireBundle("b").assignToTargetPackage("A");
		addReactorPlugin("b").requireBundle("a").assignToTargetPackage("B");
		expectPlugin("A", "a");
		expectProvides("A", "a");
		expectPlugin("B", "b");
		expectProvides("B", "b");
		expectRequires("A", "b");
		expectRequires("B", "a");
		performTest();
	}
}
