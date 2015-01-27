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
package org.fedoraproject.p2.installer;

import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * @author Mikolaj Izdebski
 */
public class Dropin {
	private final String id;

	private final Path path;

	private final Set<EclipseArtifact> osgiProvides = new LinkedHashSet<>();

	public Dropin(String id, Path path) {
		this.id = id;
		this.path = path;
	}

	public String getId() {
		return id;
	}

	public Path getPath() {
		return path;
	}

	public Set<EclipseArtifact> getOsgiProvides() {
		return osgiProvides;
	}

	public void addProvide(EclipseArtifact provide) {
		osgiProvides.add(provide);
	}

	@Override
	public boolean equals(Object obj) {
		return obj != null && obj instanceof Dropin
				&& id.equals(((Dropin) obj).id);
	}

	@Override
	public int hashCode() {
		return id.hashCode();
	}
}
