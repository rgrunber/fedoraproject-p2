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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.eclipse.equinox.p2.metadata.IInstallableUnit;
import org.junit.Test;

import org.fedoraproject.p2.CompoundBundleRepository;
import org.fedoraproject.p2.IFedoraBundleRepository;

public class CompoundBundleRepositoryTest extends RepositoryTest {

	private void addPlugin(String path, String scl, String id, String ver)
			throws Exception {
		Plugin plugin = new Plugin(id, ver);
		Path dir = getTempDir().resolve(scl).resolve(path);
		Files.createDirectories(dir);
		String baseName = id + "_" + ver + ".jar";
		plugin.writeBundle(dir.resolve(baseName));
	}

	private void addPlatformPlugin(String scl, String id, String ver)
			throws Exception {
		// Plugin discovery code should be free to ignore plugins with
		// non-matching architecture, so we add the plugin to both lib and
		// lib64 to make sure it is discovered on any arch.
		addPlugin("usr/lib/eclipse/plugins", scl, id, ver);
		addPlugin("usr/lib64/eclipse/plugins", scl, id, ver);
	}

	private void addPlatformPlugin(String scl, String id) throws Exception {
		addPlatformPlugin(scl, id, "1.0.0");
	}

	private void addInternalPlugin(String scl, String id, String ver)
			throws Exception {
		addPlugin("usr/share/eclipse/dropins/dropin-name/eclipse/plugins", scl,
				id, ver);
	}

	private void addInternalPlugin(String scl, String id) throws Exception {
		addInternalPlugin(scl, id, "1.0.0");
	}

	private void addExternalPlugin(String scl, String id, String ver)
			throws Exception {
		addPlugin("usr/share/java/sub-directory", scl, id, ver);
	}

	private void addExternalPlugin(String scl, String id) throws Exception {
		addExternalPlugin(scl, id, "1.0.0");
	}

	@Test
	public void emptyRepoTest() throws Exception {
		IFedoraBundleRepository repo = new CompoundBundleRepository(
				Collections.singletonList(getTempDir()));
		assertTrue(repo.getPlatformUnits().isEmpty());
		assertTrue(repo.getInternalUnits().isEmpty());
		assertTrue(repo.getExternalUnits().isEmpty());
	}

	private IFedoraBundleRepository createRepo(String... scls) {
		List<Path> prefixes = new ArrayList<>(scls.length);
		for (String scl : scls) {
			prefixes.add(getTempDir().resolve(scl));
		}
		return new CompoundBundleRepository(prefixes);
	}

	@Test
	public void singlePrefixTest() throws Exception {
		addPlatformPlugin("foo", "bar");
		addInternalPlugin("foo", "baz");
		addExternalPlugin("foo", "xyzzy");
		IFedoraBundleRepository repo = createRepo("foo");

		Iterator<IInstallableUnit> platIt = repo.getPlatformUnits().iterator();
		assertTrue(platIt.hasNext());
		assertEquals("bar", platIt.next().getId());
		assertFalse(platIt.hasNext());

		Iterator<IInstallableUnit> intIt = repo.getInternalUnits().iterator();
		assertTrue(intIt.hasNext());
		assertEquals("baz", intIt.next().getId());
		assertFalse(intIt.hasNext());

		Iterator<IInstallableUnit> extIt = repo.getExternalUnits().iterator();
		assertTrue(extIt.hasNext());
		assertEquals("xyzzy", extIt.next().getId());
		assertFalse(extIt.hasNext());
	}
}
