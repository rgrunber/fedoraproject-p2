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

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.equinox.p2.internal.repository.tools.MirrorApplication;
import org.eclipse.equinox.p2.metadata.IInstallableUnit;
import org.eclipse.equinox.p2.query.IQueryResult;
import org.eclipse.equinox.p2.query.QueryUtil;
import org.eclipse.equinox.p2.repository.metadata.IMetadataRepository;
import org.junit.BeforeClass;
import org.junit.Test;

public class MirrorTest extends RepositoryTest {

	// Mirroring is time consuming so let's not do too many
	private final int LIMIT = 10;

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		RepositoryTest.beforeClass();
	}

	@Test
	public void simpleMirrorTest() {
		try {
			List<IInstallableUnit> roots = new ArrayList<IInstallableUnit>();
			IMetadataRepository sourceRepo = getMetadataRepoManager().loadRepository(new URI(JAVADIR), new NullProgressMonitor());
			IQueryResult<IInstallableUnit> res = sourceRepo.query(QueryUtil.createIUAnyQuery(), new NullProgressMonitor());
			Set<IInstallableUnit> units = res.toUnmodifiableSet();
			for (IInstallableUnit u : units) {
				if (u.getRequirements().size() == 0) {
					roots.add(u);
				}
				if (roots.size() == LIMIT) {
					break;
				}
			}

			StringBuffer rootStr = new StringBuffer();
			for (int i = 0; i < roots.size(); i++) {
				String id = roots.get(i).getId();
				String version = roots.get(i).getVersion().toString();
				if (i == 0) {
					rootStr.append(id + "/[" + version + "," + version + "]");
				} else {
					rootStr.append("," + id + "/[" + version + "," + version + "]");
				}
			}

			String args[] = new String[] {"-source", JAVADIR, "-destination",
					ResourcesPlugin.getWorkspace().getRoot().getLocation().toOSString()
					+ File.separator + getClass().getName(),
					"-roots", rootStr.toString()};
			MirrorApplication app = new MirrorApplication();
			app.initializeFromArguments(args);
			app.run(new NullProgressMonitor());

			IMetadataRepository destRepo = getMetadataRepoManager().loadRepository(new URI("file:"
			+ ResourcesPlugin.getWorkspace().getRoot().getLocation().toOSString() 
			+ File.separator + getClass().getName()), new NullProgressMonitor());
			IQueryResult<IInstallableUnit> destRes = destRepo.query(QueryUtil.createIUAnyQuery(), new NullProgressMonitor());
			Set<IInstallableUnit> destUnits = destRes.toUnmodifiableSet();

			// We should only have installed the roots since they have no requirements
			assertTrue(roots.containsAll(destUnits));
			assertTrue(destUnits.containsAll(roots));

		} catch (Exception e) {
			e.printStackTrace();
			fail();
		}
	}

}
