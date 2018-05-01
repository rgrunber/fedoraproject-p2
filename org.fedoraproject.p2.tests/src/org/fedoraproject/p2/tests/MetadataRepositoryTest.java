/*******************************************************************************
 * Copyright (c) 2014, 2018 Red Hat Inc.
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
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.net.URI;
import java.util.LinkedHashSet;
import java.util.Set;

import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.equinox.p2.metadata.IArtifactKey;
import org.eclipse.equinox.p2.metadata.IInstallableUnit;
import org.eclipse.equinox.p2.metadata.IProvidedCapability;
import org.eclipse.equinox.p2.query.IQueryResult;
import org.eclipse.equinox.p2.query.QueryUtil;
import org.eclipse.equinox.p2.repository.metadata.IMetadataRepository;
import org.fedoraproject.p2.FedoraMetadataRepository;
import org.fedoraproject.p2.P2Utils;
import org.junit.Test;

public class MetadataRepositoryTest extends RepositoryTest {

	@Test
	public void ownershipTest () {
		try {
			IMetadataRepository repo = getMetadataRepoManager().loadRepository(new URI(JAVADIR), new NullProgressMonitor());
			assertEquals(FedoraMetadataRepository.class.getName() + " must own the proper namespace", FedoraMetadataRepository.class.getName(), repo.getType());
		} catch (Exception e) {
			e.printStackTrace();
			fail ();
		}
	}

	@Test
	public void nonEmptyRepositoryTest() {
		try {
			IMetadataRepository repo = getMetadataRepoManager().loadRepository(new URI(JAVADIR), new NullProgressMonitor());
			IQueryResult<IInstallableUnit> res = repo.query(QueryUtil.createIUAnyQuery(), new NullProgressMonitor());
			Set<IInstallableUnit> units = res.toUnmodifiableSet();
			assertTrue("Metadata Repository must not be empty", units.size() > 0);
		} catch (Exception e) {
			e.printStackTrace();
			fail();
		}
	}

	@Test
	public void existsBundleInRepository() {
		boolean pass = false;
		try {
			IMetadataRepository repo = getMetadataRepoManager().loadRepository(new URI(JAVADIR), new NullProgressMonitor());
			IQueryResult<IInstallableUnit> res = repo.query(QueryUtil.createIUAnyQuery(), new NullProgressMonitor());
			Set<IInstallableUnit> units = res.toUnmodifiableSet();
			assertTrue("Metadata Repository must not be empty", units.size() > 0);
			for (IInstallableUnit u : units) {
				for (IArtifactKey k : u.getArtifacts()) {
					if (k.getClassifier().equals("osgi.bundle")) {
						pass = true;
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
			fail();
		}
		if (!pass) {
			fail ("Metadata Repository must contain an IU referencing an artifact of type osgi.bundle.");
		}
	}

	@Test
	public void existsFeatureInRepository () {
		boolean pass = false;
		try {
			IMetadataRepository repo = getMetadataRepoManager().loadRepository(new URI(ECLIPSE_DIR), new NullProgressMonitor());
			IQueryResult<IInstallableUnit> res = repo.query(QueryUtil.createIUAnyQuery(), new NullProgressMonitor());
			Set<IInstallableUnit> units = res.toUnmodifiableSet();
			assertTrue("Metadata Repository must not be empty", units.size() > 0);
			for (IInstallableUnit u : units) {
				for (IArtifactKey k : u.getArtifacts()) {
					if (k.getClassifier().equals("org.eclipse.update.feature")) {
						pass = true;
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
			fail();
		}
		if (!pass) {
			fail ("Metadata Repository must contain an IU referencing an artifact of type org.eclipse.update.feature.");
		}
	}

	@Test
	public void emptyRepositoryTest () {
		try {
			IMetadataRepository repo = getMetadataRepoManager().loadRepository(new URI("fedora:" + getTempDir()), new NullProgressMonitor());
			IQueryResult<IInstallableUnit> res = repo.query(QueryUtil.createIUAnyQuery(), new NullProgressMonitor());
			Set<IInstallableUnit> units = res.toUnmodifiableSet();
			assertEquals("Metadata Repository must be empty", 0, units.size());
		} catch (Exception e) {
			e.printStackTrace();
			fail();
		}
	}

	@Test
	public void bundleShapeDirRepositoryTest () {
		boolean hasBundleShapeDir = false;
		try {
			IMetadataRepository repo = getMetadataRepoManager().loadRepository(new URI(ECLIPSE_DIR), new NullProgressMonitor());
			IQueryResult<IInstallableUnit> res = repo.query(QueryUtil.createIUAnyQuery(), new NullProgressMonitor());
			Set<IInstallableUnit> units = res.toUnmodifiableSet();
			for (IInstallableUnit u : units) {
				for (IProvidedCapability cap : u.getProvidedCapabilities()) {
					// We want to find OSGi bundles
					if (cap.getNamespace().equals("org.eclipse.equinox.p2.eclipse.type")
							&& cap.getName().equals("bundle")) {
						if (P2Utils.isBundleShapeDir(u)) {
							hasBundleShapeDir = true;
						}
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
			fail();
		}
		assertTrue("There are no IUs corresponding to artifacts with an Eclipse-BundleShape: dir .", hasBundleShapeDir);
	}

	@Test
	public void cachingTest () {
		try {
			IMetadataRepository orig = getMetadataRepoManager().loadRepository(new URI(JAVADIR), new NullProgressMonitor());
			IMetadataRepository cached = getMetadataRepoManager().loadRepository(new URI(JAVADIR), new NullProgressMonitor());
			assertTrue("Caching of previously accessed repositories failed.", orig.equals(cached));
		} catch (Exception e) {
			e.printStackTrace();
			fail();
		}
	}

	@Test
	public void featureJarAndGroupTest () {
	    try {
            IMetadataRepository repo = getMetadataRepoManager().loadRepository(new URI(JAVADIR), new NullProgressMonitor());
            IQueryResult<IInstallableUnit> res = repo.query(QueryUtil.createIUAnyQuery(), new NullProgressMonitor());
            Set<IInstallableUnit> units = res.toUnmodifiableSet();
            Set<String> unitIds = new LinkedHashSet<>();

            // Gather all feature jars and feature groups
            for (IInstallableUnit u : units) {
                if (u.getId().endsWith(".feature.jar") || u.getId().endsWith(".feature.group")) {
                    unitIds.add(u.getId());
                }
            }

            // Every feature must contain a feature.jar and a feature.group unit
            String otherId;
            for (String id : unitIds) {
                if (id.endsWith(".feature.jar")) {
                    otherId = id.replace(".jar", ".group");
                    assertTrue(unitIds.contains(otherId));
                } else if (id.endsWith(".feature.group")) {
                    otherId = id.replace(".group", ".jar");
                    assertTrue(unitIds.contains(otherId));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            fail();
        }
	}

}
