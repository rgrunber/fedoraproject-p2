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

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashSet;
import java.util.Set;

import org.eclipse.equinox.p2.metadata.IInstallableUnit;

import org.fedoraproject.p2.EclipseSystemLayout;
import org.fedoraproject.p2.FedoraBundleRepository;
import org.fedoraproject.p2.P2Utils;
import org.fedoraproject.p2.SCL;

import org.junit.Test;

public class FedoraBundleRepositoryTest extends RepositoryTest {

	static final String EMPTY = "/tmp/";

	@Test
	public void definitionTest () {
		SCL scl = new SCL(Paths.get("/etc/java/eclipse.conf"));
		FedoraBundleRepository rep = new FedoraBundleRepository(scl);
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
	public void emptyRepositoryTest () throws Exception {
		Path conf = getTempDir().resolve("scl.conf");
		Files.createFile(conf);
		SCL scl = new SCL(conf);
		FedoraBundleRepository rep = new FedoraBundleRepository(scl);
		assertTrue(rep.getPlatformUnits().isEmpty());
		assertTrue(rep.getInternalUnits().isEmpty());
		assertTrue(rep.getExternalUnits().isEmpty());
	}

	@Test
	public void simpleLookupTest () {
		SCL scl = new SCL(Paths.get("/etc/java/eclipse.conf"));
		FedoraBundleRepository rep = new FedoraBundleRepository(scl);
		Set<IInstallableUnit> allUnits = new LinkedHashSet<> ();
		allUnits.addAll(rep.getPlatformUnits());
		allUnits.addAll(rep.getInternalUnits());
		allUnits.addAll(rep.getExternalUnits());

		for (IInstallableUnit u : allUnits) {
			Path path = P2Utils.getPath(u);
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
		Set<Path> res = EclipseSystemLayout.getUserDefinedBundleLocations();
		assertTrue(res.size() == 2);
	}

}
