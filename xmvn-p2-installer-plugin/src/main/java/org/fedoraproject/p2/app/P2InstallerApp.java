/*******************************************************************************
 * Copyright (c) 2014-2017 Red Hat Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.fedoraproject.p2.app;

import java.nio.file.Paths;
import java.util.Set;

import org.fedoraproject.p2.installer.Dropin;
import org.fedoraproject.p2.installer.EclipseArtifact;
import org.fedoraproject.p2.installer.EclipseInstallationRequest;
import org.fedoraproject.p2.installer.EclipseInstallationResult;
import org.fedoraproject.p2.installer.EclipseInstaller;
import org.fedoraproject.p2.installer.EclipseInstallerFactory;

/**
 * @author Mikolaj Izdebski
 */
public class P2InstallerApp {

	private int run(CliRequest cliRequest, EclipseInstaller installer) throws Exception {
		if (cliRequest.getParameters().isEmpty()) {
			System.err.println("No artifacts specified for installation."
					+ " There is nothing to do.");
			return 0;
		}

		EclipseInstallationRequest request = new EclipseInstallationRequest();
		request.setMainPackageId(cliRequest.getName());
		if (!cliRequest.isDryRun())
			request.setBuildRoot(Paths.get(cliRequest.getRoot()));
		for (String arg : cliRequest.getParameters())
			request.addArtifact(new EclipseArtifact(Paths.get(arg), false, false));
		if (!cliRequest.getMappings().isEmpty())
			throw new RuntimeException("FIXME: for now subpackage mapping is disabled in P2InstallerApp");

		EclipseInstallationResult result = installer
				.performInstallation(request);

		if (cliRequest.isPrintDeps())
			printDeps(result.getDropins());

		if (cliRequest.isStrict()
				&& result.getDropins().size() != cliRequest.getParameters()
						.size()) {
			System.err.println("Some artifact failed to install");
			return 1;
		}

		return 0;
	}

	private void printDeps(Set<Dropin> dropins) {
		for (Dropin dropin : dropins) {
			for (EclipseArtifact provide : dropin.getOsgiProvides()) {
				String idVer = provide.getId() + " " + provide.getVersion();
				String req = provide.getProperties().get("osgi.requires");
				System.out.println(req == null ? idVer : idVer + " " + req);
			}
		}
	}

	public static void main(String[] args) {
		try {
			CliRequest cliRequest = new CliRequest(args);
			EclipseInstaller installer = new EclipseInstallerFactory()
					.createEmbeddedInstaller();
			System.exit(new P2InstallerApp().run(cliRequest, installer));
		} catch (Throwable e) {
			System.err.println("Exception during installation");
			e.printStackTrace();
			System.exit(2);
		}
	}
}
