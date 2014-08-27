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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.equinox.p2.metadata.IInstallableUnit;
import org.fedoraproject.p2.EclipseSystemLayout;
import org.fedoraproject.p2.FedoraBundleRepository;
import org.junit.Test;

public class FedoraBundleRepositoryTest {

	static final String EMPTY = "/tmp/";

	@Test
	public void definitionTest () {
		FedoraBundleRepository rep = new FedoraBundleRepository(new File("/"));
		Set<IInstallableUnit> platformUnits = rep.getPlatformUnits();
		Set<IInstallableUnit> internalUnits = rep.getInternalUnits();
		Set<IInstallableUnit> externalUnits = rep.getExternalUnits();

		assertTrue(!platformUnits.isEmpty());
		assertTrue(!internalUnits.isEmpty());
		assertTrue(!externalUnits.isEmpty());

		// No external units present in platform/internal sets
		for (IInstallableUnit u : externalUnits) {
			assertFalse(platformUnits.contains(u));
			assertFalse(internalUnits.contains(u));
		}
	}

	@Test
	public void emptyRepositoryTest () {
		FedoraBundleRepository rep = new FedoraBundleRepository(new File(EMPTY));
		assertTrue(rep.getPlatformUnits().isEmpty());
		assertTrue(rep.getInternalUnits().isEmpty());
		assertTrue(rep.getExternalUnits().isEmpty());
	}

	@Test
	public void simpleLookupTest () {
		FedoraBundleRepository rep = new FedoraBundleRepository(new File("/"));
		Set<IInstallableUnit> allUnits = new HashSet<IInstallableUnit> ();
		allUnits.addAll(rep.getPlatformUnits());
		allUnits.addAll(rep.getInternalUnits());
		allUnits.addAll(rep.getExternalUnits());

		for (IInstallableUnit u : allUnits) {
			Path path = rep.lookupBundle(u);
			if (path != null) {
			    assertTrue("Path for " + u.getId() + " " + u.getVersion() + " does not exist", path.toFile().exists());
			    System.out.println(u.getId() + " " + u.getVersion() + " " + path.toString());
			} else {
			    System.out.println("ERROR : Path for " + u.getId() + " " + u.getVersion() + " not found.");
			}
		}
	}

	@Test
	public void userDefinedLocationsTest () {
		System.setProperty("fedora.p2.repos", "/tmp/notexist/,/usr/share/java/,/usr/lib/");
		Set<String> res = EclipseSystemLayout.getUserDefinedBundleLocations();
		assertTrue(res.size() == 2);
	}

}
