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
package org.fedoraproject.p2;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.eclipse.equinox.internal.p2.metadata.InstallableUnit;
import org.eclipse.equinox.p2.metadata.IInstallableUnit;

public class P2Utils {
	private static final String PROP_PATH = "org.fedoraproject.p2.path";
	private static final String PROP_NAMESPACE = "org.fedoraproject.p2.scl";

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
}
