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
import java.util.Set;

import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.equinox.internal.p2.director.app.DirectorApplication;
import org.eclipse.equinox.p2.metadata.IInstallableUnit;
import org.eclipse.equinox.p2.query.IQueryResult;
import org.eclipse.equinox.p2.query.QueryUtil;
import org.eclipse.equinox.p2.repository.metadata.IMetadataRepository;
import org.junit.BeforeClass;
import org.junit.Test;

public class InstallTest extends RepositoryTest {

	@BeforeClass
	public static void beforeClass() throws Exception {
		RepositoryTest.beforeClass();
	}

	@Test
	public void simpleSingleUnitInstallTest() {
		try {
			IInstallableUnit targetIU = null;
			IMetadataRepository repo = getMetadataRepoManager().loadRepository(new URI(JAVADIR), new NullProgressMonitor());
			IQueryResult<IInstallableUnit> res = repo.query(QueryUtil.createIUAnyQuery(), new NullProgressMonitor());
			Set<IInstallableUnit> units = res.toUnmodifiableSet();
			for (IInstallableUnit u : units) {
				if (u.getRequirements().size() == 0) {
					targetIU = u;
					break;
				}
			}

			// TODO: Fix test to create the profile and use the proper InstallOperation API
			if (targetIU != null) {
				String args [] = new String [] {"-repository", JAVADIR,
						"-installIU", targetIU.getId(),
						"-destination", ResourcesPlugin.getWorkspace().getRoot().getLocation().toOSString()
						+ File.separator + getClass().getName()};
				DirectorApplication app = new DirectorApplication();
				app.run(args);

				// See org.eclipse.equinox.internal.p2.artifact.repository.simple.Mapper
				String fileName = targetIU.getId() + "_" + targetIU.getVersion() + ".jar";
				assertTrue(new File(ResourcesPlugin.getWorkspace().getRoot().getLocation().toOSString()
						+ File.separator + getClass().getName() + File.separator + "plugins" + File.separator + fileName).exists());

			}
		} catch (Exception e) {
			e.printStackTrace();
			fail();
		}
	}

}
