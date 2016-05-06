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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.eclipse.equinox.internal.p2.metadata.InstallableUnit;
import org.eclipse.equinox.p2.core.IProvisioningAgent;
import org.eclipse.equinox.p2.core.IProvisioningAgentProvider;
import org.eclipse.equinox.p2.core.ProvisionException;
import org.eclipse.equinox.p2.metadata.IInstallableUnit;
import org.eclipse.equinox.p2.metadata.ITouchpointData;
import org.eclipse.equinox.p2.metadata.ITouchpointInstruction;
import org.eclipse.osgi.framework.util.Headers;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class P2Utils {
	private static final String PROP_PATH = "org.fedoraproject.p2.path";
	private static final String PROP_NAMESPACE = "org.fedoraproject.p2.scl";
	private static final String PROP_MANIFEST = "org.fedoraproject.p2.manifest";

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

	public static Map<String, String> getManifest(IInstallableUnit unit) {
		return decodeManifest(unit.getProperty(PROP_MANIFEST));
	}

	public static IInstallableUnit setManifest(IInstallableUnit unit,
			Headers headers) {
		return setProperty(unit, PROP_MANIFEST, encodeManifest(headers));
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

	public static void delete (File root) {
		if (root.isDirectory()) {
			for (File child : root.listFiles()) {
				if (child.isDirectory() && child.canRead()) {
					delete(child);
				} else {
					child.delete();
				}
			}
		}
		root.delete();
	}

	private static Map<String, String> decodeManifest(String base64) {
		if (base64 == null)
			return null;
		byte[] data = Base64.getDecoder().decode(base64);
		try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(data))) {
			return Collections.unmodifiableMap((Map<String, String>) ois.readObject());
		} catch (IOException | ReflectiveOperationException e) {
			throw new RuntimeException(e);
		}
	}

	private static String encodeManifest(Headers<String, String> headers) {
		Map<String, String> manifest = new LinkedHashMap<>();
		Enumeration<String> keys = headers.keys();
		while (keys.hasMoreElements()) {
			String key = keys.nextElement();
			manifest.put(key, headers.get(key));
		}
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
			oos.writeObject(new LinkedHashMap<>(manifest));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		return Base64.getEncoder().encodeToString(baos.toByteArray());
	}
}
