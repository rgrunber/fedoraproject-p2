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
package org.fedoraproject.p2.app;

import java.nio.file.Paths;
import java.util.Map.Entry;
import java.util.Set;

import javax.inject.Inject;
import com.google.inject.Injector;
import org.eclipse.sisu.space.ClassSpace;
import com.google.inject.Module;
import javax.inject.Singleton;
import javax.inject.Named;
import com.google.inject.Guice;
import org.eclipse.sisu.space.SpaceModule;
import org.eclipse.sisu.space.URLClassSpace;
import org.eclipse.sisu.wire.WireModule;

import org.fedoraproject.p2.installer.Dropin;
import org.fedoraproject.p2.installer.EclipseInstallationRequest;
import org.fedoraproject.p2.installer.EclipseInstallationResult;
import org.fedoraproject.p2.installer.EclipseInstaller;
import org.fedoraproject.p2.installer.Provide;
import org.fedoraproject.p2.osgi.OSGiServiceLocator;

/**
 * @author Mikolaj Izdebski
 */
@Named
@Singleton
public class P2InstallerApp {

	private OSGiServiceLocator serviceLocator;

	@Inject
	public P2InstallerApp(OSGiServiceLocator serviceLocator) {
		this.serviceLocator = serviceLocator;
	}

	public int run(CliRequest cliRequest) throws Exception {
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
			request.addPlugin(Paths.get(arg));
		for (Entry<String, String> entry : cliRequest.getMappings().entrySet())
			request.addPackageMapping(entry.getKey(), entry.getValue());

		EclipseInstaller installer = serviceLocator
				.getService(EclipseInstaller.class);
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
			for (Provide provide : dropin.getOsgiProvides()) {
				String idVer = provide.getId() + " " + provide.getVersion();
				String req = provide.getProperties().get("osgi.requires");
				System.out.println(req == null ? idVer : idVer + " " + req);
			}
		}
	}

	public static void main(String[] args) {
		try {
			CliRequest cliRequest = new CliRequest(args);

			ClassLoader realm = Thread.currentThread().getContextClassLoader();
			ClassSpace classSpace = new URLClassSpace(realm);
			Module module = new WireModule(new SpaceModule(classSpace));
			Injector injector = Guice.createInjector(module);
			P2InstallerApp app = injector.getInstance(P2InstallerApp.class);

			System.exit(app.run(cliRequest));
		} catch (Throwable e) {
			System.err.println("Exception during installation");
			e.printStackTrace();
			System.exit(2);
		}
	}
}
