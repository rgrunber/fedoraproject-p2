/*******************************************************************************
 * Copyright (c) 2015 Red Hat Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.fedoraproject.p2;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import org.eclipse.equinox.p2.metadata.IInstallableUnit;

/**
 * Abstract bundle repository that contains filtering functionality so that
 * duplicate bundles do not get returned from the
 * {@link IFedoraBundleRepository} interface. All bundle repository
 * implementations should extend this class and implement
 * {@link #getDropinsLocations()}.
 */
public abstract class AbstractBundleRepository implements
		IFedoraBundleRepository {

	protected Set<IInstallableUnit> platformUnits;
	protected Set<IInstallableUnit> internalUnits;
	protected Set<IInstallableUnit> externalUnits;
	private boolean filtered = false;

	private void filterUnits() {
		if (!filtered) {
			internalUnits = new LinkedHashSet<>(internalUnits);
			externalUnits = new LinkedHashSet<>(externalUnits);

			Set<IInstallableUnit> commonUnits = new LinkedHashSet<>(
					externalUnits);
			commonUnits.retainAll(internalUnits);

			internalUnits.removeAll(commonUnits);
			externalUnits.removeAll(commonUnits);

			for (IInstallableUnit unit : commonUnits) {
				try {
					Path path = P2Utils.getPath(unit);
					if (path == null)
						continue;
					path = path.toRealPath();
					for (Path dropin : getDropinsLocations()) {
						if (path.startsWith(dropin))
							internalUnits.add(unit);
						else
							externalUnits.add(unit);
					}
				} catch (IOException e) {
				}
			}
			filtered = true;
		}
	}

	public abstract Set<Path> getDropinsLocations();

	@Override
	public final Set<IInstallableUnit> getPlatformUnits() {
		return Collections.unmodifiableSet(platformUnits);
	}

	@Override
	public final Set<IInstallableUnit> getInternalUnits() {
		filterUnits();
		return Collections.unmodifiableSet(internalUnits);
	}

	@Override
	public final Set<IInstallableUnit> getExternalUnits() {
		filterUnits();
		return Collections.unmodifiableSet(externalUnits);
	}
}
