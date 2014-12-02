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

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.equinox.p2.metadata.IInstallableUnit;

/**
 * A compound bundle repository which consists of one or more layered bundle
 * repositories.
 * 
 * @author Mikolaj Izdebski
 */
public class CompoundBundleRepository implements IFedoraBundleRepository {

	private final List<IFedoraBundleRepository> indices;
	private Set<IInstallableUnit> platformUnits;
	private Set<IInstallableUnit> internalUnits;
	private Set<IInstallableUnit> externalUnits;

	/**
	 * Create a compound repository backed by file system locations at given
	 * prefixes.
	 * 
	 * @param prefixes
	 *            ordered list of prefixes to use
	 */
	public CompoundBundleRepository(List<Path> prefixes) {
		if (prefixes.isEmpty())
			prefixes = Collections.singletonList(Paths.get("/"));
		indices = new ArrayList<>(prefixes.size());
		for (Path prefix : prefixes) {
			indices.add(new FedoraBundleRepository(prefix.toFile()));
		}
	}

	@Override
	public synchronized Set<IInstallableUnit> getPlatformUnits() {
		if (platformUnits == null) {
			platformUnits = new LinkedHashSet<>();
			for (IFedoraBundleRepository index : indices) {
				platformUnits.addAll(index.getPlatformUnits());
			}
		}
		return platformUnits;
	}

	@Override
	public synchronized Set<IInstallableUnit> getInternalUnits() {
		if (internalUnits == null) {
			internalUnits = new LinkedHashSet<>();
			for (IFedoraBundleRepository index : indices) {
				internalUnits.addAll(index.getInternalUnits());
			}
		}
		return internalUnits;
	}

	@Override
	public synchronized Set<IInstallableUnit> getExternalUnits() {
		if (externalUnits == null) {
			externalUnits = new LinkedHashSet<>();
			for (IFedoraBundleRepository index : indices) {
				externalUnits.addAll(index.getExternalUnits());
			}
		}
		return externalUnits;
	}

}
