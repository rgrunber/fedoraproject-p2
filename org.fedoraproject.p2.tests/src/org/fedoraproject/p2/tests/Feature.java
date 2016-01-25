/*******************************************************************************
 * Copyright (c) 2015-2016 Red Hat Inc.
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
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

/**
 * @author Mikolaj Izdebski
 */
class Feature {
	private final String id;
	private final String version;
	private Path path;
	private String targetPackage;
	private Properties p2inf = new Properties();
	private Map<String, String> plugins = new HashMap<>();

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

	public Feature addP2Inf(String key, String value) {
		p2inf.setProperty(key, value);
		return this;
	}

	public Feature addPlugin(String id, String version) {
		plugins.put(id, version);
		return this;
	}

	public void write(Path path) throws Exception {
		Files.createDirectories(path);
		try (PrintWriter pw = new PrintWriter(path.resolve("feature.xml").toFile())) {
			pw.printf("<feature id=\"%s\" version=\"%s\">\n", id, version);
			for (Entry<String, String> plugin : plugins.entrySet()) {
				pw.printf(
						"<plugin id=\"%s\" download-size=\"1024\" install-size=\"1024\" version=\"%s\" unpack=\"false\"/>\n",
						plugin.getKey(), plugin.getValue());
			}
			pw.printf("</feature>");
		}
		if (!p2inf.isEmpty()) {
			try (PrintWriter pw = new PrintWriter(path.resolve("p2.inf").toFile())) {
				p2inf.store(pw, "p2.inf");
			}
		}
		this.path = path;
	}

}
