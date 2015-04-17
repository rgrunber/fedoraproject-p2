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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A compound bundle repository which consists of one or more layered bundle
 * repositories.
 * 
 * @author Mikolaj Izdebski
 */
public class CompoundBundleRepository extends AbstractBundleRepository {

	private final List<FedoraBundleRepository> indices;

	/**
     * Create a compound repository backed by file system locations at given
     * prefixes.
     *
     * @param scls
     *            ordered list of prefixes to use
     */
	public CompoundBundleRepository(List<SCL> scls) {
		indices = new ArrayList<>(scls.size());
		for (SCL scl : scls) {
			indices.add(new FedoraBundleRepository(scl));
		}

		platformUnits = new LinkedHashSet<>();
		for (IFedoraBundleRepository index : indices) {
			platformUnits.addAll(index.getPlatformUnits());
		}

		internalUnits = new LinkedHashSet<>();
		for (IFedoraBundleRepository index : indices) {
			internalUnits.addAll(index.getInternalUnits());
		}
		internalUnits.removeAll(platformUnits);

		externalUnits = new LinkedHashSet<>();
		for (IFedoraBundleRepository index : indices) {
			externalUnits.addAll(index.getExternalUnits());
		}
		externalUnits.removeAll(platformUnits);
	}

	@Override
	public Set<Path> getDropinsLocations() {
		Set<Path> dropinsLocations = new LinkedHashSet<>();
		for (AbstractBundleRepository index : indices) {
			dropinsLocations.addAll(index.getDropinsLocations());
		}
		return Collections.unmodifiableSet(dropinsLocations);
	}
}
