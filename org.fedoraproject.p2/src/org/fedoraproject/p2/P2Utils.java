/*******************************************************************************
 * Copyright (c) 2014-2015 Red Hat Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.fedoraproject.p2;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.TreeSet;

import org.eclipse.equinox.internal.p2.metadata.InstallableUnit;
import org.eclipse.equinox.p2.core.IProvisioningAgent;
import org.eclipse.equinox.p2.core.IProvisioningAgentProvider;
import org.eclipse.equinox.p2.core.ProvisionException;
import org.eclipse.equinox.p2.metadata.IInstallableUnit;
import org.eclipse.equinox.p2.metadata.ITouchpointData;
import org.eclipse.equinox.p2.metadata.ITouchpointInstruction;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class P2Utils {
	private static final String PROP_PATH = "org.fedoraproject.p2.path";
	private static final String PROP_NAMESPACE = "org.fedoraproject.p2.scl";

	private static final Logger logger = LoggerFactory.getLogger(P2Utils.class);

	private static IProvisioningAgent agent;

	public static synchronized IProvisioningAgent getAgent() throws ProvisionException {
		if (agent != null)
			return agent;

		BundleContext context = Activator.getContext();

		ServiceReference<IProvisioningAgent> agentRef = context
				.getServiceReference(IProvisioningAgent.class);
		if (agentRef != null) {
			agent = context.getService(agentRef);
			if (agent != null)
				return agent;
		}

		ServiceReference<IProvisioningAgentProvider> providerRef = context
				.getServiceReference(IProvisioningAgentProvider.class);
		if (providerRef == null)
			throw new RuntimeException("No registered OSGi services for "
					+ IProvisioningAgentProvider.class);

		try {
			IProvisioningAgentProvider provider = context
					.getService(providerRef);
			if (provider == null)
				throw new RuntimeException("Unable to get OSGi service for "
						+ IProvisioningAgentProvider.class);

			agent = provider.createAgent(null);
			return agent;
		} finally {
			context.ungetService(providerRef);
		}
	}

	public static IInstallableUnit setProperty(IInstallableUnit unit,
			String key, String value) {
		((InstallableUnit) unit).setProperty(key, value);
		return unit;
	}

	public static Path getPath(IInstallableUnit unit) {
		String path = unit.getProperty(PROP_PATH);
		if (path == null)
			return null;
		return Paths.get(path);
	}

	public static IInstallableUnit setPath(IInstallableUnit unit, File path) {
		return setProperty(unit, PROP_PATH, path.getPath());
	}

	public static String getSclNamespace(IInstallableUnit unit) {
		return unit.getProperty(PROP_NAMESPACE);
	}

	public static IInstallableUnit setSclNamespace(IInstallableUnit unit,
			String namespace) {
		return setProperty(unit, PROP_NAMESPACE, namespace);
	}

	public static String toString(IInstallableUnit unit) {
		String namespace = getSclNamespace(unit);
		String suffix = "";
		if (namespace != null)
			suffix = "(" + namespace + ")";
		return unit.getId() + suffix;
	}

	public static boolean isBundleShapeDir(IInstallableUnit u) {
		for (ITouchpointData d : u.getTouchpointData()) {
			ITouchpointInstruction i = d.getInstruction("zipped");
			if (i != null && "true".equals(i.getBody())) {
				return true;
			}
		}
		return false;
	}

	public static void dump(String message, Set<IInstallableUnit> units) {
		logger.debug("{}:", message);
		Set<String> sorted = new TreeSet<>();
		for (IInstallableUnit unit : units)
			sorted.add(unit.toString());
		for (String unit : sorted)
			logger.debug("  * {}", unit);
		if (sorted.isEmpty())
			logger.debug("  (none)");
	}
}
