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

import org.eclipse.core.runtime.Platform;
import org.eclipse.equinox.p2.core.IProvisioningAgent;
import org.eclipse.equinox.p2.core.IProvisioningAgentProvider;
import org.eclipse.equinox.p2.metadata.IInstallableUnit;
import org.eclipse.equinox.p2.metadata.ITouchpointData;
import org.eclipse.equinox.p2.metadata.ITouchpointInstruction;
import org.eclipse.equinox.p2.repository.artifact.IArtifactRepositoryManager;
import org.eclipse.equinox.p2.repository.metadata.IMetadataRepositoryManager;
import org.junit.BeforeClass;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;

public class RepositoryTest {

	static final String NAMESPACE = "fedora:";
	// TODO: Create our own test repositories in resoures/ folder
	static final String JAVADIR = NAMESPACE + "/usr/share/java";
	static final String EMPTY = NAMESPACE + "/tmp";
	static final String ECLIPSE_DIR = NAMESPACE + "/usr/lib"
						+ (System.getProperty("os.arch").contains("64") ? "64" : "")
						+ "/eclipse/plugins";

	private static IProvisioningAgent agent;
	private static IMetadataRepositoryManager metadataRM;
	private static IArtifactRepositoryManager artifactRM;

	/**
	 * All access to p2 services happen through the provisioning agent.
	 */
	@BeforeClass
	public static void beforeClass() throws Exception {
		BundleContext bc = Platform.getBundle("org.fedoraproject.p2.tests").getBundleContext();
		ServiceReference<?> sr = (ServiceReference<?>) bc.getServiceReference(IProvisioningAgentProvider.SERVICE_NAME);
		IProvisioningAgentProvider pr = (IProvisioningAgentProvider) bc.getService(sr);
		agent = pr.createAgent(null);
		metadataRM = (IMetadataRepositoryManager) agent.getService(IMetadataRepositoryManager.SERVICE_NAME);
		artifactRM = (IArtifactRepositoryManager) agent.getService(IArtifactRepositoryManager.SERVICE_NAME);
	}

	protected IMetadataRepositoryManager getMetadataRepoManager () {
		return metadataRM;
	}

	protected IArtifactRepositoryManager getArtifactRepoManager () {
		return artifactRM;
	}

	protected boolean isBundleShapeDir (IInstallableUnit u) {
		for (ITouchpointData d : u.getTouchpointData()) {
			ITouchpointInstruction i = d.getInstruction("zipped");
			if (i != null && "true".equals(i.getBody())) {
				return true;
			}
		}
		return false;
	}

}
