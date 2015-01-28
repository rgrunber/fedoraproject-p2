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

import java.io.File;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.equinox.internal.p2.director.app.DirectorApplication;
import org.eclipse.equinox.p2.metadata.IInstallableUnit;
import org.eclipse.equinox.p2.query.IQueryResult;
import org.eclipse.equinox.p2.query.QueryUtil;
import org.eclipse.equinox.p2.repository.metadata.IMetadataRepository;
import org.junit.Test;

import org.fedoraproject.p2.P2Utils;

public class InstallTest extends RepositoryTest {

	private final String profileID = "testProfile";
	private final String installLoc = ResourcesPlugin.getWorkspace().getRoot().getLocation().toOSString()
			+ File.separator + getClass().getSimpleName();

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

			if (targetIU != null) {
				installUnit(targetIU, JAVADIR);
				checkUnitInstallation(targetIU);
			}
		} catch (Exception e) {
			e.printStackTrace();
			fail();
		}
	}

	@Test
	public void bundleShapeDirUnitInstallTest () {
		try {
			IInstallableUnit targetIU = null;
			IMetadataRepository repo = getMetadataRepoManager().loadRepository(new URI(ECLIPSE_DIR), new NullProgressMonitor());
			IQueryResult<IInstallableUnit> res = repo.query(QueryUtil.createIUAnyQuery(), new NullProgressMonitor());
			Set<IInstallableUnit> units = res.toUnmodifiableSet();
			for (IInstallableUnit u : units) {
				if (isBundleShapeDir(u)) {
					targetIU = u;
					break;
				}
			}

			if (targetIU != null) {
				installUnit(targetIU, ECLIPSE_DIR);
				checkUnitInstallation(targetIU);
			}
		} catch (Exception e) {
			e.printStackTrace();
			fail();
		}
	}

	private void installUnit (IInstallableUnit targetIU, String repository) {
		String args [] = new String [] {"-repository", repository,
				"-installIU", targetIU.getId(),
				"-profile", profileID,
				"-destination", installLoc};
		DirectorApplication app = new DirectorApplication();
		app.run(args);
	}

	private void checkUnitInstallation (IInstallableUnit targetIU) {
		try {
			// See org.eclipse.equinox.internal.p2.artifact.repository.simple.Mapper
			String fileName = targetIU.getId() + "_" + targetIU.getVersion();
			if (! isBundleShapeDir(targetIU)) {
				fileName += ".jar";
			}

			File targetLoc = new File(installLoc + File.separator + "plugins" + File.separator + fileName);
			assertTrue(targetLoc.exists());
			File sysLoc = P2Utils.getPath(targetIU).toFile();
			assertEquals("Possible corruption : " + sysLoc.getAbsolutePath() + " and " + targetLoc.getAbsolutePath()
			        + " do not appear to match in size.", sysLoc.length(), targetLoc.length());

			File[] profileFiles = getProfileFiles(new File(installLoc));

			// Get the latest '.profile.gz' file
			File latestProfileFile = null;
			long timestamp = 0;
			for (File file : profileFiles) {
				long ts = Long.parseLong(file.getName().replace(".profile.gz",
						""));
				if (ts > timestamp) {
					timestamp = ts;
					latestProfileFile = file;
				}
			}

			// A profile with ID of ${profileID} must be contained in a folder ${profileID}.profile
			String profileDir = File.separator + "tmp" + File.separator + profileID + ".profile";
			new File(profileDir).mkdir();
			String profileFile = profileDir + File.separator + latestProfileFile.getName();
			Files.copy(Paths.get(latestProfileFile.getAbsolutePath()), Paths.get(profileFile), StandardCopyOption.REPLACE_EXISTING);

			IMetadataRepository profile = getMetadataRepoManager().loadRepository(new URI("file:" + profileFile), new NullProgressMonitor());
			IQueryResult<IInstallableUnit> profRes = profile.query(QueryUtil.createIUAnyQuery(), new NullProgressMonitor());
			Set<IInstallableUnit> profUnits = profRes.toUnmodifiableSet();

			assertTrue(profUnits.contains(targetIU));
		} catch (Exception e) {
			e.printStackTrace();
			fail();
		}
	}

	private File[] getProfileFiles(File root) {
		List<File> res = new ArrayList<>();
		for (File child : root.listFiles()) {
			File [] tmp = null;
			if (child.isDirectory() && child.canRead()) {
				tmp = getProfileFiles(child);
			} else if (child.isFile() && child.getName().endsWith(".profile.gz")) {
				tmp = new File [] {child};
			}
			if (tmp != null) {
				res.addAll(Arrays.asList(tmp));
			}
		}
		return res.toArray(new File[0]);
	}

}
