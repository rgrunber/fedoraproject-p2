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
package org.fedoraproject.p2.tests;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * @author Mikolaj Izdebski
 */
class Feature {
	private final String id;
	private final String version;
	private Path path;
	private String targetPackage;

	public Feature(String id, String version) {
		this.id = id;
		this.version = version;
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

	public String getTargetPackage() {
		return targetPackage;
	}

	public Feature assignToTargetPackage(String pkg) {
		targetPackage = pkg;
		return this;
	}

	public void write(Path path) throws Exception {
		Files.createDirectories(path);
		try (PrintWriter pw = new PrintWriter(path.resolve("feature.xml").toFile())) {
			pw.printf("<feature id=\"%s\" version=\"%s\"/>", id, version);
		}
		this.path = path;
	}

}
