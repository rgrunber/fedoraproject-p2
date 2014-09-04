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

import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author Mikolaj Izdebski
 */
public class Provide {
	private final String id;

	private final String version;

	private final Path path;

	private final Map<String, String> properties = new LinkedHashMap<>();

	public Provide(String id, String version, Path path) {
		this.id = id;
		this.version = version;
		this.path = path;
	}

	public String getId() {
		return id;
	}

	public String getVersion() {
		return version;
	}

	public Path getPath() {
		return path;
	}

	public Map<String, String> getProperties() {
		return Collections.unmodifiableMap(properties);
	}

	public void setProperty(String key, String value) {
		properties.put(key, value);
	}

	@Override
	public boolean equals(Object obj) {
		return obj != null && obj instanceof Provide
				&& id.equals(((Provide) obj).id);
	}

	@Override
	public int hashCode() {
		return id.hashCode();
	}
}
