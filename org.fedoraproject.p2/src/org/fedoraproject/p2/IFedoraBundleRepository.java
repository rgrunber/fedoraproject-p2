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
import java.util.Set;

import org.eclipse.equinox.p2.metadata.IInstallableUnit;
import org.eclipse.equinox.p2.metadata.IVersionedId;

public interface IFedoraBundleRepository {
	/**
	 * @return A set of installable units which are part of the Eclipse platform installation.
	 */
	Set<IInstallableUnit> getPlatformUnits();

	/**
	 * @return A set of installable units that are discovered by the Eclipse platform at runtime.
	 * This refers to the 'dropins' mechanism of bundle discovery. Any platform units that are
	 * also present as internal units are ignored.
	 */
	Set<IInstallableUnit> getInternalUnits();

	/**
	 * @return a set of installable units that are OSGi bundles, but not in a location for
	 * discovery, or inclusion as part of Eclipse. Any platform or internal units that are
	 * also present as external units are ignored.
	 */
	Set<IInstallableUnit> getExternalUnits();

	/**
	 * Retrieve the system path corresponding to the given ID and Version.
	 * This assumes the artifact is an OSGi bundle.
	 * @param key an ID and Version represented as an {@link IVersionedId}.
	 * @return The system path on which to find the specified {@link IVersionedId}
	 * or <code>null</code> if no such bundle could be found.
	 */
	Path lookupBundle (IVersionedId key);
}
