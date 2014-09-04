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
package org.fedoraproject.p2.installer;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * @author Mikolaj Izdebski
 */
public class EclipseInstallationResult {
	private final Set<Dropin> dropins = new LinkedHashSet<>();

	public EclipseInstallationResult(Set<Dropin> dropins) {
		this.dropins.addAll(dropins);
	}

	public Set<Dropin> getDropins() {
		return Collections.unmodifiableSet(dropins);
	}

	public void addDropin(Dropin dropin) {
		dropins.add(dropin);
	}
}
