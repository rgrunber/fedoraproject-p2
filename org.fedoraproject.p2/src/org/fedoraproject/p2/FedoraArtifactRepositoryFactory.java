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
import java.net.URI;
import java.util.Map;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.equinox.p2.core.ProvisionException;
import org.eclipse.equinox.p2.repository.artifact.IArtifactRepository;
import org.eclipse.equinox.p2.repository.artifact.spi.ArtifactRepositoryFactory;

public class FedoraArtifactRepositoryFactory extends ArtifactRepositoryFactory {

	@Override
	public IArtifactRepository create(URI location, String name, String type,
			Map<String, String> properties) throws ProvisionException {
		throw new ProvisionException("Not Implemented");
	}

	@Override
	public IArtifactRepository load(URI location, int flags,
			IProgressMonitor monitor) throws ProvisionException {

		if (location.getScheme().equals("fedora")) {
			File file = new File(location.getPath());
			if (file.exists()) {
				return new FedoraArtifactRepository(getAgent(), location);
			}
		}
		throw new ProvisionException(new Status(IStatus.ERROR, "org.fedoraproject.p2", ProvisionException.REPOSITORY_NOT_FOUND, "Repository Not Found", null));
	}
}
