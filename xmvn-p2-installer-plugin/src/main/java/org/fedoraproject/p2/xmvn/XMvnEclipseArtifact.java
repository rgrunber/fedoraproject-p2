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
package org.fedoraproject.p2.xmvn;

import java.nio.file.Path;

import org.fedoraproject.xmvn.metadata.ArtifactMetadata;
import org.fedoraproject.p2.installer.EclipseArtifact;

class XMvnEclipseArtifact extends EclipseArtifact {

	private final ArtifactMetadata metadata;

	public XMvnEclipseArtifact(Path path, boolean isFeature, boolean isNative,
			ArtifactMetadata metadata) {
		super(path, isFeature, isNative);
		this.metadata = metadata;
	}

	public ArtifactMetadata getMetadata() {
		return metadata;
	}
}
