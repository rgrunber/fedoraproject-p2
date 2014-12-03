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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.eclipse.equinox.p2.metadata.IInstallableUnit;
import org.junit.Before;
import org.junit.Test;

import org.fedoraproject.p2.CompoundBundleRepository;
import org.fedoraproject.p2.IFedoraBundleRepository;
import org.fedoraproject.p2.P2Utils;
import org.fedoraproject.p2.SCL;

interface RepositoryVisitor {
	void visitPlatformPlugin(String id, String ver);

	void visitInternalPlugin(String id, String ver);

	// For external plugins it does matter where they are located in the file
	// system because they are being symlinked.
	void visitExternalPlugin(String id, String ver, Path path);
}

public class CompoundBundleRepositoryTest extends RepositoryTest {

	private RepositoryVisitor visitor;

	@Before
	public void initMocks() {
		visitor = createMock(RepositoryVisitor.class);
	}

	private Path addPlugin(String path, String scl, String id, String ver)
			throws Exception {
		Plugin plugin = new Plugin(id, ver);
		Path dir = getTempDir().resolve(scl).resolve(path);
		Files.createDirectories(dir);
		String baseName = id + "_" + ver + ".jar";
		Path bundle = dir.resolve(baseName);
		plugin.writeBundle(bundle);
		return bundle;
	}

	private void addPlatformPlugin(String scl, String id, String ver,
			boolean expect) throws Exception {
		addPlugin("usr/lib/eclipse/plugins", scl, id, ver);
		if (expect) {
			visitor.visitPlatformPlugin(id, ver);
			expectLastCall();
		}
	}

	private void addPlatformPlugin(String scl, String id, boolean expect)
			throws Exception {
		addPlatformPlugin(scl, id, "1.0.0", expect);
	}

	private void addInternalPlugin(String scl, String id, String ver,
			boolean expect) throws Exception {
		addPlugin("usr/share/eclipse/dropins/dropin-name/eclipse/plugins", scl,
				id, ver);
		if (expect) {
			visitor.visitInternalPlugin(id, ver);
			expectLastCall();
		}
	}

	private void addInternalPlugin(String scl, String id, boolean expect)
			throws Exception {
		addInternalPlugin(scl, id, "1.0.0", expect);
	}

	private void addExternalPlugin(String scl, String id, String ver,
			boolean expect) throws Exception {
		Path path = addPlugin("usr/share/java/sub-directory", scl, id, ver);
		if (expect) {
			visitor.visitExternalPlugin(id, ver, path);
			expectLastCall();
		}
	}

	private void addExternalPlugin(String scl, String id, boolean expect)
			throws Exception {
		addExternalPlugin(scl, id, "1.0.0", expect);
	}

	@Test
	public void emptyRepoTest() throws Exception {
		Path conf = getTempDir().resolve("eclipse.conf");
		Files.createFile(conf);
		SCL scl = new SCL(conf);
		IFedoraBundleRepository repo = new CompoundBundleRepository(
				Collections.singletonList(scl));
		assertTrue(repo.getPlatformUnits().isEmpty());
		assertTrue(repo.getInternalUnits().isEmpty());
		assertTrue(repo.getExternalUnits().isEmpty());
	}

	private void performTest(String... sclNames) throws Exception {
		List<SCL> scls = new ArrayList<>(sclNames.length);
		for (String name : sclNames) {
			Path prefix = getTempDir().resolve(name);
			Files.createDirectories(prefix);

			Path confPath = prefix.resolve("eclipse.conf");
			writeSclConfig(confPath, name, prefix);
			scls.add(new SCL(confPath));
		}

		IFedoraBundleRepository repo = new CompoundBundleRepository(scls);
		replay(visitor);
		for (IInstallableUnit unit : repo.getPlatformUnits()) {
			visitor.visitPlatformPlugin(unit.getId(), unit.getVersion()
					.toString());
		}
		for (IInstallableUnit unit : repo.getInternalUnits()) {
			visitor.visitInternalPlugin(unit.getId(), unit.getVersion()
					.toString());
		}
		for (IInstallableUnit unit : repo.getExternalUnits()) {
			Path path = P2Utils.getPath(unit);
			assertNotNull(path);
			visitor.visitExternalPlugin(unit.getId(), unit.getVersion()
					.toString(), path);
		}
		verify(visitor);
	}

	@Test
	public void singlePrefixTest() throws Exception {
		addPlatformPlugin("foo", "bar", true);
		addInternalPlugin("foo", "baz", true);
		addExternalPlugin("foo", "xyzzy", true);
		performTest("foo");
	}

	@Test
	public void layeredRepoTest() throws Exception {
		for (String scl : Arrays.asList("base", "maven30", "thermostat1")) {
			String p = scl.substring(0, 1);
			addPlatformPlugin("base", p + "P", "1.0.0", true);
			addPlatformPlugin("base", p + "P", "2.0.0", true);
			addPlatformPlugin("base", p + "P3", true);
			addInternalPlugin("base", p + "I", "1.0.0", true);
			addInternalPlugin("base", p + "I", "2.0.0", true);
			addInternalPlugin("base", p + "I3", true);
			addExternalPlugin("base", p + "E", "1.0.0", true);
			addExternalPlugin("base", p + "E", "2.0.0", true);
			addExternalPlugin("base", p + "E3", true);
		}
		performTest("thermostat1", "empty-scl", "maven30", "base");
	}

	// External bundles with the same BSN and version should be shadowed
	@Test
	public void shadowingTest() throws Exception {
		addExternalPlugin("scl", "p", "1.2.3", true);
		// Plugin from base is not expected to be discovered - it is shadowed by
		// the same plugin from SCL
		addExternalPlugin("base", "p", "1.2.3", false);
		performTest("scl", "base");
	}
}
