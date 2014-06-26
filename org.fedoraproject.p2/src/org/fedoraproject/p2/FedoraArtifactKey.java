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

import org.eclipse.equinox.p2.metadata.IArtifactKey;
import org.eclipse.equinox.p2.metadata.Version;

public class FedoraArtifactKey implements IArtifactKey {

	private String classifier;
	private String id;
	private Version version;

	public FedoraArtifactKey (String classifier, String id,
			Version version) {
		this.classifier = classifier;
		this.id = id;
		this.version = version;
	}

	@Override
	public String getClassifier() {
		return classifier;
	}

	@Override
	public String getId() {
		return id;
	}

	@Override
	public Version getVersion() {
		return version;
	}

	@Override
	public String toExternalForm() {
		return classifier + ":" + id + ":" + version;
	}

	@Override
	public boolean equals (Object other) {
		if (other instanceof IArtifactKey) {
			IArtifactKey okey = (IArtifactKey) other;
			if (classifier.equals(okey.getClassifier())
					&& id.equals(okey.getId())
					&& getVersion().equals(okey.getVersion())) {
				return true;
			}
		}
		return false;
	}

	@Override
	public int hashCode() {
		int hash = id.hashCode();
		hash = 17 * hash + getVersion().hashCode();
		hash = 17 * hash + classifier.hashCode();
		return hash;
	}

}
