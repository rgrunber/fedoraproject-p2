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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author Mikolaj Izdebski
 */
public class EclipseArtifact {

	private final Path path;

	private final boolean isFeature;

	private String targetPackage;

	private String id;

	private String version;

	private Path installedPath;

	private final Map<String, String> properties = new LinkedHashMap<>();

	public EclipseArtifact(Path path, boolean isFeature) {
		this.path = path;
		this.isFeature = isFeature;
	}

	public Path getPath() {
		return path;
	}

	public boolean isFeature() {
		return isFeature;
	}

	public String getTargetPackage() {
		return targetPackage;
	}

	public void setTargetPackage(String targetPackage) {
		this.targetPackage = targetPackage;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getVersion() {
		return version;
	}

	public void setVersion(String version) {
		this.version = version;
	}

	public Path getInstalledPath() {
		return installedPath;
	}

	public void setInstalledPath(Path installedPath) {
		this.installedPath = installedPath;
	}

	public Map<String, String> getProperties() {
		return Collections.unmodifiableMap(properties);
	}

	public void setProperty(String key, String value) {
		properties.put(key, value);
	}

	@Override
	public boolean equals(Object obj) {
		return obj != null && obj instanceof EclipseArtifact
				&& path.equals(((EclipseArtifact) obj).path);
	}

	@Override
	public int hashCode() {
		return path.hashCode();
	}
}
