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
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.net.URI;
import java.util.Set;

import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.equinox.p2.metadata.IArtifactKey;
import org.eclipse.equinox.p2.query.IQueryResult;
import org.eclipse.equinox.p2.repository.artifact.ArtifactKeyQuery;
import org.eclipse.equinox.p2.repository.artifact.IArtifactRepository;
import org.fedoraproject.p2.FedoraArtifactRepository;
import org.junit.Test;

public class ArtifactRepositoryTest extends RepositoryTest {

	@Test
	public void ownershipTest () {
		try {
			IArtifactRepository repo = getArtifactRepoManager().loadRepository(new URI (JAVADIR), new NullProgressMonitor());
			assertEquals(FedoraArtifactRepository.class.getName() + " must own the proper namespace", FedoraArtifactRepository.class.getName(), repo.getType());
			System.out.println(repo.getName() + ", " + repo.getDescription() + ", " + repo.getProvider());
		} catch (Exception e) {
			e.printStackTrace();
			fail ();
		}
	}

	@Test
	public void nonEmptyRepositoryTest() {
		try {
			IArtifactRepository repo = getArtifactRepoManager().loadRepository(new URI(JAVADIR), new NullProgressMonitor());
			IQueryResult<IArtifactKey> res = repo.query(ArtifactKeyQuery.ALL_KEYS, new NullProgressMonitor());
			Set<IArtifactKey> keys = res.toUnmodifiableSet();
			assertTrue("Artifact Repository must not be empty", keys.size() > 0);
			for (IArtifactKey k : keys) {
				System.out.println(k.getClassifier() + " " + k.getId() + " " + k.getVersion());
			}
		} catch (Exception e) {
			e.printStackTrace();
			fail();
		}
	}

	@Test
	public void existsBundleInRepository() {
		boolean pass = false;
		try {
			IArtifactRepository repo = getArtifactRepoManager().loadRepository(new URI(JAVADIR), new NullProgressMonitor());
			IQueryResult<IArtifactKey> res = repo.query(ArtifactKeyQuery.ALL_KEYS, new NullProgressMonitor());
			Set<IArtifactKey> keys = res.toUnmodifiableSet();
			assertTrue("Artifact Repository must not be empty", keys.size() > 0);
			for (IArtifactKey k : keys) {
				if (k.getClassifier().equals("osgi.bundle")) {
					pass = true;
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
			fail();
		}
		if (!pass) {
			fail ("Artifact Repository must contain an IU referencing an artifact of type osgi.bundle.");
		}
	}

	@Test
	public void existsFeatureInRepository () {
		boolean pass = false;
		try {
			IArtifactRepository repo = getArtifactRepoManager().loadRepository(new URI(JAVADIR), new NullProgressMonitor());
			IQueryResult<IArtifactKey> res = repo.query(ArtifactKeyQuery.ALL_KEYS, new NullProgressMonitor());
			Set<IArtifactKey> keys = res.toUnmodifiableSet();
			assertTrue("Artifact Repository must not be empty", keys.size() > 0);
			for (IArtifactKey k : keys) {
				if (k.getClassifier().equals("org.eclipse.update.feature")) {
					pass = true;
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
			fail();
		}
		if (!pass) {
			fail ("Artifact Repository must contain an IU referencing an artifact of type org.eclipse.update.feature.");
		}
	}

	@Test
	public void emptyRepositoryTest () {
		try {
			IArtifactRepository repo = getArtifactRepoManager().loadRepository(new URI(EMPTY), new NullProgressMonitor());
			IQueryResult<IArtifactKey> res = repo.query(ArtifactKeyQuery.ALL_KEYS, new NullProgressMonitor());
			Set<IArtifactKey> keys = res.toUnmodifiableSet();
			assertEquals("Artifact Repository must be empty", 0, keys.size());
		} catch (Exception e) {
			e.printStackTrace();
			fail();
		}
	}

	@Test
	public void cachingTest () {
		try {
			IArtifactRepository orig = getArtifactRepoManager().loadRepository(new URI(JAVADIR), new NullProgressMonitor());
			IArtifactRepository cached = getArtifactRepoManager().loadRepository(new URI(JAVADIR), new NullProgressMonitor());
			assertTrue("Caching of previously accessed repositories failed.", orig.equals(cached));
		} catch (Exception e) {
			e.printStackTrace();
			fail();
		}
	}

}
